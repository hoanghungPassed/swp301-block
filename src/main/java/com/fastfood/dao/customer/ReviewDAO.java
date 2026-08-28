package com.fastfood.dao.customer;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.dto.Dtos.ReviewSummary;
import com.fastfood.model.entity.MenuEntities.Review;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReviewDAO {

    private static final String BASE =
            "SELECT r.review_id, r.product_id, r.customer_id, r.rating, r.comment, " +
            "       r.created_at, r.updated_at, u.full_name AS customer_name, " +
            "       p.name AS product_name " +
            "FROM dbo.Review  r " +
            "JOIN dbo.Users   u ON u.user_id    = r.customer_id " +
            "JOIN dbo.Product p ON p.product_id = r.product_id ";

    /** Chèn đánh giá mới và gán reviewId tự tăng vào entity. */
    public int insert(Connection con, Review review) throws SQLException {
        String sql = "INSERT INTO dbo.Review (product_id, customer_id, rating, comment, created_at) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, review.getProductId());
            ps.setInt(2, review.getCustomerId());
            ps.setInt(3, review.getRating());
            JdbcSupport.setString(ps, 4, review.getComment());
            JdbcSupport.setDateTime(ps, 5, review.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    review.setReviewId(keys.getInt(1));
                }
            }
        }
        return review.getReviewId();
    }

    /** Tìm một review theo khóa chính. */
    public Review findById(Connection con, int reviewId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE r.review_id = ?")) {
            ps.setInt(1, reviewId);
            List<Review> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Lấy các review của sản phẩm, ưu tiên review mới nhất. */
    public List<Review> findByProduct(Connection con, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE r.product_id = ? ORDER BY r.created_at DESC, r.review_id DESC")) {
            ps.setInt(1, productId);
            return collect(ps);
        }
    }

    /** Tìm review của đúng customer cho một sản phẩm. */
    public Review findMine(Connection con, int productId, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE r.product_id = ? AND r.customer_id = ?")) {
            ps.setInt(1, productId);
            ps.setInt(2, customerId);
            List<Review> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Tính COUNT và AVG rating của một sản phẩm. */
    public ReviewSummary summaryOf(Connection con, int productId) throws SQLException {
        ReviewSummary summary = new ReviewSummary();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) AS so_luot, AVG(CAST(rating AS DECIMAL(4,2))) AS diem_tb " +
                "FROM dbo.Review WHERE product_id = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    summary.setCount(rs.getInt("so_luot"));
                    BigDecimal avg = rs.getBigDecimal("diem_tb");
                    summary.setAverage(avg == null ? BigDecimal.ZERO : avg);
                }
            }
        }
        return summary;
    }

    /** Kiểm tra customer có đơn COMPLETED chứa product hay chưa. */
    public boolean hasCompletedPurchase(Connection con, int customerId, int productId)
            throws SQLException {
        String sql =
            "SELECT TOP 1 1 FROM dbo.Orders o " +
            "JOIN dbo.OrderItem oi ON oi.order_id = o.order_id " +
            "WHERE o.customer_id = ? AND oi.product_id = ? AND o.order_status = 'COMPLETED'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Cập nhật review chỉ khi reviewId thuộc đúng customerId. */
    public int update(Connection con, int reviewId, int customerId, int rating, String comment,
                      LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Review SET rating = ?, comment = ?, updated_at = ? " +
                     "WHERE review_id = ? AND customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, rating);
            JdbcSupport.setString(ps, 2, comment);
            JdbcSupport.setDateTime(ps, 3, now);
            ps.setInt(4, reviewId);
            ps.setInt(5, customerId);
            return ps.executeUpdate();
        }
    }

    /** Xóa review chỉ khi reviewId thuộc đúng customerId. */
    public int delete(Connection con, int reviewId, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.Review WHERE review_id = ? AND customer_id = ?")) {
            ps.setInt(1, reviewId);
            ps.setInt(2, customerId);
            return ps.executeUpdate();
        }
    }

    /** Chạy truy vấn và ánh xạ toàn bộ ResultSet thành danh sách Review. */
    private List<Review> collect(PreparedStatement ps) throws SQLException {
        List<Review> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Review r = new Review();
                r.setReviewId(rs.getInt("review_id"));
                r.setProductId(rs.getInt("product_id"));
                r.setCustomerId(rs.getInt("customer_id"));
                r.setRating(rs.getInt("rating"));
                r.setComment(rs.getNString("comment"));
                r.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                r.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                r.setCustomerName(rs.getNString("customer_name"));
                r.setProductName(rs.getNString("product_name"));
                list.add(r);
            }
        }
        return list;
    }
}
