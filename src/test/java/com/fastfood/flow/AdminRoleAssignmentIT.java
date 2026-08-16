package com.fastfood.flow;

import com.fastfood.common.constant.RoleName;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.filter.AuthenticationFilter;
import com.fastfood.filter.RoleAuthorizationFilter;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.User;
import com.fastfood.service.admin.AdminService;
import com.fastfood.service.auth.AuthService;
import com.fastfood.testsupport.FakeHttp;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vai trò gán cho tài khoản — phần phân quyền nằm ở tầng nghiệp vụ.
 * <p>
 * {@code RoleAuthorizationFilterTest} kiểm một vai trò <i>đi được tới đâu</i>. Bài này kiểm vế
 * còn lại: <b>vai trò đó được gán ra sao</b>. Cả hai đều là cửa vào khu vực đặc quyền, và cửa
 * thứ hai dễ bị bỏ quên hơn vì trông như một ô chọn bình thường trên biểu mẫu.
 * <p>
 * Điều cần chứng minh ở đây là màn hình quản trị <b>không phải</b> rào chắn: ô chọn vai trò trên
 * trang tạo tài khoản đã ẩn mục "Khách hàng", nhưng ẩn một mục trong thẻ {@code select} chỉ đổi
 * cái người dùng nhìn thấy — chuỗi gửi lên máy chủ thì ai cũng sửa được. Rào chắn thật phải nằm
 * ở tầng dịch vụ, đúng như ghi chú trên {@code RoleName}.
 */
@DisplayName("Gán vai trò cho tài khoản")
class AdminRoleAssignmentIT extends IntegrationTestBase {

    private final AdminService adminService = new AdminService();

    // ================================================================== tạo tài khoản

    @Nested
    @DisplayName("Tạo tài khoản nhân viên")
    class CreateStaff {

        @Test
        @DisplayName("Mã vai trò không có thật bị từ chối bằng thông báo đọc được")
        void unknownRoleIdIsRejected() {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> adminService.createStaff(userId(ADMIN), "Nhan Vien Ma La",
                            newEmail(), "0900000000", "MatKhauGoc7", 999_999),
                    "Để mã vai trò lạ đi thẳng xuống cơ sở dữ liệu thì người dùng nhận được "
                            + "thông báo lỗi chung chung, còn nguyên nhân thật nằm trong log máy chủ");
            assertTrue(e.getMessage().toLowerCase().contains("vai trò"),
                    "Thông báo phải nói đúng chỗ sai để người dùng sửa được: " + e.getMessage());
        }

        @Test
        @DisplayName("Mã vai trò bằng 0 — ô chọn để trống — cũng bị từ chối")
        void missingRoleIdIsRejected() {
            assertThrows(ValidationException.class,
                    () -> adminService.createStaff(userId(ADMIN), "Nhan Vien Thieu Vai Tro",
                            newEmail(), "0900000000", "MatKhauGoc7", 0));
        }

        @Test
        @DisplayName("Không tạo được tài khoản khách qua màn hình nhân viên, dù gửi thẳng mã vai trò")
        void customerRoleIsNotCreatableHere() {
            int customerRole = roleId("CUSTOMER");

            assertThrows(ValidationException.class,
                    () -> adminService.createStaff(userId(ADMIN), "Khach Gia Danh Nhan Vien",
                            newEmail(), "0900000000", "MatKhauGoc7", customerRole),
                    "Màn hình đã ẩn mục Khách hàng khỏi ô chọn, nhưng ẩn một mục trong thẻ select "
                            + "không ngăn được ai gửi thẳng mã vai trò đó lên");
        }

