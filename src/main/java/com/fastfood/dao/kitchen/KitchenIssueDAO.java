package com.fastfood.dao.kitchen;

import com.fastfood.model.entity.KitchenIssue;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/** Truy vấn bảng KitchenIssue. */
public class KitchenIssueDAO {

    private static final String BASE =
            "SELECT ki.issue_id, ki.order_item_id, ki.created_by, ki.issue_type, ki.description, " +
            "       ki.status, ki.created_at, ki.resolved_at, " +
            "       u.full_name AS created_by_name, oi.product_name_snapshot, oi.order_id " +
            "FROM dbo.KitchenIssue ki " +
            "JOIN dbo.Users u      ON u.user_id = ki.created_by " +
            "JOIN dbo.OrderItem oi ON oi.order_item_id = ki.order_item_id ";

    public int insert(Connection con, KitchenIssue issue) throws SQLException {
        String sql = "INSERT INTO dbo.KitchenIssue (order_item_id, created_by, issue_type, description, " +
                     "status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, issue.getOrderItemId());
            ps.setInt(2, issue.getCreatedBy());
            ps.setString(3, issue.getIssueType());
            JdbcSupport.setString(ps, 4, issue.getDescription());
            ps.setString(5, issue.getStatus());
            JdbcSupport.setDateTime(ps, 6, issue.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    issue.setIssueId(keys.getInt(1));
                }
            }
        }
        return issue.getIssueId();
    }

    public KitchenIssue findById(Connection con, int issueId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE ki.issue_id = ?")) {
            ps.setInt(1, issueId);
            List<KitchenIssue> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /**
     * Sửa mô tả sự cố.
     * <p>
     * Hai điều kiện "còn đang mở" và "đúng người báo" nằm ngay trong câu lệnh, không kiểm tra
     * trước rồi mới ghi: giữa hai bước đó người khác có thể vừa đánh dấu đã xử lý xong.
     */
    public int updateDescription(Connection con, int issueId, int userId, String description)
            throws SQLException {
        String sql = "UPDATE dbo.KitchenIssue SET description = ? " +
                     "WHERE issue_id = ? AND status = 'OPEN' AND created_by = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, description);
            ps.setInt(2, issueId);
            ps.setInt(3, userId);
            return ps.executeUpdate();
        }
    }

    /**
     * Thu hồi sự cố báo nhầm. Dùng lại cột {@code resolved_at} làm mốc đóng sự cố — cả hai
     * đường ra đều là "sự cố khép lại lúc nào", nên thêm một cột nữa chỉ để phân biệt
     * lý do đóng là thừa, trong khi cột {@code status} đã nói rõ điều đó.
     */
    public int cancel(Connection con, int issueId, int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.KitchenIssue SET status = 'CANCELLED', resolved_at = ? " +
                     "WHERE issue_id = ? AND status = 'OPEN' AND created_by = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, issueId);
            ps.setInt(3, userId);
            return ps.executeUpdate();
        }
    }

    /**
     * Còn bao nhiêu báo hết nguyên liệu đang mở cho một món.
     * <p>
     * Thu hồi một báo nhầm không đủ để bật món lại: cùng một món có thể đang bị nhiều người
     * báo hết. Đếm trước rồi mới bật, nếu không thì một lần bấm nhầm sẽ xoá sạch cảnh báo
     * thật của người khác và khách lại đặt được đúng món đang hết.
     */
    public int countOpenOutOfStockForProduct(Connection con, int productId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM dbo.KitchenIssue ki " +
                     "JOIN dbo.OrderItem oi ON oi.order_item_id = ki.order_item_id " +
                     "WHERE oi.product_id = ? AND ki.issue_type = 'OUT_OF_STOCK' AND ki.status = 'OPEN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int resolve(Connection con, int issueId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.KitchenIssue SET status = 'RESOLVED', resolved_at = ? " +
                     "WHERE issue_id = ? AND status = 'OPEN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, issueId);
            return ps.executeUpdate();
        }
    }

    public List<KitchenIssue> findOpen(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE ki.status = 'OPEN' ORDER BY ki.created_at DESC")) {
            return collect(ps);
        }
    }

    public List<KitchenIssue> findRecent(Connection con, int limit) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE.replaceFirst("SELECT", "SELECT TOP (" + limit + ")") + "ORDER BY ki.created_at DESC")) {
            return collect(ps);
        }
    }

    /**
     * Sự cố đang mở của một đơn cụ thể.
     * Thu ngân cần cả loại và mô tả để nói chuyện được với khách, không chỉ con số đếm.
     */
    public List<KitchenIssue> findOpenByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE oi.order_id = ? AND ki.status = 'OPEN' ORDER BY ki.created_at DESC")) {
            ps.setInt(1, orderId);
            return collect(ps);
        }
    }

    /** Số sự cố còn chưa xử lý — hiện trên màn hình điều phối của thu ngân. */
    public int countOpen(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.KitchenIssue WHERE status = 'OPEN'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<KitchenIssue> findByOrderItem(Connection con, int orderItemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE ki.order_item_id = ? ORDER BY ki.created_at DESC")) {
            ps.setInt(1, orderItemId);
            return collect(ps);
        }
    }

    private List<KitchenIssue> collect(PreparedStatement ps) throws SQLException {
        List<KitchenIssue> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                KitchenIssue i = new KitchenIssue();
                i.setIssueId(rs.getInt("issue_id"));
                i.setOrderItemId(rs.getInt("order_item_id"));
                i.setCreatedBy(rs.getInt("created_by"));
                i.setIssueType(rs.getString("issue_type"));
                i.setDescription(rs.getNString("description"));
                i.setStatus(rs.getString("status"));
                i.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                i.setResolvedAt(JdbcSupport.getDateTime(rs, "resolved_at"));
                i.setCreatedByName(rs.getNString("created_by_name"));
                i.setProductName(rs.getNString("product_name_snapshot"));
                i.setOrderId(rs.getInt("order_id"));
                list.add(i);
            }
        }
        return list;
    }
}
