package com.fastfood.flow;

import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Category;
import com.fastfood.model.entity.Product;
import com.fastfood.model.entity.User;
import com.fastfood.service.admin.AdminService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hai danh sách của khu quản trị: món ăn và tài khoản.
 * <p>
 * Trước đây cả hai lấy về <b>toàn bộ</b> bảng rồi đổ thẳng ra trang. Điều cần chứng minh ở đây
 * là ba thứ mà cách làm cũ không có:
 * <ul>
 *   <li>chỉ lấy về một trang, nhưng vẫn biết tổng số dòng để nói "đang xem 21–40 trong 214";</li>
 *   <li>bộ lọc và số đếm dùng chung một mệnh đề — lệch nhau thì số trang không khớp số dòng;</li>
 *   <li>giá trị lọc lạ, đến từ địa chỉ người dùng gõ tay, hiểu thành "không lọc" chứ không
 *       biến thành bảng rỗng — bảng rỗng trông y hệt như cửa hàng chưa có gì.</li>
 * </ul>
 */
@DisplayName("Danh sách quản trị: phân trang và bộ lọc")
class AdminListingIT extends IntegrationTestBase {

    private final AdminService adminService = new AdminService();

    // ================================================================== món ăn

    @Nested
    @DisplayName("Món ăn")
    class Products {

        @Test
        @DisplayName("Một trang chỉ lấy đúng số dòng của trang, nhưng tổng số vẫn là tổng thật")
        void pageHoldsOnePageButCountsEverything() {
            /* Đọc số đếm ở cả hai đầu rồi kẹp tổng số vào giữa. Bài này buộc phải nhìn toàn bảng
               — đó chính là thứ nó kiểm — nên không khoanh vùng được như các bài dưới; kẹp hai
               đầu là cách giữ nó đúng kể cả khi có tiến trình khác đang ghi vào database test. */
            long before = countProducts("1 = 1");
            Page<Product> first = adminService.listProducts(null, null, null, null, 1);
            long after = countProducts("1 = 1");

            assertTrue(first.getTotalItems() >= before && first.getTotalItems() <= after,
                    "Tổng số phải là số món thật trong bảng (" + before + "–" + after
                            + "), không phải số dòng vừa lấy về: " + first.getTotalItems());
            assertTrue(first.getItems().size() <= Page.SIZE,
                    "Lấy nhiều hơn một trang thì phân trang không có tác dụng gì");
            if (first.getTotalItems() > Page.SIZE) {
                assertEquals(Page.SIZE, first.getItems().size());
            }
        }

        @Test
        @DisplayName("Trang 2 không lặp lại món nào của trang 1")
        void secondPageDoesNotRepeatTheFirst() {
            Page<Product> first = adminService.listProducts(null, null, null, null, 1);
            if (first.getTotalItems() <= Page.SIZE) {
                // Dữ liệu mẫu chưa đủ hai trang thì bài này không có gì để nói.
                return;
            }
            Page<Product> second = adminService.listProducts(null, null, null, null, 2);

            Set<Integer> ids = new HashSet<>();
            first.getItems().forEach(p -> ids.add(p.getProductId()));
            for (Product p : second.getItems()) {
                assertTrue(ids.add(p.getProductId()),
                        "Món " + p.getProductId() + " hiện ở cả hai trang — thứ tự cắt trang không ổn định");
            }
        }

        @Test
        @DisplayName("Trang vượt quá số trang có thật thì rỗng, không quay vòng về đầu")
        void pageBeyondTheEndIsEmpty() {
            Page<Product> far = adminService.listProducts(null, null, null, null, 10_000);

            assertTrue(far.isEmptyPage(),
                    "Quay vòng về trang đầu thì người dùng tưởng mình vẫn đang lật tiếp");
        }

        @Test
        @DisplayName("Lọc theo ngừng bán chỉ trả về món đã ngừng bán, và đếm khớp")
        void retiredFilterMatchesItsCount() {
            String mine = "Loc Ngung Ban " + System.nanoTime();
            newNamed(mine + " con ban");
            adminService.setProductStatus(userId(ADMIN), newNamed(mine + " da ngung"), "INACTIVE");

            Page<Product> page = adminService.listProducts(null, mine, "INACTIVE", null, 1);

            assertEquals(1, page.getTotalItems(),
                    "Số đếm phải dùng đúng mệnh đề lọc của câu lấy dữ liệu");
            assertEquals(1, page.getItems().size());
            for (Product p : page.getItems()) {
                assertEquals("INACTIVE", p.getStatus());
            }
        }

        @Test
        @DisplayName("Lọc tạm hết hàng và lọc ngừng bán là hai câu hỏi khác nhau")
        void stockFilterIsNotTheTradingStatus() {
            int id = newProduct("Mon Con Ban Nhung Het Hang", true);
            adminService.toggleProductAvailability(userId(ADMIN), id, false);

            Page<Product> outOfStock = adminService.listProducts(null, null, "ACTIVE", Boolean.FALSE, 1);

            assertTrue(outOfStock.getItems().stream().anyMatch(p -> p.getProductId() == id),
                    "Món đang bán mà tạm hết hàng chính là câu hỏi người quản lý hỏi mỗi sáng");
            for (Product p : outOfStock.getItems()) {
                assertEquals("ACTIVE", p.getStatus());
                assertFalse(p.isAvailable());
            }
        }

