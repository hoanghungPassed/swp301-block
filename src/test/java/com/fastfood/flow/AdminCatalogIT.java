package com.fastfood.flow;

import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.Category;
import com.fastfood.model.entity.Product;
import com.fastfood.service.admin.AdminService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đủ bốn thao tác trên món ăn và nhóm món — hai màn hình quản trị danh mục.
 * <p>
 * Điều cần chứng minh ở đây không chỉ là "bốn thao tác chạy được", mà là <b>Xoá đi một đường
 * riêng, tách khỏi Sửa</b>. Trước đây trạng thái kinh doanh nằm trong ô tick của form Sửa: mọi
 * lần lưu nội dung đều ghi đè trạng thái, nên một lần quên tick là món rời thực đơn mà không ai
 * chủ ý làm vậy — và nhật ký không phân biệt được lần đó với một lần sửa giá bình thường.
 */
@DisplayName("Quản trị món ăn và nhóm món")
class AdminCatalogIT extends IntegrationTestBase {

    private final AdminService adminService = new AdminService();

    // ================================================================== món ăn

    @Nested
    @DisplayName("Món ăn")
    class Products {

        @Test
        @DisplayName("Thêm món mới thì món có mặt trong danh sách quản trị và đang bán")
        void createPutsTheProductOnTheMenu() {
            String name = "Mon Test Them " + System.nanoTime();
            Product form = blankForm(name);
            form.setPrice(new BigDecimal("31000"));

            adminService.saveProduct(userId(ADMIN), form);

            Integer id = scalar(Integer.class, "SELECT product_id FROM dbo.Product WHERE name = ?", name);
            assertNotNull(id, "Thêm món xong mà không tìm lại được là không có gì được ghi");
            Product saved = adminService.findProduct(id);
            assertEquals(name, saved.getName());
            assertEquals(0, new BigDecimal("31000").compareTo(saved.getPrice()));
            assertEquals("ACTIVE", saved.getStatus(), "Thêm món là để bán, không phải để cất đi");
        }

        @Test
        @DisplayName("Đọc một món không tồn tại thì báo không tìm thấy")
        void readingAMissingProductFails() {
            assertThrows(NotFoundException.class, () -> adminService.findProduct(999_999));
        }

        @Test
        @DisplayName("Sửa món thì ghi lại nội dung mới")
        void updateRewritesTheProduct() {
            int id = newProduct("Mon Test Sua");

            Product form = adminService.findProduct(id);
            form.setName("Ten Da Sua");
            form.setPrice(new BigDecimal("45000"));
            adminService.saveProduct(userId(ADMIN), form);

            Product after = adminService.findProduct(id);
            assertEquals("Ten Da Sua", after.getName());
            assertEquals(0, new BigDecimal("45000").compareTo(after.getPrice()));
        }

        @Test
        @DisplayName("Sửa món KHÔNG được đụng tới trạng thái kinh doanh")
        void updateLeavesTheTradingStatusAlone() {
            int id = newProduct("Mon Test Giu Trang Thai");
            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            Product form = adminService.findProduct(id);
            form.setName("Sua Ten Thoi");
            form.setStatus("ACTIVE");   // form cố tình gửi trạng thái sai — tầng dịch vụ phải bỏ qua
            adminService.saveProduct(userId(ADMIN), form);

            assertEquals("INACTIVE", statusOfProduct(id),
                    "Sửa mô tả một món mà nó tự bán lại là kiểu hỏng không ai để ý cho tới khi khách đặt được");
        }

        @Test
        @DisplayName("Ngừng bán là xoá mềm: món biến khỏi thực đơn nhưng bản ghi còn nguyên")
        void retireIsASoftDelete() {
            int id = newProduct("Mon Test Ngung Ban");

            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            assertEquals("INACTIVE", statusOfProduct(id));
            assertNotNull(adminService.findProduct(id),
                    "Xoá thật sẽ làm hỏng mọi đơn cũ đang trỏ tới món này");
            assertEquals(0, count(
                    "SELECT COUNT(*) FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id "
                            + "WHERE p.product_id = ? AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'", id),
                    "Món đã ngừng bán thì không được lọt vào thực đơn nữa");
        }

