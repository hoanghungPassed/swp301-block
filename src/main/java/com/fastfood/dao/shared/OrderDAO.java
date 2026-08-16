package com.fastfood.dao.shared;

import com.fastfood.common.constant.OrderStatus;
import com.fastfood.model.entity.Order;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/**
 * Truy vấn bảng Orders.
 * <p>
 * Các phương thức đổi trạng thái ở đây đều gộp điều kiện kiểm tra vào chính câu UPDATE
 * và trả về số dòng bị ảnh hưởng. Đây là cách chống trùng lặp khi nhiều luồng cùng chạy:
 * đọc trước rồi mới ghi sẽ để lọt trường hợp hai luồng cùng đọc thấy điều kiện đúng.
 */
public class OrderDAO {

    private static final String COLS =
            "o.order_id, o.customer_id, o.created_by_user_id, o.order_source, o.total_amount, " +
            "o.order_status, o.idempotency_key, o.pickup_time, o.kitchen_release_at, " +
            "o.released_to_kds_at, o.pickup_code, o.ready_at, o.picked_up_at, o.handoff_by_user_id, " +
            "o.created_at, o.completed_at, o.cancelled_at, o.expired_at ";

    private static final String BASE =
            "SELECT " + COLS + ", cu.full_name AS customer_name, cu.email AS customer_email, " +
            "       hu.full_name AS handoff_by_name " +
            "FROM dbo.Orders o " +
            "LEFT JOIN dbo.Users cu ON cu.user_id = o.customer_id " +
            "LEFT JOIN dbo.Users hu ON hu.user_id = o.handoff_by_user_id ";

    // ---------------------------------------------------------------- ghi

