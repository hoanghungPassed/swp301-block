package com.fastfood.dao.shared;

import com.fastfood.model.entity.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng Category. */
public class CategoryDAO {

    public List<Category> findActive(Connection con) throws SQLException {
        String sql = "SELECT category_id, name, status, display_order FROM dbo.Category " +
                     "WHERE status = 'ACTIVE' ORDER BY display_order, name";
        return query(con, sql);
    }

    /** Danh sách đầy đủ kèm số món, dùng cho màn hình quản trị. */
    public List<Category> findAllWithCount(Connection con) throws SQLException {
        String sql = "SELECT c.category_id, c.name, c.status, c.display_order, " +
                     "       (SELECT COUNT(*) FROM dbo.Product p WHERE p.category_id = c.category_id) AS product_count " +
                     "FROM dbo.Category c ORDER BY c.display_order, c.name";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Category c = map(rs);
                c.setProductCount(rs.getInt("product_count"));
                list.add(c);
            }
        }
        return list;
    }

    public Category findById(Connection con, int id) throws SQLException {
        String sql = "SELECT category_id, name, status, display_order FROM dbo.Category WHERE category_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int insert(Connection con, Category c) throws SQLException {
        String sql = "INSERT INTO dbo.Category (name, status, display_order) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setNString(1, c.getName());
            ps.setString(2, c.getStatus());
            ps.setInt(3, c.getDisplayOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    c.setCategoryId(keys.getInt(1));
                }
            }
        }
        return c.getCategoryId();
    }

    public void update(Connection con, Category c) throws SQLException {
        String sql = "UPDATE dbo.Category SET name = ?, status = ?, display_order = ? WHERE category_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setNString(1, c.getName());
            ps.setString(2, c.getStatus());
            ps.setInt(3, c.getDisplayOrder());
            ps.setInt(4, c.getCategoryId());
            ps.executeUpdate();
        }
    }

    /**
     * Đổi riêng trạng thái hiển thị — đây là thao tác Xoá của màn hình quản trị nhóm món.
     * Tách khỏi {@link #update} cùng lý do với {@code ProductDAO}: sửa tên nhóm không được
     * kéo theo việc cả nhóm biến mất khỏi thực đơn.
     */
    public void updateStatus(Connection con, int categoryId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Category SET status = ? WHERE category_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, categoryId);
            ps.executeUpdate();
        }
    }

    private List<Category> query(Connection con, String sql) throws SQLException {
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private Category map(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setCategoryId(rs.getInt("category_id"));
        c.setName(rs.getNString("name"));
        c.setStatus(rs.getString("status"));
        c.setDisplayOrder(rs.getInt("display_order"));
        return c;
    }
}