        @Test
        @DisplayName("Không chọn tình trạng thì lấy cả món còn hàng lẫn món tạm hết")
        void noStockFilterKeepsBothSides() {
            // Từ khoá riêng của bài: mọi con số dưới đây chỉ đếm hai món do chính bài này tạo ra,
            // nên bài không lung lay theo dữ liệu mà bài khác để lại trong cùng database.
            String mine = "Loc Tinh Trang " + System.nanoTime();
            newNamed(mine + " con hang");
            adminService.toggleProductAvailability(userId(ADMIN), newNamed(mine + " het hang"), false);

            long all = adminService.listProducts(null, mine, null, null, 1).getTotalItems();
            long inStock = adminService.listProducts(null, mine, null, Boolean.TRUE, 1).getTotalItems();
            long outOfStock = adminService.listProducts(null, mine, null, Boolean.FALSE, 1).getTotalItems();

            assertEquals(2, all);
            assertEquals(all, inStock + outOfStock,
                    "Bỏ trống ô lọc mà lại ngầm hiểu là 'còn hàng' thì món tạm hết biến mất khỏi màn quản trị");
            assertEquals(1, outOfStock);
        }

        @Test
        @DisplayName("Trạng thái lạ trong địa chỉ hiểu thành không lọc, không thành bảng rỗng")
        void unknownStatusMeansNoFilter() {
            String mine = "Trang Thai La " + System.nanoTime();
            newNamed(mine + " mot");
            newNamed(mine + " hai");

            long all = adminService.listProducts(null, mine, null, null, 1).getTotalItems();
            long bogus = adminService.listProducts(null, mine, "DELETED", null, 1).getTotalItems();

            assertEquals(2, all);
            assertEquals(all, bogus,
                    "Một bảng rỗng không giải thích được trông y hệt như cửa hàng chưa có món nào");
        }

        @Test
        @DisplayName("Lọc theo từ khoá thì cả trang lẫn số đếm cùng thu hẹp")
        void keywordNarrowsBothListAndCount() {
            String unique = "Mon Tim Kiem " + System.nanoTime();
            newNamed(unique);

            Page<Product> page = adminService.listProducts(null, unique, null, null, 1);

            assertEquals(1, page.getTotalItems());
            assertEquals(1, page.getItems().size());
            assertEquals(unique, page.getItems().get(0).getName());
        }
    }

    // ================================================================== nhóm món

    @Nested
    @DisplayName("Nhóm món")
    class Categories {

        @Test
        @DisplayName("Danh sách nhóm cho màn hình món có cả nhóm đang ẩn")
        void hiddenCategoriesStayInTheList() {
            int catId = newCategory();
            adminService.setCategoryStatus(userId(ADMIN), catId, "INACTIVE");

            List<Category> all = adminService.listCategories();

            assertTrue(all.stream().anyMatch(c -> c.getCategoryId() == catId),
                    "Thiếu nhóm đang ẩn thì món thuộc nhóm đó không có mục nào được chọn sẵn trong "
                            + "ô chọn nhóm, và bấm Lưu là món lặng lẽ chuyển sang nhóm khác");
        }

        @Test
        @DisplayName("Sửa một món thuộc nhóm đã ẩn thì món vẫn ở nguyên nhóm cũ")
        void editingAProductInAHiddenCategoryKeepsItsCategory() {
            int catId = newCategory();
            int productId = newProductIn(catId, "Mon Trong Nhom Da An");
            adminService.setCategoryStatus(userId(ADMIN), catId, "INACTIVE");

            /* Đúng những gì trang gửi lên khi người dùng chỉ sửa tên: mọi ô của biểu mẫu,
               trong đó ô chọn nhóm mang giá trị đang được chọn sẵn. */
            Product form = adminService.findProduct(productId);
            form.setName("Doi Ten Thoi " + System.nanoTime());
            adminService.saveProduct(userId(ADMIN), form);

            assertEquals(catId, adminService.findProduct(productId).getCategoryId(),
                    "Món tự nhảy sang nhóm khác sau một lần sửa tên là kiểu hỏng không ai nhìn thấy");
        }
    }

    // ================================================================== tài khoản

    @Nested
    @DisplayName("Tài khoản")
    class Users {

        @Test
        @DisplayName("Một trang tài khoản cũng biết tổng số tài khoản thật")
        void pageKnowsTheRealTotal() {
            long before = count("SELECT COUNT(*) FROM dbo.Users");
            Page<User> page = adminService.listUsers(null, null, null, 1);
            long after = count("SELECT COUNT(*) FROM dbo.Users");

            assertTrue(page.getTotalItems() >= before && page.getTotalItems() <= after,
                    "Tổng số phải là số tài khoản thật (" + before + "–" + after + "), không phải "
                            + "số dòng vừa lấy về: " + page.getTotalItems());
            assertTrue(page.getItems().size() <= Page.SIZE);
        }