        @Test
        @DisplayName("Bán lại được món đã ngừng bán")
        void retiredProductCanComeBack() {
            int id = newProduct("Mon Test Ban Lai");
            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            adminService.setProductStatus(userId(ADMIN), id, "ACTIVE");

            assertEquals("ACTIVE", statusOfProduct(id));
        }

        @Test
        @DisplayName("Ngừng bán ghi nhật ký bằng mã riêng, không lẫn với sửa nội dung")
        void retireHasItsOwnAuditCode() {
            int id = newProduct("Mon Test Nhat Ky");

            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'PRODUCT' AND entity_id = ? "
                            + "AND action = 'PRODUCT_RETIRED' AND old_value = 'ACTIVE' AND new_value = 'INACTIVE'",
                    id),
                    "Câu hỏi hay gặp nhất khi rà thực đơn là 'món này ai gỡ' — gộp mã thì phải lọc tay");
        }

        @Test
        @DisplayName("Bán lại ghi mã khác với ngừng bán")
        void restoreIsAuditedSeparately() {
            int id = newProduct("Mon Test Nhat Ky Ban Lai");
            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            adminService.setProductStatus(userId(ADMIN), id, "ACTIVE");

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'PRODUCT' AND entity_id = ? "
                            + "AND action = 'PRODUCT_RESTORED'", id));
        }

        @Test
        @DisplayName("Ngừng bán một món không tồn tại thì báo lỗi, không im lặng bỏ qua")
        void retiringAMissingProductFails() {
            assertThrows(NotFoundException.class,
                    () -> adminService.setProductStatus(userId(ADMIN), 999_999, "INACTIVE"),
                    "Báo thành công trong khi không ghi được gì là kiểu hỏng khó phát hiện nhất");
        }

        @Test
        @DisplayName("Trạng thái lạ bị từ chối thay vì ghi thẳng xuống cơ sở dữ liệu")
        void unknownStatusIsRejected() {
            int id = newProduct("Mon Test Trang Thai La");

            assertThrows(ValidationException.class,
                    () -> adminService.setProductStatus(userId(ADMIN), id, "DELETED"));
            assertEquals("ACTIVE", statusOfProduct(id));
        }

        @Test
        @DisplayName("Bỏ trống tên món thì bị từ chối")
        void blankNameIsRejected() {
            Product form = blankForm("   ");
            assertThrows(ValidationException.class,
                    () -> adminService.saveProduct(userId(ADMIN), form));
        }

        @Test
        @DisplayName("Giá âm thì bị từ chối")
        void negativePriceIsRejected() {
            Product form = blankForm("Mon Gia Am");
            form.setPrice(new BigDecimal("-1"));
            assertThrows(ValidationException.class,
                    () -> adminService.saveProduct(userId(ADMIN), form));
        }

        @Test
        @DisplayName("Ngừng bán không đụng tới cờ còn hàng — hai khái niệm khác nhau")
        void retireDoesNotTouchAvailability() {
            int id = newProduct("Mon Test Con Hang");

            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");

            assertTrue(isAvailable(id),
                    "Tạm hết hàng hôm nay và ngừng bán hẳn là hai việc khác nhau, không được gộp");
        }
    }

    // ================================================================== nhóm món

    @Nested
    @DisplayName("Nhóm món")
    class Categories {

        @Test
        @DisplayName("Thêm nhóm mới thì nhóm hiện trên thực đơn")
        void createPutsTheCategoryOnTheMenu() {
            String name = "Nhom Test Them " + System.nanoTime();
            Category form = new Category();
            form.setName(name);
            form.setDisplayOrder(12);

            adminService.saveCategory(userId(ADMIN), form);

            Integer id = scalar(Integer.class, "SELECT category_id FROM dbo.Category WHERE name = ?", name);
            assertNotNull(id, "Thêm nhóm xong mà không tìm lại được là không có gì được ghi");
            Category saved = adminService.findCategory(id);
            assertEquals(name, saved.getName());
            assertEquals(12, saved.getDisplayOrder());
            assertEquals("ACTIVE", saved.getStatus());
        }

        @Test
        @DisplayName("Đọc một nhóm không tồn tại thì báo không tìm thấy")
        void readingAMissingCategoryFails() {
            assertThrows(NotFoundException.class, () -> adminService.findCategory(999_999));
        }

        @Test
        @DisplayName("Sửa nhóm thì ghi lại tên và thứ tự mới")
        void updateRewritesTheCategory() {
            int id = newCategory("Nhom Test Sua");

            Category form = adminService.findCategory(id);
            form.setName("Nhom Da Sua");
            form.setDisplayOrder(7);
            adminService.saveCategory(userId(ADMIN), form);

            Category after = adminService.findCategory(id);
            assertEquals("Nhom Da Sua", after.getName());
            assertEquals(7, after.getDisplayOrder());
        }

        @Test
        @DisplayName("Sửa nhóm KHÔNG được đụng tới trạng thái hiển thị")
        void updateLeavesTheVisibilityAlone() {
            int id = newCategory("Nhom Test Giu Trang Thai");
            adminService.setCategoryStatus(userId(ADMIN), id, "INACTIVE");

            Category form = adminService.findCategory(id);
            form.setName("Sua Ten Thoi");
            form.setStatus("ACTIVE");   // form cố tình gửi trạng thái sai
            adminService.saveCategory(userId(ADMIN), form);

            assertEquals("INACTIVE", statusOfCategory(id),
                    "Sửa tên nhóm mà cả nhóm tự hiện lại thì không ai kiểm soát được thực đơn");
        }

        @Test
        @DisplayName("Sửa nhóm không tồn tại thì báo lỗi, không báo thành công suông")
        void editingAMissingCategoryFails() {
            Category form = new Category();
            form.setCategoryId(999_999);
            form.setName("Khong Co That");

            assertThrows(NotFoundException.class,
                    () -> adminService.saveCategory(userId(ADMIN), form));
        }

        @Test
        @DisplayName("Ẩn nhóm là xoá mềm: bản ghi còn nguyên, món trong nhóm vẫn trỏ tới được")
        void retireIsASoftDelete() {
            int catId = newCategory("Nhom Test An");
            int productId = newProductIn(catId, "Mon Trong Nhom An");

            adminService.setCategoryStatus(userId(ADMIN), catId, "INACTIVE");

            assertEquals("INACTIVE", statusOfCategory(catId));
            assertNotNull(adminService.findProduct(productId),
                    "Món vẫn trỏ tới nhóm bằng khoá ngoại — xoá thật sẽ làm hỏng khoá đó");
        }

        @Test
        @DisplayName("Ẩn nhóm thì món trong nhóm cũng rời thực đơn dù bản thân món vẫn đang bán")
        void retiringACategoryHidesItsProducts() {
            int catId = newCategory("Nhom Test An Keo Theo Mon");
            int productId = newProductIn(catId, "Mon Bi An Theo");

            adminService.setCategoryStatus(userId(ADMIN), catId, "INACTIVE");

            assertEquals("ACTIVE", statusOfProduct(productId), "Bản thân món không bị đổi trạng thái");
            assertEquals(0, count(
                    "SELECT COUNT(*) FROM dbo.Product p JOIN dbo.Category c ON c.category_id = p.category_id "
                            + "WHERE p.product_id = ? AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'", productId),
                    "Ẩn nhóm là cách ngừng bán cả dòng sản phẩm bằng một thao tác — phải kéo theo món");
        }

        @Test
        @DisplayName("Hiện lại được nhóm đã ẩn")
        void retiredCategoryCanComeBack() {
            int id = newCategory("Nhom Test Hien Lai");
            adminService.setCategoryStatus(userId(ADMIN), id, "INACTIVE");

            adminService.setCategoryStatus(userId(ADMIN), id, "ACTIVE");

            assertEquals("ACTIVE", statusOfCategory(id));
        }

        @Test
        @DisplayName("Ẩn nhóm ghi nhật ký bằng mã riêng kèm trạng thái cũ")
        void retireHasItsOwnAuditCode() {
            int id = newCategory("Nhom Test Nhat Ky");

            adminService.setCategoryStatus(userId(ADMIN), id, "INACTIVE");

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'CATEGORY' AND entity_id = ? "
                            + "AND action = 'CATEGORY_RETIRED' AND old_value = 'ACTIVE' AND new_value = 'INACTIVE'",
                    id));
        }

        @Test
        @DisplayName("Ẩn một nhóm không tồn tại thì báo lỗi")
        void retiringAMissingCategoryFails() {
            assertThrows(NotFoundException.class,
                    () -> adminService.setCategoryStatus(userId(ADMIN), 999_999, "INACTIVE"));
        }

        @Test
        @DisplayName("Trạng thái lạ bị từ chối")
        void unknownStatusIsRejected() {
            int id = newCategory("Nhom Test Trang Thai La");

            assertThrows(ValidationException.class,
                    () -> adminService.setCategoryStatus(userId(ADMIN), id, "HIDDEN"));
            assertEquals("ACTIVE", statusOfCategory(id));
        }

        @Test
        @DisplayName("Bỏ trống tên nhóm thì bị từ chối")
        void blankNameIsRejected() {
            Category form = new Category();
            form.setName("  ");

            assertThrows(ValidationException.class,
                    () -> adminService.saveCategory(userId(ADMIN), form));
        }
    }

    // ------------------------------------------------------------------ tiện ích

    /** Một nhóm món mới toanh cho mỗi bài, để các bài không giẫm lên nhau. */
    private int newCategory(String name) {
        String unique = name + " " + System.nanoTime();
        Category form = new Category();
        form.setName(unique);
        form.setDisplayOrder(99);
        adminService.saveCategory(userId(ADMIN), form);

        Integer id = scalar(Integer.class,
                "SELECT category_id FROM dbo.Category WHERE name = ?", unique);
        if (id == null) {
            throw new IllegalStateException("Khong tao duoc nhom mon test: " + unique);
        }
        return id;
    }

    private int newProduct(String name) {
        return newProductIn(anyCategoryId(), name);
    }

    private int newProductIn(int categoryId, String name) {
        String unique = name + " " + System.nanoTime();
        Product form = blankForm(unique);
        form.setCategoryId(categoryId);
        adminService.saveProduct(userId(ADMIN), form);

        Integer id = scalar(Integer.class,
                "SELECT product_id FROM dbo.Product WHERE name = ?", unique);
        if (id == null) {
            throw new IllegalStateException("Khong tao duoc mon test: " + unique);
        }
        return id;
    }

    private Product blankForm(String name) {
        Product form = new Product();
        form.setName(name);
        form.setCategoryId(anyCategoryId());
        form.setPrice(new BigDecimal("30000"));
        form.setDescription("Mon dung cho kiem thu");
        form.setAvailable(true);
        return form;
    }

    private static int anyCategoryId() {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 category_id FROM dbo.Category ORDER BY category_id");
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co nhom mon nao");
        }
        return id;
    }

    private static String statusOfProduct(int productId) {
        return text("SELECT status FROM dbo.Product WHERE product_id = ?", productId);
    }

    private static String statusOfCategory(int categoryId) {
        return text("SELECT status FROM dbo.Category WHERE category_id = ?", categoryId);
    }

    private static boolean isAvailable(int productId) {
        Boolean flag = scalar(Boolean.class,
                "SELECT is_available FROM dbo.Product WHERE product_id = ?", productId);
        return Boolean.TRUE.equals(flag);
    }
}
