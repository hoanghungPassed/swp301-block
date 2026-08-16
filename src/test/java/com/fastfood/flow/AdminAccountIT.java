package com.fastfood.flow;

import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.User;
import com.fastfood.service.admin.AdminService;
import com.fastfood.service.auth.AuthService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quản trị tài khoản (BR-23) và các rào tự hạ quyền.
 * <p>
 * Mật khẩu do quản trị viên đặt hộ là mật khẩu <b>ít nhất hai người biết</b>, và thường còn
 * phải đọc qua điện thoại. Vì vậy tài khoản bị đánh dấu buộc đổi, và bộ lọc giữ nó ở màn hình
 * tài khoản cho tới khi chủ nhân tự đặt lại.
 */
@DisplayName("Quản trị tài khoản và mật khẩu đặt hộ")
class AdminAccountIT extends IntegrationTestBase {

    private final AdminService adminService = new AdminService();
    private final AuthService authService = new AuthService();

    // ------------------------------------------------------------------ mật khẩu đặt hộ

    @Test
    @DisplayName("Quản trị viên đặt lại mật khẩu thì tài khoản bị buộc đổi ở lần vào sau")
    void resetPasswordForcesChange() {
        int target = newStaff();

        adminService.resetPassword(userId(ADMIN), target, "TempPass1");

        assertTrue(mustChange(target),
                "Không bật cờ này thì tài khoản chạy tiếp bằng mật khẩu mà người khác biết");
    }

    @Test
    @DisplayName("Người dùng tự đặt mật khẩu mới thì cờ được gỡ")
    void changingPasswordClearsTheFlag() {
        int target = newStaff();
        adminService.resetPassword(userId(ADMIN), target, "TempPass1");

        authService.changePassword(target, "TempPass1", "MatKhauMoi9", "MatKhauMoi9");

        assertFalse(mustChange(target), "Làm đúng việc được yêu cầu rồi thì phải được đi tiếp");
    }

    @Test
    @DisplayName("Mật khẩu tạm dùng đăng nhập được, mật khẩu cũ thì không")
    void temporaryPasswordActuallyWorks() {
        int target = newStaff();
        String email = emailOf(target);
        adminService.resetPassword(userId(ADMIN), target, "TempPass1");

        User loggedIn = authService.login(email, "TempPass1");
        assertEquals(target, loggedIn.getUserId());
        assertTrue(loggedIn.isMustChangePassword(),
                "Đối tượng trả về lúc đăng nhập phải mang theo cờ, vì bộ lọc đọc từ phiên");

        assertThrows(ValidationException.class, () -> authService.login(email, "MatKhauGoc7"),
                "Mật khẩu cũ phải hết tác dụng ngay");
    }

    @Test
    @DisplayName("Đổi mật khẩu mà gõ sai mật khẩu hiện tại thì bị từ chối")
    void wrongCurrentPasswordIsRejected() {
        int target = newStaff();

        assertThrows(ValidationException.class,
                () -> authService.changePassword(target, "SaiHoanToan", "MatKhauMoi9", "MatKhauMoi9"));
    }

    @Test
    @DisplayName("Mật khẩu nhập lại không khớp thì bị từ chối")
    void confirmationMustMatch() {
        int target = newStaff();

        assertThrows(ValidationException.class,
                () -> authService.changePassword(target, "MatKhauGoc7", "MatKhauMoi9", "MatKhauKhac9"));
    }

    @Test
    @DisplayName("Quản trị viên không tự đặt lại mật khẩu của chính mình qua đường này")
    void adminCannotResetOwnPassword() {
        assertThrows(ValidationException.class,
                () -> adminService.resetPassword(userId(ADMIN), userId(ADMIN), "TempPass1"),
                "Tự đặt lại rồi tự bị chặn ở trang tài khoản là vòng luẩn quẩn vô nghĩa");
    }

    // ------------------------------------------------------------------ chống tự hạ quyền

    @Test
    @DisplayName("Quản trị viên không tự khoá tài khoản của chính mình")
    void adminCannotLockThemselves() {
        assertThrows(ValidationException.class,
                () -> adminService.setUserStatus(userId(ADMIN), userId(ADMIN), "LOCKED"),
                "Tự khoá là thao tác không tự quay lại được");
    }

    @Test
    @DisplayName("Quản trị viên không tự đổi vai trò của chính mình")
    void adminCannotChangeOwnRole() {
        Integer customerRole = scalar(Integer.class, "SELECT role_id FROM dbo.Role WHERE name = 'CUSTOMER'");

        assertThrows(ValidationException.class,
                () -> adminService.setUserRole(userId(ADMIN), userId(ADMIN), customerRole),
                "Hạ quyền chính mình xong là mất luôn đường vào khu quản trị để sửa lại");
    }