        @Test
        @DisplayName("Vai trò bị từ chối thì không có tài khoản nào được ghi lại")
        void rejectedRoleLeavesNoAccountBehind() {
            String email = newEmail();

            assertThrows(ValidationException.class,
                    () -> adminService.createStaff(userId(ADMIN), "Khong Duoc Tao",
                            email, "0900000000", "MatKhauGoc7", 999_999));

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Users WHERE email = ?", email),
                    "Một tài khoản nửa vời không đăng nhập được nhưng vẫn chiếm mất email");
        }

        @Test
        @DisplayName("Ba vai trò nhân viên vẫn tạo được bình thường")
        void staffRolesStillWork() {
            for (String role : new String[]{"CASHIER", "KITCHEN", "ADMIN"}) {
                String email = newEmail();
                adminService.createStaff(userId(ADMIN), "Nhan Vien " + role, email,
                        "0900000000", "MatKhauGoc7", roleId(role));

                assertEquals(role, adminService.findUser(userId(email)).getRoleName());
            }
        }
    }

    // ================================================================== đổi vai trò

    @Nested
    @DisplayName("Đổi vai trò của tài khoản")
    class ChangeRole {

        @Test
        @DisplayName("Đổi sang mã vai trò không có thật bị từ chối, và vai trò cũ giữ nguyên")
        void unknownRoleIdIsRejected() {
            int target = newStaff("CASHIER");

            assertThrows(ValidationException.class,
                    () -> adminService.setUserRole(userId(ADMIN), target, 999_999));

            assertEquals("CASHIER", adminService.findUser(target).getRoleName());
        }

        @Test
        @DisplayName("Hạ một nhân viên xuống vai trò khách vẫn được — đó là quyết định của quản trị viên")
        void demotingToCustomerIsAllowed() {
            int target = newStaff("CASHIER");

            adminService.setUserRole(userId(ADMIN), target, roleId("CUSTOMER"));

            assertEquals("CUSTOMER", adminService.findUser(target).getRoleName(),
                    "Khác với lúc tạo: ô chọn vai trò trên từng dòng cố ý liệt kê đủ bốn vai trò, "
                            + "vì nhân viên nghỉ việc có thể còn là khách của cửa hàng");
        }

        @Test
        @DisplayName("Đổi vai trò của tài khoản không tồn tại thì báo không tìm thấy")
        void missingAccountIsReported() {
            assertThrows(NotFoundException.class,
                    () -> adminService.setUserRole(userId(ADMIN), 999_999, roleId("CASHIER")));
        }

        @Test
        @DisplayName("Nâng một tài khoản lên quản trị có ghi nhật ký kèm vai trò cũ")
        void promotionIsAudited() {
            int target = newStaff("KITCHEN");

            adminService.setUserRole(userId(ADMIN), target, roleId("ADMIN"));

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'USER' AND entity_id = ? "
                            + "AND old_value = 'KITCHEN' AND new_value = 'ADMIN'", target),
                    "Ai được nâng lên quản trị, lúc nào, do ai — đó là dòng nhật ký được hỏi tới "
                            + "đầu tiên khi có chuyện, nên nó phải đọc được mà không cần tra "
                            + "bảng Role để dịch một con số");
        }
    }

    // ================================================================== trạng thái tài khoản

    @Nested
    @DisplayName("Trạng thái tài khoản")
    class Status {

        @Test
        @DisplayName("Trạng thái lạ bị từ chối ở tầng dịch vụ, không đợi cơ sở dữ liệu chặn")
        void unknownStatusIsRejected() {
            int target = newStaff("CASHIER");

            assertThrows(ValidationException.class,
                    () -> adminService.setUserStatus(userId(ADMIN), target, "DELETED"),
                    "Món và nhóm món đều kiểm trạng thái ở tầng dịch vụ; tài khoản không có lý do "
                            + "gì để dựa vào ràng buộc của cơ sở dữ liệu và trả về lỗi chung chung");

            assertEquals("ACTIVE", statusOf(target));
        }

        @Test
        @DisplayName("Khoá và mở khoá vẫn chạy bình thường")
        void lockAndUnlockStillWork() {
            int target = newStaff("CASHIER");

            adminService.setUserStatus(userId(ADMIN), target, "LOCKED");
            assertEquals("LOCKED", statusOf(target));

            adminService.setUserStatus(userId(ADMIN), target, "ACTIVE");
            assertEquals("ACTIVE", statusOf(target));
        }
    }

    // ================================================================== lọc theo vai trò

    /**
     * Vế còn lại của cùng một câu hỏi. Hai nhóm trên kiểm vai trò lúc <b>ghi</b>; nhóm này kiểm
     * vai trò lúc <b>đọc</b> — tên vai trò trên thanh địa chỉ cũng là chuỗi người dùng sửa được.
     * <p>
     * Ở đây một giá trị lạ không được phép thành lỗi: nó phải hiểu thành "không lọc". Trả về
     * bảng rỗng thì màn hình nói dối — trống vì lọc trượt trông y hệt trống vì chưa có tài khoản
     * nào, và quản trị viên sẽ đi tìm những tài khoản vẫn đang nằm nguyên đó.
     */
    @Nested
    @DisplayName("Lọc danh sách theo vai trò")
    class LocTheoVaiTro {

        @Test
        @DisplayName("Vai trò lạ trên địa chỉ hiểu thành không lọc, không phải bảng rỗng")
        void unknownRoleFiltersNothing() {
            long all = adminService.listUsers(null, null, null, 1).getTotalItems();

            assertEquals(all, adminService.listUsers("BANH_MI", null, null, 1).getTotalItems(),
                    "Bảng rỗng vì lọc trượt trông y hệt bảng rỗng vì chưa có tài khoản nào");
        }

        @Test
        @DisplayName("Vai trò có thật thì lọc đúng, không lẫn vai trò khác")
        void knownRoleFiltersExactly() {
            Page<User> page = adminService.listUsers("CASHIER", null, null, 1);

            assertTrue(page.getTotalItems() > 0, "Du lieu mau phai co thu ngan");
            assertTrue(page.getItems().stream().allMatch(u -> "CASHIER".equals(u.getRoleName())),
                    "Lọt một vai trò khác vào đây nghĩa là điều kiện lọc không tới được câu SQL");
        }

        @Test
        @DisplayName("Gõ thường hay gõ hoa đều ra cùng một danh sách")
        void roleFilterIsCaseInsensitive() {
            assertEquals(adminService.listUsers("CASHIER", null, null, 1).getTotalItems(),
                    adminService.listUsers("cashier", null, null, 1).getTotalItems(),
                    "Tên vai trò được chuẩn hoá trước khi vào câu lệnh, không phó mặc cho "
                            + "cách so chuỗi của cơ sở dữ liệu");
        }

        @Test
        @DisplayName("Bỏ trống bộ lọc thì thấy đủ bốn vai trò")
        void noFilterShowsEveryRole() {
            assertEquals(adminService.listUsers(null, null, null, 1).getTotalItems(),
                    adminService.listUsers("   ", null, null, 1).getTotalItems());
        }

        /**
         * Số đếm và danh sách phải đi qua cùng một bộ lọc. Lệch nhau thì thanh phân trang hứa
         * hẹn những trang không có gì trong đó.
         */
        @Test
        @DisplayName("Số tổng và danh sách cùng đếm trên một bộ lọc")
        void countAndItemsAgree() {
            Page<User> page = adminService.listUsers("BANH_MI", null, null, 1);

            assertEquals(Math.min(page.getTotalItems(), Page.SIZE), page.getItems().size());
        }
    }

    // ================================================================== bảng Role và enum RoleName

    /**
     * Vai trò tồn tại ở hai nơi: bốn hàng trong bảng {@code Role}, và bốn hằng số trong enum
     * {@link RoleName}. Bảng quyết định gán được cái gì; enum quyết định vào được đâu. Hai bên
     * lệch nhau thì không có gì báo lỗi — chỉ có những tài khoản đăng nhập được mà bị chặn ở
     * mọi khu vực, đúng nhánh "vai trò lạ → 403" mà {@code RoleAuthorizationFilterTest} đã kiểm.
     */
    @Nested
    @DisplayName("Bảng Role khớp với enum RoleName")
    class DuLieuVaiTro {

        @Test
        @DisplayName("Bảng chứa đúng bốn vai trò mà mã nguồn biết, không thiếu không thừa")
        void roleTableMatchesTheEnumExactly() {
            for (RoleName role : RoleName.values()) {
                assertEquals(1, count("SELECT COUNT(*) FROM dbo.Role WHERE name = ?", role.name()),
                        "Enum có " + role + " mà bảng thì không: mọi màn hình gán vai trò đều "
                                + "thiếu mất một lựa chọn, và không có gì lên tiếng");
            }
            assertEquals(RoleName.values().length, count("SELECT COUNT(*) FROM dbo.Role"),
                    "Bảng có vai trò mà mã nguồn không biết thì tài khoản mang vai trò đó đăng "
                            + "nhập được nhưng bị chặn ở khắp nơi");
        }

        /**
         * Bài trên canh <i>dữ liệu</i> trong bảng; bài này canh <i>cái khoá</i> giữ cho dữ liệu
         * đó không lệch được. Đây cũng là lý do {@code AdminService.requireRole} chỉ kiểm mã vai
         * trò có thật chứ không kiểm thêm tên: một tên ngoài bốn cái đã biết không vào nổi bảng.
         */
        @Test
        @DisplayName("Vai trò thứ năm không lọt được vào bảng, dù gửi thẳng câu lệnh")
        void aFifthRoleCannotEvenEnterTheTable() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Role (name, description) VALUES ('MANAGER', N'Vai tro la')");

            assertTrue(error.contains("CK_Role_name"),
                    "Không có khoá này thì thêm một dòng vào bảng Role là sinh ra những tài khoản "
                            + "đăng nhập được nhưng bị chặn ở mọi khu vực. Lỗi thật: " + error);
        }
    }

    // ================================================================== hiệu lực của vai trò mới

    @Nested
    @DisplayName("Vai trò mới có hiệu lực ngay trong phiên đang mở")
    class HieuLucNgay {

        private final AuthenticationFilter authFilter = new AuthenticationFilter();
        private final RoleAuthorizationFilter roleFilter = new RoleAuthorizationFilter();

        /**
         * Hai bài đã có ở nơi khác mỗi bài chứng minh một nửa: {@code AuthGuardIT} chứng minh
         * phiên nhận được vai trò mới, {@code RoleAuthorizationFilterTest} chứng minh vai trò
         * sai thì bị chặn. Bài này nối hai nửa lại — vì câu mà người dùng thật sự quan tâm
         * ("hạ quyền là chặn được ngay") chỉ đúng khi cả hai cùng chạy đúng thứ tự.
         */
        @Test
        @DisplayName("Hạ thu ngân xuống khách thì họ mất luôn đường vào màn hình quầy")
        void demotedCashierLosesTheCounterScreenImmediately() throws Exception {
            int target = newStaff("CASHIER");
            FakeHttp.Session session = FakeHttp.session()
                    .signedInAs(new AuthService().findById(target));

            adminService.setUserRole(userId(ADMIN), target, roleId("CUSTOMER"));

            // Bộ lọc đăng nhập chạy trước và đồng bộ lại bản chụp trong phiên; bộ lọc phân quyền
            // chạy sau và đọc chính bản chụp đó. Đúng thứ tự khai báo trong web.xml.
            authFilter.doFilter(FakeHttp.request("/staff/pos").in(session).method("POST").build(),
                    FakeHttp.response().build(), FakeHttp.chain().build());

            FakeHttp.Request req = FakeHttp.request("/staff/pos").in(session).method("POST");
            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();
            roleFilter.doFilter(req.build(), resp.build(), chain.build());

            assertFalse(chain.ran(),
                    "Phiên cũ còn giữ vai trò cũ thì hạ quyền chỉ có tác dụng sau khi người đó "
                            + "tự đăng xuất — đúng điều họ sẽ không làm");
            assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status());
        }
    }

    // ------------------------------------------------------------------ tiện ích

    private static String newEmail() {
        return "test-vaitro-" + System.nanoTime() + "@fastfood.vn";
    }

    private static int roleId(String name) {
        Integer id = scalar(Integer.class, "SELECT role_id FROM dbo.Role WHERE name = ?", name);
        if (id == null) {
            throw new IllegalStateException("Du lieu mau khong co vai tro " + name);
        }
        return id;
    }

    private static String statusOf(int userId) {
        return text("SELECT status FROM dbo.Users WHERE user_id = ?", userId);
    }

    private int newStaff(String role) {
        String email = newEmail();
        adminService.createStaff(userId(ADMIN), "Nhan Vien Kiem Thu", email,
                "0900000000", "MatKhauGoc7", roleId(role));
        return userId(email);
    }
}
