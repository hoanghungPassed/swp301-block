package com.fastfood.dao.customer;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.Favourite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Truy vấn bảng Favourite — món quen của khách. */
public class FavouriteDAO {

    private static final String BASE =
            "SELECT f.favourite_id, f.customer_id, f.product_id, f.note, f.created_at, f.updated_at, " +
            "       p.name AS product_name, p.image_url, p.price, p.is_available, " +
            "       p.status AS product_status, c.name AS category_name " +
            "FROM dbo.Favourite f " +
            "JOIN dbo.Product  p ON p.product_id  = f.product_id " +
            "JOIN dbo.Category c ON c.category_id = p.category_id ";

    public int insert(Connection con, Favourite fav) throws SQLException {
        String sql = "INSERT INTO dbo.Favourite (customer_id, product_id, note, created_at) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, fav.getCustomerId());
            ps.setInt(2, fav.getProductId());
            JdbcSupport.setString(ps, 3, fav.getNote());
            JdbcSupport.setDateTime(ps, 4, fav.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    fav.setFavouriteId(keys.getInt(1));
                }
            }
        }
        return fav.getFavouriteId();
    }

    public Favourite findById(Connection con, int favouriteId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE f.favourite_id = ?")) {
            ps.setInt(1, favouriteId);
            List<Favourite> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Món quen của một khách, mới đánh dấu trước. */
    public List<Favourite> findByCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE f.customer_id = ? ORDER BY f.created_at DESC, f.favourite_id DESC")) {
            ps.setInt(1, customerId);
            return collect(ps);
        }
    }

    /**
     * Chỉ lấy mã món đã đánh dấu, để tô dấu trên lưới thực đơn.
     * <p>
     * Thực đơn có hàng chục món; hỏi từng món "khách này đã đánh dấu chưa" sẽ thành hàng chục
     * lượt truy vấn cho một lần mở trang. Một lượt lấy hết rồi tra trong bộ nhớ là đủ.
     */
    public Set<Integer> productIdsOf(Connection con, int customerId) throws SQLException {
        Set<Integer> ids = new LinkedHashSet<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT product_id FROM dbo.Favourite WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    public int updateNote(Connection con, int favouriteId, int customerId, String note,
                          LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Favourite SET note = ?, updated_at = ? " +
                     "WHERE favourite_id = ? AND customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, note);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, favouriteId);
            ps.setInt(4, customerId);
            return ps.executeUpdate();
        }
    }

    public int delete(Connection con, int favouriteId, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.Favourite WHERE favourite_id = ? AND customer_id = ?")) {
            ps.setInt(1, favouriteId);
            ps.setInt(2, customerId);
            return ps.executeUpdate();
        }
    }

    /** Bỏ đánh dấu ngay từ lưới thực đơn, nơi chỉ biết mã món chứ không biết mã bản ghi. */
    public int deleteByProduct(Connection con, int customerId, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.Favourite WHERE customer_id = ? AND product_id = ?")) {
            ps.setInt(1, customerId);
            ps.setInt(2, productId);
            return ps.executeUpdate();
        }
    }

    private List<Favourite> collect(PreparedStatement ps) throws SQLException {
        List<Favourite> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Favourite f = new Favourite();
                f.setFavouriteId(rs.getInt("favourite_id"));
                f.setCustomerId(rs.getInt("customer_id"));
                f.setProductId(rs.getInt("product_id"));
                f.setNote(rs.getNString("note"));
                f.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                f.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                f.setProductName(rs.getNString("product_name"));
                f.setCategoryName(rs.getNString("category_name"));
                f.setImageUrl(rs.getString("image_url"));
                f.setPrice(rs.getBigDecimal("price"));
                f.setAvailable(rs.getBoolean("is_available"));
                f.setProductStatus(rs.getString("product_status"));
                list.add(f);
            }
        }
        return list;
    }
}
