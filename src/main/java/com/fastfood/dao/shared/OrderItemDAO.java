package com.fastfood.dao.shared;

import com.fastfood.model.entity.OrderItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/**
 * Truy vấn bảng OrderItem — vừa là dòng món của đơn, vừa là việc trên màn hình bếp.
 * <p>
 * Màn hình bếp chỉ thấy món của đơn đã được đưa xuống bếp. Đơn đặt trước đã thanh toán
 * nhưng chưa tới giờ vẫn nằm ngoài tầm nhìn của bếp — đó là điểm mấu chốt để món không bị
 * làm quá sớm.
 */
public class OrderItemDAO {

    private static final String COLS =
            "oi.order_item_id, oi.order_id, oi.product_id, oi.product_name_snapshot, oi.unit_price, " +
            "oi.quantity, oi.item_status, oi.assigned_to_user_id, oi.started_at, oi.ready_at, " +
            "oi.handed_over_at, oi.handed_over_by, oi.received_at, oi.received_by ";

    /**
     * Tên ba người liên quan tới một dòng món. Đi kèm {@link #NAME_JOINS} — dùng cái này thì
     * phải dùng cái kia, nếu không {@link #map} sẽ đọc một cột không tồn tại.
     */
    private static final String NAME_COLS =
            ", u.full_name AS assigned_to_name, hu.full_name AS handed_over_by_name, " +
            "  ru.full_name AS received_by_name ";

    private static final String NAME_JOINS =
            "LEFT JOIN dbo.Users u  ON u.user_id  = oi.assigned_to_user_id " +
            "LEFT JOIN dbo.Users hu ON hu.user_id = oi.handed_over_by " +
            "LEFT JOIN dbo.Users ru ON ru.user_id = oi.received_by ";

    /**
     * Thông tin của đơn mà màn hình bếp và màn hình quầy cần biết. Chỉ dùng được ở truy vấn
     * có tham gia bảng Orders, và phải đi cùng {@code map(rs, true)}.
     * <p>
     * Có {@code order_status} vì món đã nấu xong vẫn ở lại hàng chờ của quầy kể cả khi đơn bị
     * huỷ sau đó — món có thật, giấu đi thì không ai xử lý. Quầy cần nhìn thấy nó kèm nhãn
     * "đơn đã huỷ" để mang đi bỏ chứ không đưa cho khách.
     */
    private static final String ORDER_COLS = ", o.order_source, o.pickup_time, o.order_status ";

    /** Số sự cố còn mở của dòng món — hiện thành nhãn đỏ trên thẻ ở màn hình bếp. */
    private static final String OPEN_ISSUE_COL =
            ", (SELECT COUNT(*) FROM dbo.KitchenIssue ki " +
            "   WHERE ki.order_item_id = oi.order_item_id AND ki.status = 'OPEN') AS open_issue_count ";

