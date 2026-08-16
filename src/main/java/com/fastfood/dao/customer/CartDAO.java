package com.fastfood.dao.customer;

import com.fastfood.model.entity.CartItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/** Truy vấn giỏ hàng. Gộp cả Cart và CartItem vì hai bảng luôn dùng cùng nhau. */
public class CartDAO {

    /**
     * Lấy giỏ của khách, chưa có thì tạo mới. Mỗi khách chỉ có đúng một giỏ.
     * <p>
     * Cột user_id có ràng buộc duy nhất, nên hai yêu cầu gần như cùng lúc của cùng một khách
     * (mở trang thực đơn trong lúc bấm thêm món chẳng hạn) đều thấy "chưa có giỏ" rồi cùng ghi.
     * Người thứ hai đụng ràng buộc; khi đó đọc lại giỏ vừa được tạo thay vì trả lỗi ra ngoài.
     */
    public int getOrCreateCartId(Connection con, int userId, LocalDateTime now) throws SQLException {
        Integer existing = findCartId(con, userId);
        if (existing != null) {
            return existing;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO dbo.Cart (user_id, updated_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            Integer created = findCartId(con, userId);
            if (created == null) {
                throw e;
            }
            return created;
        }
    }

    private Integer findCartId(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT cart_id FROM dbo.Cart WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /**
     * Các dòng trong giỏ, kèm tên, giá và tình trạng còn bán của món.
     * Lấy kèm để màn hình giỏ hàng cảnh báo ngay khi có món vừa hết hàng.
     */
    public List<CartItem> findItems(Connection con, int cartId) throws SQLException {
        String sql = "SELECT ci.cart_item_id, ci.cart_id, ci.product_id, ci.quantity, " +
                     "       p.name AS product_name, p.price, p.image_url, p.is_available, " +
                     "       p.status AS product_status, c.status AS category_status " +
                     "FROM dbo.CartItem ci " +
                     "JOIN dbo.Product  p ON p.product_id  = ci.product_id " +
                     "JOIN dbo.Category c ON c.category_id = p.category_id " +
                     "WHERE ci.cart_id = ? ORDER BY ci.cart_item_id";
        List<CartItem> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CartItem i = new CartItem();
                    i.setCartItemId(rs.getInt("cart_item_id"));
                    i.setCartId(rs.getInt("cart_id"));
                    i.setProductId(rs.getInt("product_id"));
                    i.setQuantity(rs.getInt("quantity"));
                    i.setProductName(rs.getNString("product_name"));
                    i.setUnitPrice(JdbcSupport.getMoney(rs, "price"));
                    i.setImageUrl(rs.getString("image_url"));
                    i.setAvailable(rs.getBoolean("is_available"));
                    i.setProductStatus(rs.getString("product_status"));
                    i.setCategoryStatus(rs.getString("category_status"));
                    list.add(i);
                }
            }
        }
        return list;
    }

    /** Thêm món; nếu đã có trong giỏ thì cộng dồn số lượng thay vì tạo dòng thứ hai. */
    public void addItem(Connection con, int cartId, int productId, int quantity) throws SQLException {
        String sql = "UPDATE dbo.CartItem SET quantity = quantity + ? WHERE cart_id = ? AND product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, cartId);
            ps.setInt(3, productId);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO dbo.CartItem (cart_id, product_id, quantity) VALUES (?, ?, ?)")) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.executeUpdate();
        }
    }

    public void updateQuantity(Connection con, int cartId, int cartItemId, int quantity) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.CartItem SET quantity = ? WHERE cart_item_id = ? AND cart_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, cartItemId);
            ps.setInt(3, cartId);
            ps.executeUpdate();
        }
    }

    /** Điều kiện cart_id trong câu lệnh đảm bảo khách không xoá được dòng trong giỏ người khác. */
    public void removeItem(Connection con, int cartId, int cartItemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.CartItem WHERE cart_item_id = ? AND cart_id = ?")) {
            ps.setInt(1, cartItemId);
            ps.setInt(2, cartId);
            ps.executeUpdate();
        }
    }

    /** Dọn giỏ sau khi đặt hàng thành công. */
    public void clear(Connection con, int cartId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM dbo.CartItem WHERE cart_id = ?")) {
            ps.setInt(1, cartId);
            ps.executeUpdate();
        }
    }

    public void touch(Connection con, int cartId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Cart SET updated_at = ? WHERE cart_id = ?")) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, cartId);
            ps.executeUpdate();
        }
    }

    /**
     * Số lượng của một món đang có trong giỏ; chưa có thì 0.
     * <p>
     * Dùng để chặn số lượng <b>sau khi cộng dồn</b>: {@link #addItem} cộng vào dòng sẵn có, nên
     * kiểm mỗi lần thêm mà không nhìn con số đang có thì bấm "thêm vào giỏ" nhiều lần vẫn vượt
     * được giới hạn — xem {@code CartService.addProduct}.
     */
    public int quantityOf(Connection con, int cartId, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT quantity FROM dbo.CartItem WHERE cart_id = ? AND product_id = ?")) {
            ps.setInt(1, cartId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countItems(Connection con, int userId) throws SQLException {
        String sql = "SELECT ISNULL(SUM(ci.quantity), 0) FROM dbo.CartItem ci " +
                     "JOIN dbo.Cart c ON c.cart_id = ci.cart_id WHERE c.user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
