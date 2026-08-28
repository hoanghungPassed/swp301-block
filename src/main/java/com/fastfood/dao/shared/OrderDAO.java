package com.fastfood.dao.shared;

import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.model.entity.OrderEntities.Order;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class OrderDAO {

    private static final String COLS =
            "o.order_id, o.customer_id, o.created_by_user_id, o.order_source, o.total_amount, " +
            "o.order_status, o.idempotency_key, o.pickup_time, o.kitchen_release_at, " +
            "o.released_to_kds_at, o.pickup_code, o.ready_at, o.picked_up_at, o.handoff_by_user_id, " +
            "o.created_at, o.completed_at, o.expired_at ";

    private static final String BASE =
            "SELECT " + COLS + ", cu.full_name AS customer_name, cu.email AS customer_email, " +
            "       hu.full_name AS handoff_by_name " +
            "FROM dbo.Orders o " +
            "LEFT JOIN dbo.Users cu ON cu.user_id = o.customer_id " +
            "LEFT JOIN dbo.Users hu ON hu.user_id = o.handoff_by_user_id ";

    /** Chèn Orders mới và gán orderId identity vào entity. */
    public int insert(Connection con, Order o) throws SQLException {
        String sql = "INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, " +
                     "order_status, idempotency_key, pickup_time, kitchen_release_at, released_to_kds_at, " +
                     "pickup_code, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setInteger(ps, 1, o.getCustomerId());
            JdbcSupport.setInteger(ps, 2, o.getCreatedByUserId());
            ps.setString(3, o.getOrderSource());
            ps.setBigDecimal(4, o.getTotalAmount());
            ps.setString(5, o.getOrderStatus());
            JdbcSupport.setString(ps, 6, o.getIdempotencyKey());
            JdbcSupport.setDateTime(ps, 7, o.getPickupTime());
            JdbcSupport.setDateTime(ps, 8, o.getKitchenReleaseAt());
            JdbcSupport.setDateTime(ps, 9, o.getReleasedToKdsAt());
            JdbcSupport.setString(ps, 10, o.getPickupCode());
            JdbcSupport.setDateTime(ps, 11, o.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    o.setOrderId(keys.getInt(1));
                }
            }
        }
        return o.getOrderId();
    }

    /** Lưu tổng tiền do server tính từ các OrderItem. */
    public void updateTotal(Connection con, int orderId, BigDecimal total) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Orders SET total_amount = ? WHERE order_id = ?")) {
            ps.setBigDecimal(1, total);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    /** Chuyển đơn online từ PENDING_PAYMENT sang CONFIRMED và lưu mã/thời gian xuống bếp. */
    public int confirmOnlineAfterPaid(Connection con, int orderId, String pickupCode,
                                      LocalDateTime kitchenReleaseAt) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'CONFIRMED', pickup_code = ?, kitchen_release_at = ? " +
                     "WHERE order_id = ? AND order_status = 'PENDING_PAYMENT'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, pickupCode);
            JdbcSupport.setDateTime(ps, 2, kitchenReleaseAt);
            ps.setInt(3, orderId);
            return ps.executeUpdate();
        }
    }

    /** Đánh dấu đơn CONFIRMED đã được đưa vào hàng chờ bếp đúng một lần. */
    public int markReleasedToKds(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET released_to_kds_at = ? " +
                     "WHERE order_id = ? AND released_to_kds_at IS NULL AND order_status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Đổi đơn CONFIRMED sang PREPARING khi món đầu tiên được bếp nhận làm. */
    public int markPreparing(Connection con, int orderId) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'PREPARING' " +
                     "WHERE order_id = ? AND order_status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate();
        }
    }

    /** Đánh dấu toàn đơn READY và lưu thời điểm sẵn sàng khi mọi món đã hoàn thành. */
    public int markReady(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'READY', ready_at = ? " +
                     "WHERE order_id = ? AND order_status IN ('CONFIRMED', 'PREPARING')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Hoàn tất đơn sau khi quầy xác minh mã nhận hàng của Customer. */
    public int markCompleted(Connection con, int orderId, int handoffByUserId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'COMPLETED', picked_up_at = ?, " +
                     "completed_at = ?, handoff_by_user_id = ? WHERE order_id = ? AND order_status = 'READY'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, handoffByUserId);
            ps.setInt(4, orderId);
            return ps.executeUpdate();
        }
    }

    /* Đơn tại quầy khách bỏ dở. Tách khỏi markExpired vì trạng thái xuất phát khác nhau:
       đơn đặt trước hết hiệu lực từ PENDING_PAYMENT, còn đơn quầy phải lập ở CONFIRMED thì
       mới sinh được mã QR cho khách quét. Dùng chung một câu lệnh nới điều kiện WHERE ra
       cho cả hai sẽ mở đường cho đơn quầy đang chờ khách quét bị đóng nhầm. */
    public int markCounterExpired(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'EXPIRED', expired_at = ? " +
                     "WHERE order_id = ? AND order_source = 'POS' AND order_status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Chuyển đơn PENDING_PAYMENT quá hạn hoặc khách bỏ sang EXPIRED. */
    public int markExpired(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'EXPIRED', expired_at = ? " +
                     "WHERE order_id = ? AND order_status = 'PENDING_PAYMENT'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Khóa dòng đơn trong transaction để tránh callback thanh toán và hủy đơn chạy đồng thời. */
    public void lockForUpdate(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT order_id FROM dbo.Orders WITH (UPDLOCK, ROWLOCK) WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    /** Tìm đơn theo khóa chính. */
    public Order findById(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tìm đúng đơn READY từ mã Customer đưa tại quầy để tránh giao nhầm. */
    public Order findByPickupCode(Connection con, String pickupCode) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.pickup_code = ?")) {
            ps.setString(1, pickupCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tìm đơn đã tạo bằng idempotency key để chống double-submit checkout. */
    public Order findByIdempotencyKey(Connection con, String key) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.idempotency_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Lấy một trang lịch sử đơn chỉ thuộc customer và bộ lọc được truyền vào. */
    public List<Order> findByCustomer(Connection con, int customerId, String status,
                                      LocalDateTime from, LocalDateTime to,
                                      int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = BASE + customerWhere(customerId, status, from, to, params)
                   + "ORDER BY o.created_at DESC, o.order_id DESC "
                   + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = bindAll(ps, params);
            ps.setInt(i++, offset);
            ps.setInt(i, limit);
            return collect(ps);
        }
    }

    /** Đếm lịch sử đơn của customer theo cùng bộ lọc để phân trang. */
    public long countByCustomer(Connection con, int customerId, String status,
                                LocalDateTime from, LocalDateTime to) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM dbo.Orders o "
                   + customerWhere(customerId, status, from, to, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Dựng WHERE bắt buộc customerId và thêm status/khoảng ngày bằng bind parameter. */
    private String customerWhere(int customerId, String status,
                                 LocalDateTime from, LocalDateTime to, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE o.customer_id = ? ");
        params.add(customerId);
        if (status != null && !status.isBlank()) {
            sql.append("AND o.order_status = ? ");
            params.add(status);
        }
        if (from != null) {
            sql.append("AND o.created_at >= ? ");
            params.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            sql.append("AND o.created_at <= ? ");
            params.add(Timestamp.valueOf(to));
        }
        return sql.toString();
    }

    /** Bind tuần tự danh sách parameter cho câu truy vấn lịch sử Customer. */
    private int bindAll(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return params.size() + 1;
    }

    /** Tìm đơn PENDING_PAYMENT gần nhất để chặn một customer giữ nhiều đơn chưa trả. */
    public Order findPendingByCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE +
                "WHERE o.customer_id = ? AND o.order_status = 'PENDING_PAYMENT' " +
                "ORDER BY o.created_at DESC")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Lấy các đơn chưa kết thúc của customer theo giờ nhận. */
    public List<Order> findActiveByCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE +
                "WHERE o.customer_id = ? AND o.order_status IN " +
                "('PENDING_PAYMENT','CONFIRMED','PREPARING','READY') ORDER BY o.pickup_time")) {
            ps.setInt(1, customerId);
            return collect(ps);
        }
    }

    public List<Order> findForDashboard(Connection con, String tab, LocalDateTime now, int overdueMinutes)
            throws SQLException {
        String where;
        switch (tab == null ? "" : tab) {
            case "SCHEDULED":
                where = "WHERE o.order_source = 'ONLINE_PREORDER' " +
                        "AND o.order_status IN ('CONFIRMED','PREPARING') " +
                        "ORDER BY o.kitchen_release_at";
                break;
            case "READY":
                where = "WHERE o.order_status = 'READY' ORDER BY o.pickup_time, o.ready_at";
                break;
            case "OVERDUE":
                where = "WHERE o.order_status = 'READY' AND o.order_source = 'ONLINE_PREORDER' " +
                        "AND DATEADD(MINUTE, ?, o.pickup_time) < ? ORDER BY o.pickup_time";
                break;
            case "POS":
            default:
                where = "WHERE o.order_source = 'POS' AND o.order_status IN ('CONFIRMED','PREPARING','READY') " +
                        "ORDER BY o.created_at DESC";
                break;
        }
        try (PreparedStatement ps = con.prepareStatement(BASE + where)) {
            if ("OVERDUE".equals(tab)) {
                ps.setInt(1, overdueMinutes);
                JdbcSupport.setDateTime(ps, 2, now);
            }
            return collect(ps);
        }
    }

    public List<Order> search(Connection con, String source, String status,
                              LocalDateTime from, LocalDateTime to,
                              int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = BASE + searchWhere(source, status, from, to, params)
                   + "ORDER BY o.created_at DESC, o.order_id DESC "
                   + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = bind(ps, params);
            ps.setInt(i++, offset);
            ps.setInt(i, limit);
            return collect(ps);
        }
    }

    public long countSearch(Connection con, String source, String status,
                            LocalDateTime from, LocalDateTime to) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM dbo.Orders o " + searchWhere(source, status, from, to, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private String searchWhere(String source, String status,
                               LocalDateTime from, LocalDateTime to, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        if (source != null && !source.isBlank()) {
            sql.append("AND o.order_source = ? ");
            params.add(source);
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND o.order_status = ? ");
            params.add(status);
        }
        if (from != null) {
            sql.append("AND o.created_at >= ? ");
            params.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            sql.append("AND o.created_at <= ? ");
            params.add(Timestamp.valueOf(to));
        }
        return sql.toString();
    }

    private int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return params.size() + 1;
    }

    /** Tìm đơn CONFIRMED đã tới kitchenReleaseAt nhưng chưa đưa xuống bếp. */
    public List<Order> findDueForRelease(Connection con, LocalDateTime now) throws SQLException {
        String sql = "SELECT " + COLS + ", NULL AS customer_name, NULL AS customer_email, " +
                     "NULL AS handoff_by_name FROM dbo.Orders o " +
                     "WHERE o.order_status = 'CONFIRMED' AND o.released_to_kds_at IS NULL " +
                     "AND o.kitchen_release_at IS NOT NULL AND o.kitchen_release_at <= ? " +
                     "ORDER BY o.kitchen_release_at";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            return collect(ps);
        }
    }

    /** Tìm đơn PENDING_PAYMENT được tạo trước deadline để scheduler cho hết hiệu lực. */
    public List<Order> findExpiredCandidates(Connection con, LocalDateTime deadline) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE o.order_status = 'PENDING_PAYMENT' AND o.created_at <= ?")) {
            JdbcSupport.setDateTime(ps, 1, deadline);
            return collect(ps);
        }
    }

    /**
     * Đơn tại quầy trả bằng mã QR mà khách bỏ dở: lập đã lâu, chưa có khoản thu nào PAID và
     * chưa xuống bếp.
     *
     * <p>Điều kiện "chưa có khoản PAID" là chỗ quan trọng nhất. Đơn đã nhận được tiền nhưng
     * thu ngân chưa kịp bấm Xong thì bên trong có tiền thật của khách — đơn ấy phải nằm yên
     * chờ người xử lý, không được để một tác vụ chạy nền huỷ mất.
     */
    public List<Order> findAbandonedCounterOrders(Connection con, LocalDateTime deadline)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE o.order_source = 'POS' AND o.order_status = 'CONFIRMED' " +
                       "AND o.released_to_kds_at IS NULL AND o.created_at <= ? " +
                       "AND NOT EXISTS (SELECT 1 FROM dbo.Payment p " +
                       "                WHERE p.order_id = o.order_id AND p.payment_status = 'PAID')")) {
            JdbcSupport.setDateTime(ps, 1, deadline);
            return collect(ps);
        }
    }

    public int countByStatus(Connection con, OrderStatus status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.Orders WHERE order_status = ?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Chạy truy vấn và ánh xạ toàn bộ ResultSet thành danh sách Order. */
    private List<Order> collect(PreparedStatement ps) throws SQLException {
        List<Order> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /** Ánh xạ một dòng ResultSet thành Order và tính cờ trạng thái thời gian. */
    private Order map(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setCustomerId(JdbcSupport.getInteger(rs, "customer_id"));
        o.setCreatedByUserId(JdbcSupport.getInteger(rs, "created_by_user_id"));
        o.setOrderSource(rs.getString("order_source"));
        o.setTotalAmount(JdbcSupport.getMoney(rs, "total_amount"));
        o.setOrderStatus(rs.getString("order_status"));
        o.setIdempotencyKey(rs.getString("idempotency_key"));
        o.setPickupTime(JdbcSupport.getDateTime(rs, "pickup_time"));
        o.setKitchenReleaseAt(JdbcSupport.getDateTime(rs, "kitchen_release_at"));
        o.setReleasedToKdsAt(JdbcSupport.getDateTime(rs, "released_to_kds_at"));
        o.setPickupCode(rs.getString("pickup_code"));
        o.setReadyAt(JdbcSupport.getDateTime(rs, "ready_at"));
        o.setPickedUpAt(JdbcSupport.getDateTime(rs, "picked_up_at"));
        o.setHandoffByUserId(JdbcSupport.getInteger(rs, "handoff_by_user_id"));
        o.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
        o.setCompletedAt(JdbcSupport.getDateTime(rs, "completed_at"));
        o.setExpiredAt(JdbcSupport.getDateTime(rs, "expired_at"));
        o.setCustomerName(rs.getNString("customer_name"));
        o.setCustomerEmail(rs.getString("customer_email"));
        o.setHandoffByName(rs.getNString("handoff_by_name"));
        return o;
    }
}