    public void insert(Connection con, OrderItem item) throws SQLException {
        String sql = "INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, " +
                     "quantity, item_status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getProductId());
            ps.setNString(3, item.getProductNameSnapshot());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.setInt(5, item.getQuantity());
            ps.setString(6, item.getItemStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    item.setOrderItemId(keys.getInt(1));
                }
            }
        }
    }

    public List<OrderItem> findByOrder(Connection con, int orderId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi " + NAME_JOINS +
                     "WHERE oi.order_id = ? ORDER BY oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return collect(ps, false);
        }
    }

    public OrderItem findById(Connection con, int orderItemId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi " +
                     "JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.order_item_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs, true) : null;
            }
        }
    }

    /**
     * Hàng chờ của bếp: món chưa ai nhận, thuộc đơn đã được đưa xuống bếp.
     * Sắp theo giờ hẹn để đơn gấp hơn nằm trên; đơn tại quầy không có giờ hẹn nên
     * xếp theo thứ tự vào trước làm trước.
     */
    public List<OrderItem> findWaitingQueue(Connection con) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.item_status = 'WAITING' AND o.released_to_kds_at IS NOT NULL " +
                     "  AND o.order_status IN ('CONFIRMED','PREPARING') " +
                     "ORDER BY CASE WHEN o.pickup_time IS NULL THEN o.released_to_kds_at ELSE o.pickup_time END, " +
                     "         oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return collect(ps, true);
        }
    }

    /**
     * Việc mà một đầu bếp đang làm dở.
     * <p>
     * Điều kiện trạng thái đơn là bắt buộc chứ không thừa: thu ngân huỷ được cả đơn đang nấu
     * (xem {@link OrderDAO#markCancelledByStaff}), nên thiếu nó thì món của đơn vừa bị huỷ
     * vẫn nằm trong danh sách việc và đầu bếp tiếp tục nấu một đơn không còn tồn tại.
     */
    public List<OrderItem> findMyTasks(Connection con, int userId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.assigned_to_user_id = ? AND oi.item_status = 'PREPARING' " +
                     "  AND o.order_status IN ('CONFIRMED','PREPARING') " +
                     "ORDER BY CASE WHEN o.pickup_time IS NULL THEN oi.started_at ELSE o.pickup_time END";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return collect(ps, true);
        }
    }

    /**
     * Món đầu bếp đã làm xong nhưng chưa đưa ra quầy.
     * <p>
     * Không lọc theo trạng thái đơn như {@link #findMyTasks}: món đã nấu xong rồi thì dù đơn
     * vừa bị huỷ, nó vẫn đang nằm trong bếp và vẫn phải được đưa ra ngoài — bỏ khỏi danh sách
     * ở đây chỉ khiến món nằm lại mà không ai còn nhìn thấy.
     */
    public List<OrderItem> findAwaitingHandover(Connection con, int userId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.assigned_to_user_id = ? AND oi.item_status = 'READY' " +
                     "  AND oi.handed_over_at IS NULL " +
                     "ORDER BY oi.ready_at, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return collect(ps, true);
        }
    }

    /**
     * Món bếp đã đưa ra quầy mà thu ngân chưa xác nhận cầm — hàng chờ của màn hình quầy.
     * <p>
     * Sắp theo lúc bàn giao chứ không theo giờ hẹn: món ra trước thì nguội trước.
     */
    public List<OrderItem> findAwaitingCounter(Connection con) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.handed_over_at IS NOT NULL AND oi.received_at IS NULL " +
                     "ORDER BY oi.handed_over_at, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return collect(ps, true);
        }
    }

    /**
     * Mọi món đang nằm trong bếp lúc này, bất kể ai làm: chưa có người nhận, đang làm dở,
     * hoặc đã xong mà chưa đưa ra quầy.
     * <p>
     * Ba nhánh điều kiện trùng khít với {@link #findWaitingQueue}, {@link #findMyTasks} và
     * {@link #findAwaitingHandover} — đây chỉ là hợp của ba danh sách đó, bỏ ràng buộc về
     * người làm. Dùng để đổ vào ô chọn món ở màn báo sự cố: sự cố là chuyện của cả bếp, đầu
     * bếp này báo hộ món của đầu bếp kia là bình thường.
     */
    public List<OrderItem> findInKitchen(Connection con) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE o.released_to_kds_at IS NOT NULL AND ( " +
                     "        (oi.item_status IN ('WAITING','PREPARING') " +
                     "         AND o.order_status IN ('CONFIRMED','PREPARING')) " +
                     "     OR (oi.item_status = 'READY' AND oi.handed_over_at IS NULL)) " +
                     "ORDER BY oi.order_id, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return collect(ps, true);
        }
    }

    /**
     * Một trang món đã hoàn thành, dùng cho màn hình lịch sử của bếp.
     *
     * @param assignedTo lọc theo người làm; truyền 0 để lấy của cả bếp
     */
    public List<OrderItem> findReadyPage(Connection con, int assignedTo, int offset, int limit)
            throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS +
                     ORDER_COLS + ", 0 AS open_issue_count " +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.item_status = 'READY' " + assignedFilter(assignedTo) +
                     "ORDER BY oi.ready_at DESC, oi.order_item_id DESC " +
                     "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (assignedTo > 0) {
                ps.setInt(i++, assignedTo);
            }
            ps.setInt(i++, offset);
            ps.setInt(i, limit);
            return collect(ps, true);
        }
    }

    public long countReady(Connection con, int assignedTo) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dbo.OrderItem oi WHERE oi.item_status = 'READY' "
                     + assignedFilter(assignedTo);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (assignedTo > 0) {
                ps.setInt(1, assignedTo);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Mảnh điều kiện lọc theo người làm, dùng chung cho câu lấy trang và câu đếm.
     * Hai câu đó phải lọc giống hệt nhau, nếu không thanh chuyển trang sẽ báo tổng số của
     * cả bếp trong khi bảng chỉ liệt kê món của một người.
     */
    private static String assignedFilter(int assignedTo) {
        return assignedTo > 0 ? "AND oi.assigned_to_user_id = ? " : "";
    }

    /**
     * Đầu bếp nhận việc.
     * <p>
     * Hai điều kiện về món là chốt chặn tranh chấp: hai người cùng bấm nhận một món thì người
     * thứ hai nhận về 0 dòng và được báo món đã có người làm, thay vì ghi đè lên phân công của
     * người trước.
     * <p>
     * Hai điều kiện về đơn là chốt chặn nghiệp vụ, và phải <b>trùng khít</b> với điều kiện của
     * {@link #findWaitingQueue}. Trước đây chúng chỉ có ở truy vấn hàng chờ, nên việc giữ đơn
     * đặt trước tới sát giờ mới đưa xuống bếp thực chất chỉ là ẩn thẻ trên màn hình: gửi thẳng
     * một mã món vào là nấu được đơn còn chưa tới giờ. Đơn khi đó rơi vào trạng thái mâu thuẫn
     * — đang chế biến nhưng {@code released_to_kds_at} vẫn trống — và khách mất luôn quyền tự
     * huỷ đơn của chính mình, vì {@link #countInProgress} tính đó là "bếp đã bắt đầu làm".
     */
    public int claim(Connection con, int orderItemId, int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE oi SET assigned_to_user_id = ?, item_status = 'PREPARING', started_at = ? " +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " +
                     "WHERE oi.order_item_id = ? AND oi.item_status = 'WAITING' " +
                     "  AND oi.assigned_to_user_id IS NULL " +
                     "  AND o.released_to_kds_at IS NOT NULL " +
                     "  AND o.order_status IN ('CONFIRMED','PREPARING')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, orderItemId);
            return ps.executeUpdate();
        }
    }

    /**
     * Đánh dấu món xong. Chỉ người đang làm mới đánh dấu được.
     * <p>
     * Điều kiện về trạng thái đơn phải trùng khít với {@link #findMyTasks}, vì cùng một lý do
     * như ở {@link #claim}: thu ngân huỷ được cả đơn đang nấu, và nếu điều kiện chỉ nằm ở truy
     * vấn hiển thị thì món của đơn vừa bị huỷ vẫn đánh dấu xong được bằng cách gửi thẳng mã
     * món — rồi đi tiếp sang quầy như một món bình thường của một đơn không còn tồn tại.
     */
    public int markReady(Connection con, int orderItemId, int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE oi SET item_status = 'READY', ready_at = ? " +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " +
                     "WHERE oi.order_item_id = ? AND oi.item_status = 'PREPARING' " +
                     "  AND oi.assigned_to_user_id = ? " +
                     "  AND o.order_status IN ('CONFIRMED','PREPARING')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, orderItemId);
            ps.setInt(3, userId);
            return ps.executeUpdate();
        }
    }

    /**
     * Bếp đưa món ra quầy.
     * <p>
     * Ba điều kiện trong câu lệnh: món phải xong, phải chưa bàn giao lần nào, và phải đúng
     * người đang làm nó. Điều kiện cuối không phải để phân quyền mà để đối chiếu được — người
     * đưa món ra quầy phải là người biết món đó gồm những gì.
     */
    public int handOverToCounter(Connection con, int orderItemId, int userId, LocalDateTime now)
            throws SQLException {
        String sql = "UPDATE dbo.OrderItem SET handed_over_at = ?, handed_over_by = ? " +
                     "WHERE order_item_id = ? AND item_status = 'READY' " +
                     "  AND handed_over_at IS NULL AND assigned_to_user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, userId);
            ps.setInt(3, orderItemId);
            ps.setInt(4, userId);
            return ps.executeUpdate();
        }
    }

    /**
     * Thu ngân xác nhận đã cầm món tại quầy.
     * <p>
     * Không ràng buộc "đúng thu ngân nào" như phía bếp: quầy có thể đổi ca giữa chừng, và
     * người cầm món không nhất thiết là người sẽ giao cho khách.
     */
    public int receiveAtCounter(Connection con, int orderItemId, int cashierId, LocalDateTime now)
            throws SQLException {
        String sql = "UPDATE dbo.OrderItem SET received_at = ?, received_by = ? " +
                     "WHERE order_item_id = ? AND handed_over_at IS NOT NULL AND received_at IS NULL";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, cashierId);
            ps.setInt(3, orderItemId);
            return ps.executeUpdate();
        }
    }

    /**
     * Trả món về bếp khi quầy từ chối nhận.
     * <p>
     * Xoá hai mốc bàn giao chứ không thêm cột mới: món quay lại đúng trạng thái nó đang có
     * ngay trước lúc bếp bấm bàn giao, nên nó tự hiện lại ở {@link #findAwaitingHandover} của
     * người đã nấu và tự rời khỏi {@link #findAwaitingCounter} của quầy. Không xoá thì món
     * kẹt giữa hai màn hình: bếp không thấy để làm lại ({@link #handOverToCounter} đòi
     * {@code handed_over_at IS NULL}), quầy vẫn thấy nó nằm chờ, và cả đơn không giao được
     * vì {@link #countNotReceived} còn đếm nó.
     * <p>
     * Điều kiện {@code received_at IS NULL} trùng khít với {@link #receiveAtCounter}: hai
     * đường ra của một món trên quầy loại trừ nhau, ai chạy trước thì bên kia nhận 0 dòng.
     */
    public int returnToKitchen(Connection con, int orderItemId) throws SQLException {
        String sql = "UPDATE dbo.OrderItem SET handed_over_at = NULL, handed_over_by = NULL " +
                     "WHERE order_item_id = ? AND handed_over_at IS NOT NULL AND received_at IS NULL";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderItemId);
            return ps.executeUpdate();
        }
    }

    /**
     * Số món của đơn chưa nằm trong tay quầy. Bằng 0 mới giao cho khách được.
     * <p>
     * Đây là điều mà trạng thái đơn không nói được: đơn ở trạng thái sẵn sàng chỉ nghĩa là bếp
     * đã nấu xong, không có nghĩa món đã ra tới quầy.
     */
    public int countNotReceived(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE order_id = ? AND received_at IS NULL")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Số món đang nằm chờ trên quầy — hiện thành số đếm trên thanh điều hướng của thu ngân. */
    public int countAwaitingCounter(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem " +
                "WHERE handed_over_at IS NOT NULL AND received_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Số món của đơn chưa hoàn thành. Bằng 0 nghĩa là cả đơn đã sẵn sàng. */
    public int countUnready(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE order_id = ? AND item_status <> 'READY'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Số món của đơn đã rời khỏi hàng chờ, tức là bếp đã động tay vào.
     * <p>
     * Dùng để quyết định khách còn tự huỷ được không. Mốc đúng là "bếp đã bắt đầu làm",
     * không phải "đơn đã xuống bếp": hai thời điểm này cách nhau tới 20 phút, và trong
     * khoảng đó chưa tốn một đồng nguyên liệu nào nên huỷ vẫn hợp lý.
     * <p>
     * Người gọi phải khoá dòng đơn trước khi gọi — xem {@link OrderDAO#lockForUpdate}.
     */
    public int countInProgress(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE order_id = ? AND item_status <> 'WAITING'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countByStatus(Connection con, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE item_status = ?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private List<OrderItem> collect(PreparedStatement ps, boolean withOrderInfo) throws SQLException {
        List<OrderItem> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs, withOrderInfo));
            }
        }
        return list;
    }

    private OrderItem map(ResultSet rs, boolean withOrderInfo) throws SQLException {
        OrderItem i = new OrderItem();
        i.setOrderItemId(rs.getInt("order_item_id"));
        i.setOrderId(rs.getInt("order_id"));
        i.setProductId(rs.getInt("product_id"));
        i.setProductNameSnapshot(rs.getNString("product_name_snapshot"));
        i.setUnitPrice(JdbcSupport.getMoney(rs, "unit_price"));
        i.setQuantity(rs.getInt("quantity"));
        i.setItemStatus(rs.getString("item_status"));
        i.setAssignedToUserId(JdbcSupport.getInteger(rs, "assigned_to_user_id"));
        i.setStartedAt(JdbcSupport.getDateTime(rs, "started_at"));
        i.setReadyAt(JdbcSupport.getDateTime(rs, "ready_at"));
        i.setHandedOverAt(JdbcSupport.getDateTime(rs, "handed_over_at"));
        i.setHandedOverBy(JdbcSupport.getInteger(rs, "handed_over_by"));
        i.setReceivedAt(JdbcSupport.getDateTime(rs, "received_at"));
        i.setReceivedBy(JdbcSupport.getInteger(rs, "received_by"));
        i.setAssignedToName(rs.getNString("assigned_to_name"));
        i.setHandedOverByName(rs.getNString("handed_over_by_name"));
        i.setReceivedByName(rs.getNString("received_by_name"));
        i.setOpenIssueCount(rs.getInt("open_issue_count"));
        if (withOrderInfo) {
            i.setOrderSource(rs.getString("order_source"));
            i.setPickupTime(JdbcSupport.getDateTime(rs, "pickup_time"));
            i.setOrderStatus(rs.getString("order_status"));
        }
        return i;
    }
}
