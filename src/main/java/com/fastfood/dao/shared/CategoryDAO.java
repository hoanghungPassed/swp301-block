package com.fastfood.dao.shared;

import com.fastfood.model.entity.MenuEntities.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    public List<Category> findActive(Connection con) throws SQLException {
        String sql = "SELECT category_id, name, status, display_order FROM dbo.Category " +
                     "WHERE status = 'ACTIVE' ORDER BY display_order, name";
        return query(con, sql);
    }

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

    public List<Category> findAllWithCount(Connection con, int offset, int limit) throws SQLException {
        String sql = "SELECT c.category_id, c.name, c.status, c.display_order, " +
                     "       (SELECT COUNT(*) FROM dbo.Product p WHERE p.category_id = c.category_id) AS product_count " +
                     "FROM dbo.Category c ORDER BY c.display_order, c.name, c.category_id " +
                     "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<Category> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, offset);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Category c = map(rs);
                    c.setProductCount(rs.getInt("product_count"));
                    list.add(c);
                }
            }
        }
        return list;
    }

    public long countAll(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM dbo.Category");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
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
