package com.fastfood.dao.customer;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.OrderEntities.OrderTemplate;
import com.fastfood.model.entity.OrderEntities.OrderTemplateItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderTemplateDAO {

    private static final String BASE =
            "SELECT t.template_id, t.customer_id, t.name, t.created_at, t.updated_at " +
            "FROM dbo.OrderTemplate t ";

    private static final String ITEM_BASE =
            "SELECT i.template_item_id, i.template_id, i.product_id, i.quantity, " +
            "       p.name AS product_name, p.price, p.is_available, p.status AS product_status " +
            "FROM dbo.OrderTemplateItem i " +
            "JOIN dbo.Product p ON p.product_id = i.product_id ";

    /** Chèn phần đầu mẫu đặt nhanh và gán templateId tự tăng vào entity. */
    public int insert(Connection con, OrderTemplate template) throws SQLException {
        String sql = "INSERT INTO dbo.OrderTemplate (customer_id, name, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, template.getCustomerId());
            JdbcSupport.setString(ps, 2, template.getName());
            JdbcSupport.setDateTime(ps, 3, template.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    template.setTemplateId(keys.getInt(1));
                }
            }
        }
        return template.getTemplateId();
    }

    /** Tìm một mẫu theo id và nạp kèm danh sách món của mẫu. */
    public OrderTemplate findById(Connection con, int templateId) throws SQLException {
        OrderTemplate template;
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE t.template_id = ?")) {
            ps.setInt(1, templateId);
            List<OrderTemplate> list = collect(ps);
            if (list.isEmpty()) {
                return null;
            }
            template = list.get(0);
        }
        template.setItems(findItems(con, templateId));
        return template;
    }

    /** Lấy toàn bộ mẫu thuộc customer và nạp các item cho từng mẫu. */
    public List<OrderTemplate> findByCustomer(Connection con, int customerId) throws SQLException {
        List<OrderTemplate> templates;
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE t.customer_id = ? ORDER BY t.created_at DESC, t.template_id DESC")) {
            ps.setInt(1, customerId);
            templates = collect(ps);
        }
        if (templates.isEmpty()) {
            return templates;
        }

        Map<Integer, List<OrderTemplateItem>> byTemplate = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement(
                ITEM_BASE + "JOIN dbo.OrderTemplate t ON t.template_id = i.template_id " +
                            "WHERE t.customer_id = ? ORDER BY i.template_item_id")) {
            ps.setInt(1, customerId);
            for (OrderTemplateItem item : collectItems(ps)) {
                byTemplate.computeIfAbsent(item.getTemplateId(), k -> new ArrayList<>()).add(item);
            }
        }
        for (OrderTemplate template : templates) {
            template.setItems(byTemplate.get(template.getTemplateId()));
        }
        return templates;
    }

    /** Đếm số mẫu customer đang lưu để Service áp dụng giới hạn tối đa. */
    public int countByCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.OrderTemplate WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Đổi tên mẫu chỉ khi templateId thuộc đúng customerId. */
    public int rename(Connection con, int templateId, int customerId, String name,
                      LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.OrderTemplate SET name = ?, updated_at = ? " +
                     "WHERE template_id = ? AND customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, name);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, templateId);
            ps.setInt(4, customerId);
            return ps.executeUpdate();
        }
    }

    /** Cập nhật thời điểm mẫu được sửa gần nhất. */
    public int touch(Connection con, int templateId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.OrderTemplate SET updated_at = ? WHERE template_id = ?")) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, templateId);
            return ps.executeUpdate();
        }
    }

    /** Xóa mẫu thuộc customer; các item liên quan được xử lý theo ràng buộc database. */
    public int delete(Connection con, int templateId, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.OrderTemplate WHERE template_id = ? AND customer_id = ?")) {
            ps.setInt(1, templateId);
            ps.setInt(2, customerId);
            return ps.executeUpdate();
        }
    }

    /** Lấy các món và thông tin product hiện tại trong một mẫu. */
    public List<OrderTemplateItem> findItems(Connection con, int templateId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                ITEM_BASE + "WHERE i.template_id = ? ORDER BY i.template_item_id")) {
            ps.setInt(1, templateId);
            return collectItems(ps);
        }
    }

    /** Thêm món mới hoặc cộng quantity nếu product đã tồn tại trong mẫu. */
    public void addItem(Connection con, int templateId, int productId, int quantity)
            throws SQLException {
        String sql =
            "MERGE dbo.OrderTemplateItem AS dich " +
            "USING (SELECT ? AS template_id, ? AS product_id, ? AS quantity) AS nguon " +
            "   ON dich.template_id = nguon.template_id AND dich.product_id = nguon.product_id " +
            "WHEN MATCHED THEN UPDATE SET dich.quantity = dich.quantity + nguon.quantity " +
            "WHEN NOT MATCHED THEN INSERT (template_id, product_id, quantity) " +
            "     VALUES (nguon.template_id, nguon.product_id, nguon.quantity);";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, templateId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    /** Gán lại quantity của một product trong mẫu. */
    public int updateItemQuantity(Connection con, int templateId, int productId, int quantity)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.OrderTemplateItem SET quantity = ? " +
                "WHERE template_id = ? AND product_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, templateId);
            ps.setInt(3, productId);
            return ps.executeUpdate();
        }
    }

    /** Xóa một product khỏi mẫu. */
    public int removeItem(Connection con, int templateId, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.OrderTemplateItem WHERE template_id = ? AND product_id = ?")) {
            ps.setInt(1, templateId);
            ps.setInt(2, productId);
            return ps.executeUpdate();
        }
    }

    /** Chạy truy vấn và ánh xạ ResultSet thành danh sách OrderTemplate. */
    private List<OrderTemplate> collect(PreparedStatement ps) throws SQLException {
        List<OrderTemplate> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderTemplate t = new OrderTemplate();
                t.setTemplateId(rs.getInt("template_id"));
                t.setCustomerId(rs.getInt("customer_id"));
                t.setName(rs.getNString("name"));
                t.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                t.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                list.add(t);
            }
        }
        return list;
    }

    /** Chạy truy vấn và ánh xạ ResultSet thành danh sách OrderTemplateItem. */
    private List<OrderTemplateItem> collectItems(PreparedStatement ps) throws SQLException {
        List<OrderTemplateItem> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderTemplateItem i = new OrderTemplateItem();
                i.setTemplateItemId(rs.getInt("template_item_id"));
                i.setTemplateId(rs.getInt("template_id"));
                i.setProductId(rs.getInt("product_id"));
                i.setQuantity(rs.getInt("quantity"));
                i.setProductName(rs.getNString("product_name"));
                i.setUnitPrice(rs.getBigDecimal("price"));
                i.setAvailable(rs.getBoolean("is_available"));
                i.setProductStatus(rs.getString("product_status"));
                list.add(i);
            }
        }
        return list;
    }
}
