package com.fastfood.dao.shared;

import com.fastfood.model.entity.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/** Truy vấn bảng Product. */
public class ProductDAO {

    private static final String COLS =
            "p.product_id, p.category_id, p.name, p.description, p.price, p.image_url, " +
            "p.is_available, p.status, p.created_at, p.updated_at, c.name AS category_name ";

    /**
     * Điểm đánh giá gộp sẵn theo món.
     * <p>
     * Gộp <b>một lần cho cả bảng</b> rồi mới nối vào, chứ không viết truy vấn con chạy lại cho
     * từng dòng thực đơn. Chỉ mục {@code IX_Review_product} đã mang sẵn cột {@code rating} nên
     * phép gộp này không phải mở tới bảng gốc.
     * <p>
     * {@code LEFT JOIN} chứ không {@code JOIN}: món chưa ai đánh giá vẫn phải có mặt trên thực
     * đơn. Đây đúng là chỗ mà một dấu sót sẽ làm biến mất phần lớn thực đơn của một cửa hàng mới
     * mở, nên có bài kiểm thử riêng canh nó.
     */
    private static final String RATING_JOIN =
            "LEFT JOIN (SELECT product_id, " +
            "                  AVG(CAST(rating AS DECIMAL(4,2))) AS rating_avg, " +
            "                  COUNT(*) AS rating_count " +
            "           FROM dbo.Review GROUP BY product_id) r ON r.product_id = p.product_id ";

    private static final String RATING_COLS = ", r.rating_avg, r.rating_count ";

    /**
     * Thứ tự sắp xếp thực đơn, tra theo mã do trang gửi lên.
     * <p>
     * <b>Bảng tra chứ không ghép chuỗi.</b> Mệnh đề {@code ORDER BY} không nhận tham số {@code ?},
     * nên cách duy nhất để đổi thứ tự là đưa chữ vào thẳng câu lệnh — và một tham số của trình
     * duyệt đi thẳng vào câu lệnh chính là lỗ tiêm SQL. Ở đây tham số chỉ dùng làm khoá tra, giá
     * trị lấy ra luôn là một trong bốn chuỗi viết cứng bên dưới; mã lạ rơi về {@code DEFAULT}.
     * <p>
     * Mọi thứ tự đều kết thúc bằng {@code p.product_id} để hai món cùng giá (hoặc cùng điểm)
     * không đổi chỗ cho nhau giữa hai lần tải trang.
     */
    private static final java.util.Map<String, String> MENU_ORDER = java.util.Map.of(
            "PRICE_ASC",  "p.price, p.product_id",
            "PRICE_DESC", "p.price DESC, p.product_id",
            /* Món chưa ai chấm có rating_avg là NULL. SQL Server xếp NULL lên đầu ở thứ tự giảm
               dần thì "điểm cao nhất" lại là một dãy món chưa có điểm — nên phải hạ chúng xuống
               cuối bằng một khoá phụ trước đã. */
            "RATING",     "CASE WHEN r.rating_avg IS NULL THEN 1 ELSE 0 END, r.rating_avg DESC, p.product_id",
            "DEFAULT",    "c.display_order, p.name, p.product_id");

    /** Mã sắp xếp hợp lệ, để tầng trên trả về đúng thứ mà trang đang thật sự dùng. */
    public static String menuSortOrDefault(String sort) {
        return sort != null && MENU_ORDER.containsKey(sort) ? sort : "DEFAULT";
    }