    @Test
    @DisplayName("Tài khoản bị khoá thì không đăng nhập được")
    void lockedAccountCannotLogIn() {
        int target = newStaff();
        String email = emailOf(target);

        adminService.setUserStatus(userId(ADMIN), target, "LOCKED");

        assertThrows(com.fastfood.common.exception.BusinessException.class,
                () -> authService.login(email, "MatKhauGoc7"));
    }

    @Test
    @DisplayName("Khoá tài khoản là đổi trạng thái, không phải xoá — lịch sử đơn phải còn")
    void lockingKeepsTheRow() {
        int target = newStaff();
        adminService.setUserStatus(userId(ADMIN), target, "LOCKED");

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Users WHERE user_id = ?", target),
                "Xoá tài khoản sẽ làm mồ côi mọi đơn mà người đó từng xử lý");
    }

    // ------------------------------------------------------------------ sửa thông tin

    @Test
    @DisplayName("Quản trị viên sửa được họ tên và số điện thoại của nhân viên")
    void adminCanFixStaffInfo() {
        int target = newStaff();

        adminService.updateUserInfo(userId(ADMIN), target, "Nguyen Van Sua", "0911222333");

        User after = adminService.findUser(target);
        assertEquals("Nguyen Van Sua", after.getFullName());
        assertEquals("0911222333", after.getPhone());
    }

    @Test
    @DisplayName("Sửa thông tin không đụng tới email đăng nhập")
    void editingInfoLeavesTheLoginIdentityAlone() {
        int target = newStaff();
        String emailBefore = emailOf(target);

        adminService.updateUserInfo(userId(ADMIN), target, "Ten Khac", "0900000001");

        assertEquals(emailBefore, emailOf(target),
                "Email là tên đăng nhập — đổi nó là đổi người vào được tài khoản");
    }

    @Test
    @DisplayName("Sửa thông tin không làm mất mật khẩu đang dùng")
    void editingInfoKeepsThePassword() {
        int target = newStaff();
        String email = emailOf(target);

        adminService.updateUserInfo(userId(ADMIN), target, "Ten Moi", "0900000002");

        User loggedIn = authService.login(email, "MatKhauGoc7");
        assertEquals(target, loggedIn.getUserId(),
                "Sửa một dòng thông tin mà phải cấp lại mật khẩu thì không ai dám dùng");
    }

    @Test
    @DisplayName("Bỏ trống họ tên thì bị từ chối")
    void blankNameIsRejected() {
        int target = newStaff();

        assertThrows(ValidationException.class,
                () -> adminService.updateUserInfo(userId(ADMIN), target, "   ", "0900000000"));
    }

    @Test
    @DisplayName("Sửa tài khoản không tồn tại thì báo không tìm thấy, không im lặng bỏ qua")
    void editingMissingAccountFails() {
        assertThrows(NotFoundException.class,
                () -> adminService.updateUserInfo(userId(ADMIN), 999_999, "Khong Co That", "0900000000"),
                "Báo thành công trong khi không ghi được gì là kiểu hỏng khó phát hiện nhất");
    }

    @Test
    @DisplayName("Sửa thông tin có ghi lại nhật ký kèm tên cũ")
    void editingIsAudited() {
        int target = newStaff();

        adminService.updateUserInfo(userId(ADMIN), target, "Ten Sau Khi Sua", "0900000003");

        assertEquals(1, count(
                "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'USER' AND entity_id = ? "
                        + "AND old_value = ? AND new_value = ?",
                target, "Nhan Vien Test", "Ten Sau Khi Sua"),
                "Nhật ký cần cả hai đầu để đối chứng, không chỉ giá trị mới");
    }

    // ------------------------------------------------------------------ tiện ích

    /** Một tài khoản nhân viên mới toanh cho mỗi bài, để các bài không giẫm lên nhau. */
    private int newStaff() {
        String email = "test-staff-" + System.nanoTime() + "@fastfood.vn";
        Integer cashierRole = scalar(Integer.class, "SELECT role_id FROM dbo.Role WHERE name = 'CASHIER'");

        adminService.createStaff(userId(ADMIN), "Nhan Vien Test", email, "0900000000",
                "MatKhauGoc7", cashierRole);

        return userId(email);
    }

    private static String emailOf(int userId) {
        return text("SELECT email FROM dbo.Users WHERE user_id = ?", userId);
    }

    private static boolean mustChange(int userId) {
        Boolean flag = scalar(Boolean.class,
                "SELECT must_change_password FROM dbo.Users WHERE user_id = ?", userId);
        return Boolean.TRUE.equals(flag);
    }
}
