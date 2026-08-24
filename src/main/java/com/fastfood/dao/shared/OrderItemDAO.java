package com.fastfood.dao.shared;

import com.fastfood.model.entity.OrderEntities.OrderItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class OrderItemDAO {

    private static final String COLS =
            "oi.order_item_id, oi.order_id, oi.product_id, oi.product_name_snapshot, oi.unit_price, " +
            "oi.quantity, oi.item_status, oi.assigned_to_user_id, oi.started_at, oi.ready_at, " +
            "oi.handed_over_at, oi.handed_over_by, oi.received_at, oi.received_by ";

    private static final String NAME_COLS =
            ", u.full_name AS assigned_to_name, hu.full_name AS handed_over_by_name, " +
            "  ru.full_name AS received_by_name ";

    private static final String NAME_JOINS =
            "LEFT JOIN dbo.Users u  ON u.user_id  = oi.assigned_to_user_id " +
            "LEFT JOIN dbo.Users hu ON hu.user_id = oi.handed_over_by " +
            "LEFT JOIN dbo.Users ru ON ru.user_id = oi.received_by ";

    private static final String ORDER_COLS = ", o.order_source, o.pickup_time, o.order_status ";

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

    /*
     * Ba câu dưới đây phục vụ màn bếp làm việc theo đơn. Chúng trả về MỌI món của những đơn
     * đang thuộc từng khối — kể cả món đã xong hay đã ra quầy — để thẻ trên màn hình nói được
     * đủ "đơn này có mấy món, còn mấy món chưa xong". Việc gom món thành đơn làm ở tầng dịch
     * vụ, nên thứ tự ORDER BY phải giữ các món của cùng một đơn nằm liền nhau.
     */

    /** Đơn chưa ai đụng tới: còn món chờ và không có món nào đã có người bếp nhận. */
    public List<OrderItem> findWaitingQueueOrders(Connection con) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE o.released_to_kds_at IS NOT NULL " +
                     "  AND o.order_status IN ('CONFIRMED','PREPARING') " +
                     "  AND EXISTS (SELECT 1 FROM dbo.OrderItem w " +
                     "              WHERE w.order_id = oi.order_id AND w.item_status = 'WAITING') " +
                     "  AND NOT EXISTS (SELECT 1 FROM dbo.OrderItem a " +
                     "                  WHERE a.order_id = oi.order_id " +
                     "                    AND a.assigned_to_user_id IS NOT NULL) " +
                     "ORDER BY CASE WHEN o.pickup_time IS NULL THEN o.released_to_kds_at ELSE o.pickup_time END, " +
                     "         oi.order_id, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return collect(ps, true);
        }
    }

    /**
     * Đơn đang trong tay tôi: có món mang tên tôi và đơn vẫn còn món chưa xong.
     *
     * <p>Không đòi món của tôi phải đang dở, vì đơn nhận lẻ từ trước có thể còn sót món chưa ai
     * nhận. Những đơn ấy vẫn phải hiện ở đây kèm nút nhận nốt phần còn lại — nếu chỉ tìm theo
     * món đang làm thì chúng biến mất khỏi mọi khối: hàng chờ đã loại đơn có người đụng vào,
     * còn khối bàn giao chỉ nhận đơn đã xong hết món.
     */
    public List<OrderItem> findMyOrderItems(Connection con, int userId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE o.order_status IN ('CONFIRMED','PREPARING') " +
                     "  AND EXISTS (SELECT 1 FROM dbo.OrderItem m " +
                     "              WHERE m.order_id = oi.order_id AND m.assigned_to_user_id = ?) " +
                     "  AND EXISTS (SELECT 1 FROM dbo.OrderItem d " +
                     "              WHERE d.order_id = oi.order_id " +
                     "                AND d.item_status IN ('WAITING','PREPARING')) " +
                     "ORDER BY CASE WHEN o.pickup_time IS NULL THEN o.released_to_kds_at ELSE o.pickup_time END, " +
                     "         oi.order_id, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return collect(ps, true);
        }
    }

    /**
     * Đơn của tôi đã xong hết món và còn phần chưa ra quầy. Đơn còn món dở nằm lại khối
     * "đang làm", vì thu ngân chỉ nên nhận một lần cho trọn đơn.
     */
    public List<OrderItem> findHandoverOrderItems(Connection con, int userId) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE EXISTS (SELECT 1 FROM dbo.OrderItem r " +
                     "              WHERE r.order_id = oi.order_id AND r.assigned_to_user_id = ? " +
                     "                AND r.item_status = 'READY' AND r.handed_over_at IS NULL) " +
                     "  AND NOT EXISTS (SELECT 1 FROM dbo.OrderItem d " +
                     "                  WHERE d.order_id = oi.order_id " +
                     "                    AND d.item_status IN ('WAITING','PREPARING')) " +
                     "ORDER BY oi.order_id, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return collect(ps, true);
        }
    }

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
     * Món đang chờ trên quầy, sắp xếp sao cho các món cùng một đơn nằm liền nhau và đơn nào
     * bếp đưa ra trước thì đứng trước — tầng dịch vụ gom lại thành từng đơn để thu ngân nhận
     * một lần cho trọn đơn.
     */
    public List<OrderItem> findAwaitingCounterOrders(Connection con) throws SQLException {
        String sql = "SELECT " + COLS + NAME_COLS + ORDER_COLS + OPEN_ISSUE_COL +
                     "FROM dbo.OrderItem oi JOIN dbo.Orders o ON o.order_id = oi.order_id " + NAME_JOINS +
                     "WHERE oi.handed_over_at IS NOT NULL AND oi.received_at IS NULL " +
                     "ORDER BY MIN(oi.handed_over_at) OVER (PARTITION BY oi.order_id), " +
                     "         oi.order_id, oi.order_item_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return collect(ps, true);
        }
    }

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

    private static String assignedFilter(int assignedTo) {
        return assignedTo > 0 ? "AND oi.assigned_to_user_id = ? " : "";
    }

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

    public int handOverToCounter(Connection con, int orderItemId, int userId, LocalDateTime now)
            throws SQLException {
        String sql = "UPDATE oi SET handed_over_at = ?, handed_over_by = ? " +
                     "FROM dbo.OrderItem oi " +
                     "WHERE oi.order_item_id = ? AND oi.item_status = 'READY' " +
                     "  AND oi.handed_over_at IS NULL AND oi.assigned_to_user_id = ? " +
                     "  AND NOT EXISTS (SELECT 1 FROM dbo.KitchenIssue ki " +
                     "                  WHERE ki.order_item_id = oi.order_item_id " +
                     "                    AND ki.status = 'OPEN')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, userId);
            ps.setInt(3, orderItemId);
            ps.setInt(4, userId);
            return ps.executeUpdate();
        }
    }

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

    public int returnToKitchen(Connection con, int orderItemId) throws SQLException {
        String sql = "UPDATE dbo.OrderItem SET handed_over_at = NULL, handed_over_by = NULL " +
                     "WHERE order_item_id = ? AND handed_over_at IS NOT NULL AND received_at IS NULL";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderItemId);
            return ps.executeUpdate();
        }
    }

    public int countNotReceived(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE order_id = ? AND received_at IS NULL")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countAwaitingCounter(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem " +
                "WHERE handed_over_at IS NOT NULL AND received_at IS NULL");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int countUnready(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderItem WHERE order_id = ? AND item_status <> 'READY'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

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