    public int insert(Connection con, Order o) throws SQLException {
        String sql = "INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, " +
                     "order_status, idempotency_key, pickup_time, kitchen_release_at, released_to_kds_at, " +
                     "pickup_code, created_at, shift_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            JdbcSupport.setInteger(ps, 12, o.getShiftId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    o.setOrderId(keys.getInt(1));
                }
            }
        }
        return o.getOrderId();
    }

    public void updateTotal(Connection con, int orderId, BigDecimal total) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Orders SET total_amount = ? WHERE order_id = ?")) {
            ps.setBigDecimal(1, total);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    /**
     * Xác nhận đơn đặt trước sau khi tiền đã về: sinh mã nhận hàng và chốt giờ đưa xuống bếp.
     * Điều kiện trạng thái nằm ngay trong câu lệnh nên cổng thanh toán gọi lại lần hai
     * sẽ không làm gì cả và trả về 0.
     */
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

    /**
     * Đưa đơn xuống bếp đúng một lần.
     * <p>
     * Điều kiện {@code released_to_kds_at IS NULL} là chốt chặn: Scheduler chạy lại,
     * hoặc hai máy chủ cùng chạy, thì lần thứ hai trả về 0 và không sinh việc trùng cho bếp.
     */
    public int markReleasedToKds(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET released_to_kds_at = ? " +
                     "WHERE order_id = ? AND released_to_kds_at IS NULL AND order_status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Chuyển sang đang chế biến khi món đầu tiên được bếp nhận. */
    public int markPreparing(Connection con, int orderId) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'PREPARING' " +
                     "WHERE order_id = ? AND order_status = 'CONFIRMED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate();
        }
    }

    /** Chuyển sang sẵn sàng khi món cuối cùng xong. Mốc ready_at là mẫu số của chỉ số đúng hẹn. */
    public int markReady(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'READY', ready_at = ? " +
                     "WHERE order_id = ? AND order_status IN ('CONFIRMED', 'PREPARING')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Ghi nhận đã giao món cho khách. */
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

    /**
     * Khách tự huỷ đơn. Nhận hai trạng thái: chờ thanh toán (khách đổi ý trước khi trả tiền)
     * và đã xác nhận (đã trả tiền nhưng bếp chưa động tay).
     * <p>
     * Điều kiện "bếp chưa động tay" <b>không</b> nằm ở đây mà ở phía gọi, bằng cách đếm số
     * món đã rời hàng chờ trong lúc đang giữ khoá dòng đơn — xem
     * {@link com.fastfood.service.customer.CustomerOrderService#cancelByCustomer}. Lý do: mốc chặn đúng là
     * "đã có món được nhận làm", không phải "đơn đã xuống bếp"; hai thời điểm cách nhau 20 phút
     * và trong khoảng đó huỷ vẫn chưa gây tốn kém gì.
     */
    public int markCancelled(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'CANCELLED', cancelled_at = ? " +
                     "WHERE order_id = ? AND order_status IN ('PENDING_PAYMENT', 'CONFIRMED')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /**
     * Huỷ đơn theo quyết định của nhân viên tại quầy.
     * <p>
     * Khác {@link #markCancelled} ở chỗ không đòi {@code released_to_kds_at IS NULL}: thu ngân
     * là người nói chuyện trực tiếp với khách nên phải đóng được cả đơn đang nấu dở và đơn
     * khách không tới lấy. Thiếu đường này thì đơn READY không có ai đến nhận sẽ nằm lại
     * vĩnh viễn, và bếp báo hết nguyên liệu cũng không ai xử lý được.
     * <p>
     * Vẫn chặn ở các trạng thái kết thúc: đơn đã giao thì không huỷ ngược được.
     */
    public int markCancelledByStaff(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'CANCELLED', cancelled_at = ? " +
                     "WHERE order_id = ? AND order_status IN ('CONFIRMED', 'PREPARING', 'READY')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /** Hết hạn thanh toán. Khác huỷ ở chỗ đơn này chưa từng được trả tiền và chấp nhận. */
    public int markExpired(Connection con, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Orders SET order_status = 'EXPIRED', expired_at = ? " +
                     "WHERE order_id = ? AND order_status = 'PENDING_PAYMENT'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderId);
            return ps.executeUpdate();
        }
    }

    /**
     * Khoá dòng đơn trong giao dịch hiện tại.
     * <p>
     * Dùng trước khi đếm số món chưa xong: hai đầu bếp đánh dấu hai món cuối cùng gần như
     * đồng thời, nếu không khoá thì cả hai đều thấy "vẫn còn món chưa xong" và đơn kẹt mãi
     * ở trạng thái đang chế biến.
     */
    public void lockForUpdate(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT order_id FROM dbo.Orders WITH (UPDLOCK, ROWLOCK) WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
        }
    }

    // ---------------------------------------------------------------- đọc

    public Order findById(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tra đơn theo mã nhận hàng khi khách tới quầy. */
    public Order findByPickupCode(Connection con, String pickupCode) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.pickup_code = ?")) {
            ps.setString(1, pickupCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Order findByIdempotencyKey(Connection con, String key) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE o.idempotency_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Lịch sử đơn của một khách, có lọc theo trạng thái và khoảng thời gian đặt.
     * <p>
     * Điều kiện {@code customer_id} luôn có mặt bất kể bộ lọc nào được chọn — đó là thứ đảm bảo
     * không ai xem được đơn của người khác, nên nó nằm ngoài phần lọc tuỳ chọn chứ không đi
     * chung vào đó.
     */
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

    public long countByCustomer(Connection con, int customerId, String status,
                                LocalDateTime from, LocalDateTime to) throws SQLException {
        List<Object> params = new ArrayList<>();
        // Đếm không cần nối sang bảng Users: mọi điều kiện lọc đều nằm trên cột của Orders.
        String sql = "SELECT COUNT(*) FROM dbo.Orders o "
                   + customerWhere(customerId, status, from, to, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Mệnh đề lọc dùng chung cho câu lấy dữ liệu và câu đếm.
     * Viết hai lần thì sớm muộn hai bên cũng lệch nhau, và khi đó số trang không khớp với
     * số dòng thật sự lấy được.
     */
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

    /** Gán tham số lọc, trả về vị trí tham số kế tiếp còn trống. */
    private int bindAll(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return params.size() + 1;
    }

    /**
     * Đơn chờ thanh toán còn dang dở của khách, nếu có.
     * <p>
     * Giỏ hàng chỉ được dọn khi tiền về, nên trong lúc khách còn đang ở cổng thanh toán thì
     * giỏ vẫn nguyên. Không kiểm tra chỗ này thì khách quay lại giỏ và đặt tiếp sẽ tạo đơn
     * thứ hai cho cùng số hàng đó.
     */
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

    /** Các đơn đang theo dõi được của khách: chưa kết thúc vòng đời. */
    public List<Order> findActiveByCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE +
                "WHERE o.customer_id = ? AND o.order_status IN " +
                "('PENDING_PAYMENT','CONFIRMED','PREPARING','READY') ORDER BY o.pickup_time")) {
            ps.setInt(1, customerId);
            return collect(ps);
        }
    }

    /**
     * Bốn tab trên màn hình đơn hàng của thu ngân.
     * Tab quá hạn tính ngay trong SQL để không phải tải toàn bộ đơn READY về rồi lọc ở Java.
     * <p>
     * Bốn tab phải phủ kín mọi đơn chưa kết thúc, nếu không thì có đơn không hiện ở đâu cả
     * và thu ngân chỉ tìm ra nó khi khách hỏi. Cách chia: tab đặt trước giữ đơn Online từ lúc
     * xác nhận tới lúc xong món, tab tại quầy giữ đơn POS tương ứng, tab sẵn sàng gom cả hai
     * kênh khi món đã xong, tab quá hạn là lát cắt con của tab sẵn sàng.
     */
    public List<Order> findForDashboard(Connection con, String tab, LocalDateTime now, int overdueMinutes)
            throws SQLException {
        String where;
        switch (tab == null ? "" : tab) {
            case "SCHEDULED":
                // Cả CONFIRMED lẫn PREPARING: đơn đặt trước đã xuống bếp và đang được làm vẫn
                // là đơn hẹn giờ. Chỉ lấy CONFIRMED thì đơn rơi khỏi tab này ngay khi đầu bếp
                // nhận việc, và mắc kẹt ngoài mọi tab cho tới lúc món xong.
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

    /** Tra cứu đơn cho màn hình lịch sử và kiểm toán. */
    public List<Order> search(Connection con, String source, String status,
                              LocalDateTime from, LocalDateTime to,
                              int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        // Sắp thêm theo order_id để thứ tự luôn xác định: nhiều đơn có thể trùng
        // created_at tới từng giây, và khi đó ranh giới giữa hai trang sẽ chập chờn —
        // cùng một đơn hiện ở cả trang 1 lẫn trang 2, hoặc biến mất khỏi cả hai.
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

    /** Mệnh đề lọc dùng chung cho câu lấy dữ liệu và câu đếm, để hai bên không lệch nhau. */
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

    /**
     * Các đơn đã tới giờ đưa xuống bếp mà chưa được đưa.
     * Truy vấn này chạy mỗi 30 giây nên có riêng một chỉ mục lọc phục vụ nó.
     */
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

    /**
     * Các đơn chờ thanh toán đã quá hạn.
     * <p>
     * Dùng {@code BASE} để lấy kèm tên và email khách chứ không chọn NULL cho nhanh: bộ hẹn giờ
     * còn phải báo cho khách biết đơn đã hết hiệu lực, mà thiếu email thì tin nhắn đi vào chỗ
     * trống và không có gì báo lỗi — hỏng lặng lẽ, kiểu khó phát hiện nhất.
     */
    public List<Order> findExpiredCandidates(Connection con, LocalDateTime deadline) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE o.order_status = 'PENDING_PAYMENT' AND o.created_at <= ?")) {
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

    // ---------------------------------------------------------------- ánh xạ

    private List<Order> collect(PreparedStatement ps) throws SQLException {
        List<Order> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

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
        o.setCancelledAt(JdbcSupport.getDateTime(rs, "cancelled_at"));
        o.setExpiredAt(JdbcSupport.getDateTime(rs, "expired_at"));
        o.setCustomerName(rs.getNString("customer_name"));
        o.setCustomerEmail(rs.getString("customer_email"));
        o.setHandoffByName(rs.getNString("handoff_by_name"));
        return o;
    }
}