        @Test
        @DisplayName("Lọc theo đã khoá chỉ trả về tài khoản bị khoá")
        void lockedFilterReturnsOnlyLockedAccounts() {
            String mine = "Khoa " + System.nanoTime();
            adminService.setUserStatus(userId(ADMIN), newStaffAccount(mine + " bi khoa"), "LOCKED");
            newStaffAccount(mine + " van chay");

            Page<User> page = adminService.listUsers(null, mine, "LOCKED", 1);

            assertEquals(1, page.getTotalItems(),
                    "Số đếm phải dùng đúng mệnh đề lọc của câu lấy dữ liệu");
            for (User u : page.getItems()) {
                assertFalse(u.isActive());
            }
        }

        @Test
        @DisplayName("Lọc vai trò và lọc trạng thái chồng lên nhau chứ không thay thế nhau")
        void roleAndStatusFiltersCombine() {
            String mine = "Chong Loc " + System.nanoTime();
            adminService.setUserStatus(userId(ADMIN),
                    newStaffAccount(mine + " thu ngan bi khoa"), "LOCKED");

            assertEquals(1, adminService.listUsers("CASHIER", mine, "LOCKED", 1).getTotalItems());
            assertEquals(0, adminService.listUsers("KITCHEN", mine, "LOCKED", 1).getTotalItems(),
                    "Thêm bộ lọc vai trò mà số không đổi nghĩa là một trong hai ô lọc bị bỏ qua");
            assertEquals(0, adminService.listUsers("CASHIER", mine, "ACTIVE", 1).getTotalItems());
        }

        @Test
        @DisplayName("Trạng thái lạ trong địa chỉ hiểu thành không lọc")
        void unknownStatusMeansNoFilter() {
            String mine = "Trang Thai La " + System.nanoTime();
            newStaffAccount(mine + " mot");
            newStaffAccount(mine + " hai");

            long all = adminService.listUsers(null, mine, null, 1).getTotalItems();

            assertEquals(2, all);
            assertEquals(all, adminService.listUsers(null, mine, "BANNED", 1).getTotalItems());
        }

        @Test
        @DisplayName("Tìm theo email trả về đúng một tài khoản")
        void keywordFindsExactlyOneAccount() {
            Page<User> page = adminService.listUsers(null, ADMIN, null, 1);

            assertEquals(1, page.getTotalItems());
            assertEquals(ADMIN, page.getItems().get(0).getEmail());
        }
    }

    // ------------------------------------------------------------------ tiện ích

    private static long countProducts(String condition) {
        return count("SELECT COUNT(*) FROM dbo.Product p WHERE " + condition);
    }

    private int newProduct(String name, boolean active) {
        int id = newNamed(name + " " + System.nanoTime());
        if (!active) {
            adminService.setProductStatus(userId(ADMIN), id, "INACTIVE");
        }
        return id;
    }

    private int newNamed(String uniqueName) {
        return newProductIn(anyCategoryId(), uniqueName, true);
    }

    private int newProductIn(int categoryId, String name) {
        return newProductIn(categoryId, name + " " + System.nanoTime(), true);
    }

    private int newProductIn(int categoryId, String uniqueName, boolean available) {
        Product form = new Product();
        form.setName(uniqueName);
        form.setCategoryId(categoryId);
        form.setPrice(new BigDecimal("30000"));
        form.setDescription("Mon dung cho kiem thu danh sach");
        form.setAvailable(available);
        adminService.saveProduct(userId(ADMIN), form);

        Integer id = scalar(Integer.class, "SELECT product_id FROM dbo.Product WHERE name = ?", uniqueName);
        if (id == null) {
            throw new IllegalStateException("Khong tao duoc mon test: " + uniqueName);
        }
        return id;
    }

    private int newCategory() {
        String name = "Nhom Test Danh Sach " + System.nanoTime();
        Category form = new Category();
        form.setName(name);
        form.setDisplayOrder(99);
        adminService.saveCategory(userId(ADMIN), form);

        Integer id = scalar(Integer.class, "SELECT category_id FROM dbo.Category WHERE name = ?", name);
        if (id == null) {
            throw new IllegalStateException("Khong tao duoc nhom mon test: " + name);
        }
        return id;
    }

    /**
     * Một thu ngân mới mang đúng họ tên truyền vào — các bài lọc dùng chính họ tên đó làm từ
     * khoá, nên mỗi bài chỉ đếm những tài khoản do chính nó tạo ra.
     */
    private int newStaffAccount(String fullName) {
        String email = "thungan.test." + System.nanoTime() + "@fastfood.vn";
        int roleId = count("SELECT role_id FROM dbo.Role WHERE name = 'CASHIER'");
        adminService.createStaff(userId(ADMIN), fullName, email, null, "QuayThu7x2k", roleId);
        return userId(email);
    }

    private static int anyCategoryId() {
        Integer id = scalar(Integer.class, "SELECT TOP 1 category_id FROM dbo.Category ORDER BY category_id");
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co nhom mon nao");
        }
        return id;
    }
}