    /**
     * Thực đơn hiển thị cho khách, kèm điểm đánh giá trung bình của từng món.
     * <p>
     * Ba điều kiện lọc nằm ở hai bảng nên bắt buộc phải join: món còn kinh doanh, còn hàng,
     * và thuộc nhóm đang mở. Thiếu điều kiện cuối thì tắt cả nhóm món vẫn thấy món trên thực đơn.
     */
    public List<Product> findMenu(Connection con, Integer categoryId, String keyword, String sort)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLS + RATING_COLS +
                "FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id " +
                RATING_JOIN +
                "WHERE p.status = 'ACTIVE' AND p.is_available = 1 AND c.status = 'ACTIVE' ");
        List<Object> params = new ArrayList<>();
        if (categoryId != null && categoryId > 0) {
            sql.append("AND p.category_id = ? ");
            params.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND p.name LIKE ? ");
            params.add("%" + keyword.trim() + "%");
        }
        sql.append("ORDER BY ").append(MENU_ORDER.get(menuSortOrDefault(sort)));
        return query(con, sql.toString(), params, true);
    }

    /**
     * Một trang của danh sách quản trị, gồm cả món đã ngừng bán.
     * <p>
     * Cắt trang ngay trong câu lệnh chứ không lấy hết rồi cắt trong Java: bảng món của một
     * cửa hàng chạy vài năm sẽ có hàng nghìn dòng, và trang quản trị chỉ hiện được hai chục.
     */
    public List<Product> searchForAdmin(Connection con, Integer categoryId, String keyword,
                                        String status, Boolean available,
                                        int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLS +
                     "FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id " +
                     adminWhere(categoryId, keyword, status, available, params) +
                     "ORDER BY c.display_order, p.name, p.product_id " +
                     "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        params.add(offset);
        params.add(limit);
        return query(con, sql, params);
    }

    /** Tổng số món khớp bộ lọc, để biết có bao nhiêu trang. */
    public long countForAdmin(Connection con, Integer categoryId, String keyword,
                              String status, Boolean available) throws SQLException {
        List<Object> params = new ArrayList<>();
        // Vẫn phải nối sang Category: bộ lọc nhóm món dùng khoá ngoại nhưng thứ tự đếm không
        // cần cột nào của bảng kia — giữ nguyên mệnh đề lọc dùng chung là đủ và không lệch.
        String sql = "SELECT COUNT(*) FROM dbo.Product p " +
                     "JOIN dbo.Category c ON c.category_id = p.category_id " +
                     adminWhere(categoryId, keyword, status, available, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Mệnh đề lọc dùng chung cho cả câu lấy dữ liệu lẫn câu đếm. Viết hai lần thì sớm muộn
     * hai bên cũng lệch nhau, và khi đó số trang không khớp với số dòng thật sự lấy được.
     */
    private String adminWhere(Integer categoryId, String keyword, String status, Boolean available,
                              List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        if (categoryId != null && categoryId > 0) {
            sql.append("AND p.category_id = ? ");
            params.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND p.name LIKE ? ");
            params.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND p.status = ? ");
            params.add(status);
        }
        if (available != null) {
            sql.append("AND p.is_available = ? ");
            params.add(available);
        }
        return sql.toString();
    }

    public Product findById(Connection con, int productId) throws SQLException {
        String sql = "SELECT " + COLS +
                     "FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id " +
                     "WHERE p.product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Đọc lại món ngay trước khi tạo đơn, kèm trạng thái của nhóm món.
     * Cần thiết vì giữa lúc khách bỏ vào giỏ và lúc bấm thanh toán, quản trị viên
     * có thể đã đổi giá hoặc đánh dấu hết hàng.
     */
    public Product findForCheckout(Connection con, int productId) throws SQLException {
        String sql = "SELECT " + COLS + ", c.status AS category_status " +
                     "FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id " +
                     "WHERE p.product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Product p = map(rs);
                // Nhóm món đã tắt thì coi như món không đặt được nữa
                if (!"ACTIVE".equals(rs.getString("category_status"))) {
                    p.setStatus("INACTIVE");
                }
                return p;
            }
        }
    }

    public int insert(Connection con, Product p) throws SQLException {
        String sql = "INSERT INTO dbo.Product (category_id, name, description, price, image_url, " +
                     "is_available, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getCategoryId());
            ps.setNString(2, p.getName());
            JdbcSupport.setString(ps, 3, p.getDescription());
            ps.setBigDecimal(4, p.getPrice());
            JdbcSupport.setString(ps, 5, p.getImageUrl());
            ps.setBoolean(6, p.isAvailable());
            ps.setString(7, p.getStatus());
            JdbcSupport.setDateTime(ps, 8, p.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setProductId(keys.getInt(1));
                }
            }
        }
        return p.getProductId();
    }

    public void update(Connection con, Product p) throws SQLException {
        String sql = "UPDATE dbo.Product SET category_id = ?, name = ?, description = ?, price = ?, " +
                     "image_url = ?, is_available = ?, status = ?, updated_at = ? WHERE product_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, p.getCategoryId());
            ps.setNString(2, p.getName());
            JdbcSupport.setString(ps, 3, p.getDescription());
            ps.setBigDecimal(4, p.getPrice());
            JdbcSupport.setString(ps, 5, p.getImageUrl());
            ps.setBoolean(6, p.isAvailable());
            ps.setString(7, p.getStatus());
            JdbcSupport.setDateTime(ps, 8, p.getUpdatedAt());
            ps.setInt(9, p.getProductId());
            ps.executeUpdate();
        }
    }

    /**
     * Đổi riêng trạng thái kinh doanh — đây là thao tác Xoá của màn hình quản trị món.
     * <p>
     * Tách khỏi {@link #update} vì hai việc khác hẳn nhau: sửa món là ghi lại nội dung, còn
     * ngừng bán là gỡ món khỏi thực đơn. Đi chung một câu lệnh thì mọi lần lưu nội dung đều
     * ghi đè trạng thái, và một lần quên tick ô là món biến mất khỏi thực đơn mà không ai
     * chủ ý làm vậy.
     */
    public void updateStatus(Connection con, int productId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Product SET status = ?, updated_at = SYSDATETIME() WHERE product_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    /** Bật/tắt nhanh trạng thái còn hàng ngay trên danh sách. */
    public void toggleAvailability(Connection con, int productId, boolean available) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Product SET is_available = ? WHERE product_id = ?")) {
            ps.setBoolean(1, available);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private List<Product> query(Connection con, String sql, List<Object> params) throws SQLException {
        return query(con, sql, params, false);
    }

    /**
     * @param coDiemDanhGia truy vấn có kèm hai cột điểm hay không. Truyền cờ thay vì dò tên cột
     *                      trong {@link ResultSet}: chỉ nơi viết câu lệnh mới biết chắc, còn dò
     *                      lúc chạy thì một lần đổi câu lệnh là lặng lẽ mất cột điểm.
     */
    private List<Product> query(Connection con, String sql, List<Object> params,
                                boolean coDiemDanhGia) throws SQLException {
        List<Product> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product p = map(rs);
                    if (coDiemDanhGia) {
                        // Món chưa ai đánh giá cho ra NULL sau LEFT JOIN, không phải 0 — để trống
                        // thì màn hình biết đường im lặng thay vì trưng ra "0 sao".
                        p.setRatingAverage(rs.getBigDecimal("rating_avg"));
                        p.setRatingCount(rs.getInt("rating_count"));
                    }
                    list.add(p);
                }
            }
        }
        return list;
    }

    private Product map(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setName(rs.getNString("name"));
        p.setDescription(rs.getNString("description"));
        p.setPrice(JdbcSupport.getMoney(rs, "price"));
        p.setImageUrl(rs.getString("image_url"));
        p.setAvailable(rs.getBoolean("is_available"));
        p.setStatus(rs.getString("status"));
        p.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
        p.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
        p.setCategoryName(rs.getNString("category_name"));
        return p;
    }
}
