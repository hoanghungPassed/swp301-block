package com.fastfood.flow;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.admin.AdminService;
import com.fastfood.service.auth.AuthService;
import com.fastfood.service.auth.PasswordResetService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Đăng nhập, chống dò mật khẩu và quên mật khẩu")
class AuthFlowIT extends IntegrationTestBase {

    private static final String BASE_URL = "http://localhost:8080/fastfood";

    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private final AuthService authService = new AuthService();
    private final AdminService adminService = new AdminService();

    private final Inbox inbox = new Inbox();
    private final PasswordResetService resetService = new PasswordResetService(inbox);

    @Nested
    @DisplayName("Đăng nhập")
    class Login {

        @Test
        @DisplayName("Sai email và sai mật khẩu cho ra cùng một thông báo")
        void doesNotRevealWhichEmailsExist() {
            String unknown = message(() -> authService.login(
                    "khong-ton-tai-" + System.nanoTime() + "@gmail.com", "SaiHoanToan9", ip()));
            String wrongPassword = message(() -> authService.login(CUSTOMER_1, "SaiHoanToan9", ip()));

            assertEquals(unknown, wrongPassword,
                    "Hai thong bao khac nhau la du de do xem email nao co trong he thong");
        }

        @Test
        @DisplayName("Tài khoản bị khoá thì không vào được, dù mật khẩu đúng")
        void lockedAccountCannotLogIn() {
            Account acc = newAccount();
            adminService.setUserStatus(userId(ADMIN), acc.id(), "LOCKED");

            assertThrows(BusinessException.class,
                    () -> authService.login(acc.email(), acc.password(), ip()));
        }

        @Test
        @DisplayName("Đăng nhập được thì có dòng nhật ký LOGIN_SUCCESS")
        void successIsAudited() {
            Account acc = newAccount();

            authService.login(acc.email(), acc.password(), ip());

            assertEquals(1, auditCount(acc.id(), AuditAction.LOGIN_SUCCESS));
        }

        @Test
        @DisplayName("Sai mật khẩu thì có dòng nhật ký LOGIN_FAILED kèm email vừa gõ")
        void failureIsAudited() {
            Account acc = newAccount();

            assertThrows(ValidationException.class,
                    () -> authService.login(acc.email(), "SaiHoanToan9", ip()));

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_id = ? AND action = ? AND new_value = ?",
                    acc.id(), AuditAction.LOGIN_FAILED, acc.email()));
        }

        @Test
        @DisplayName("Email không tồn tại vẫn để lại dấu vết, dù không gắn được vào tài khoản nào")
        void unknownEmailStillLeavesATrace() {
            String unknown = "khong-ton-tai-" + System.nanoTime() + "@gmail.com";

            assertThrows(ValidationException.class,
                    () -> authService.login(unknown, "SaiHoanToan9", ip()));

            assertEquals(1, count(
                    "SELECT COUNT(*) FROM dbo.AuditLog WHERE action = ? AND new_value = ? AND actor_id IS NULL",
                    AuditAction.LOGIN_FAILED, unknown));
        }
    }

    @Nested
    @DisplayName("Chống dò mật khẩu")
    class Throttling {

        @Test
        @DisplayName("Sau năm lần sai thì cửa khoá lại, mật khẩu đúng cũng không vào được")
        void locksAfterRepeatedFailures() {
            Account acc = newAccount();
            String ip = ip();
            for (int i = 0; i < 5; i++) {
                assertThrows(ValidationException.class,
                        () -> authService.login(acc.email(), "SaiHoanToan9", ip));
            }

            BusinessException blocked = assertThrows(BusinessException.class,
                    () -> authService.login(acc.email(), acc.password(), ip));

            assertTrue(blocked.getMessage().contains("phút"),
                    "Thong bao phai noi ro con bao lau nua: " + blocked.getMessage());
            assertEquals(1, auditCount(acc.id(), AuditAction.LOGIN_BLOCKED),
                    "Moi dot chi ghi mot dong LOGIN_BLOCKED, khong ghi lai o moi lan thu");
        }

        @Test
        @DisplayName("Máy khác vẫn đăng nhập được sau khi một máy bị khoá")
        void lockIsPerMachine() {
            Account acc = newAccount();
            String attacker = ip();
            for (int i = 0; i < 5; i++) {
                assertThrows(ValidationException.class,
                        () -> authService.login(acc.email(), "SaiHoanToan9", attacker));
            }

            User owner = authService.login(acc.email(), acc.password(), ip());

            assertNotNull(owner, "Chu tai khoan ngoi may khac khong duoc bi khoa lay");
        }
    }

    @Nested
    @DisplayName("Quên mật khẩu")
    class ForgotPassword {

        @Test
        @DisplayName("Xin đặt lại thì sinh đúng một mã còn hiệu lực")
        void happyPath() {
            Account acc = newAccount();

            String token = requestAndCapture(acc);

            assertEquals(1, liveTokens(acc.id()));
            assertNotNull(resetService.findAccountFor(token));
        }

        @Test
        @DisplayName("Bảng chỉ giữ bản băm, không giữ mã mở được liên kết")
        void tableStoresOnlyTheHash() {
            Account acc = newAccount();

            String token = requestAndCapture(acc);

            String stored = text("SELECT TOP 1 token_hash FROM dbo.PasswordResetToken "
                    + "WHERE user_id = ? ORDER BY token_id DESC", acc.id());
            assertEquals(64, stored.length(), "SHA-256 dang chu so muoi sau luon dai 64 ky tu");
            assertNotEquals(token, stored, "Cot nay dang giu chinh ma goc");
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.PasswordResetToken WHERE token_hash = ?",
                    token), "Ma goc khong duoc xuat hien o bat ky dong nao");
        }

        @Test
        @DisplayName("Email không tồn tại: im lặng, không sinh mã, không nổ")
        void unknownEmailIsSilent() {
            int before = count("SELECT COUNT(*) FROM dbo.PasswordResetToken");

            resetService.request("khong-ton-tai-" + System.nanoTime() + "@gmail.com", BASE_URL, ip());

            assertEquals(before, count("SELECT COUNT(*) FROM dbo.PasswordResetToken"));
        }

        @Test
        @DisplayName("Tài khoản bị khoá thì không sinh mã")
        void lockedAccountGetsNoLink() {
            Account acc = newAccount();
            adminService.setUserStatus(userId(ADMIN), acc.id(), "LOCKED");

            resetService.request(acc.email(), BASE_URL, ip());

            assertEquals(0, liveTokens(acc.id()));
        }

        @Test
        @DisplayName("Quá hạn thì mã hết tác dụng, dù chưa ai dùng tới nó")
        void expiredTokenIsRejected() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);
            assertNotNull(resetService.findAccountFor(token), "Ma phai con dung duoc truoc khi het han");

            exec("UPDATE dbo.PasswordResetToken SET expires_at = DATEADD(MINUTE, -1, SYSDATETIME()) "
                    + "WHERE user_id = ?", acc.id());

            assertNull(resetService.findAccountFor(token));
            assertThrows(ValidationException.class,
                    () -> resetService.complete(token, "MatKhauMoi9", "MatKhauMoi9", ip()));
        }

        @Test
        @DisplayName("Tài khoản bị khoá sau khi đã cấp mã thì mã đó cũng không dùng được")
        void tokenDiesWithTheAccount() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);

            adminService.setUserStatus(userId(ADMIN), acc.id(), "LOCKED");

            assertNull(resetService.findAccountFor(token));
            assertThrows(ValidationException.class,
                    () -> resetService.complete(token, "MatKhauMoi9", "MatKhauMoi9", ip()));
        }

        @Test
        @DisplayName("Xin lần thứ hai thì mã lần đầu hết hiệu lực")
        void newRequestKillsThePreviousLink() {
            Account acc = newAccount();

            resetService.request(acc.email(), BASE_URL, ip());
            resetService.request(acc.email(), BASE_URL, ip());

            assertEquals(2, count(
                    "SELECT COUNT(*) FROM dbo.PasswordResetToken WHERE user_id = ?", acc.id()));
            assertEquals(1, liveTokens(acc.id()),
                    "Moi lan bam lai ma van de mot canh cua mo thi ho thu bi lo mot lan la lo tat ca");
        }

        @Test
        @DisplayName("Xin quá nhiều lần thì thôi, không dội thư vào hộp thư người khác")
        void requestsAreCapped() {
            Account acc = newAccount();

            for (int i = 0; i < 6; i++) {
                resetService.request(acc.email(), BASE_URL, ip());
            }

            assertEquals(3, count(
                    "SELECT COUNT(*) FROM dbo.PasswordResetToken WHERE user_id = ?", acc.id()));
        }

        @Test
        @DisplayName("Mã sai, mã rỗng và mã null đều không mở được gì")
        void badTokensAreRejected() {
            assertNull(resetService.findAccountFor("khong-phai-ma-that"));
            assertNull(resetService.findAccountFor(""));
            assertNull(resetService.findAccountFor(null));
        }

        @Test
        @DisplayName("Mật khẩu mới yếu thì bị từ chối, nhưng mã vẫn còn dùng được")
        void weakPasswordDoesNotBurnTheToken() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);

            assertThrows(ValidationException.class,
                    () -> resetService.complete(token, "abc", "abc", ip()));

            assertNotNull(resetService.findAccountFor(token),
                    "Tieu ma o lan go hong dau tien la bat nguoi dung di xin lien ket moi "
                            + "chi vi ho thieu mot chu so");
        }

        @Test
        @DisplayName("Đặt xong mật khẩu mới thì mã hết hiệu lực, và mật khẩu cũ không vào được nữa")
        void completingBurnsTheToken() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);

            resetService.complete(token, "MatKhauMoi9", "MatKhauMoi9", ip());

            assertNull(resetService.findAccountFor(token), "Ma phai la ma dung mot lan");
            assertThrows(ValidationException.class,
                    () -> authService.login(acc.email(), acc.password()),
                    "Mat khau cu phai het tac dung");
            assertNotNull(authService.login(acc.email(), "MatKhauMoi9"));
            assertEquals(1, auditCount(acc.id(), AuditAction.PASSWORD_RESET_DONE));
        }

        @Test
        @DisplayName("Đặt lại mật khẩu gỡ luôn cờ buộc đổi, không bắt đổi thêm lần nữa")
        void completingClearsTheMustChangeFlag() {
            Account acc = newAccount();
            adminService.resetPassword(userId(ADMIN), acc.id(), "TempPass1");
            String token = requestAndCapture(acc);

            resetService.complete(token, "MatKhauMoi9", "MatKhauMoi9", ip());

            assertFalse(mustChange(acc.id()),
                    "Nguoi dung vua tu dat mat khau cua chinh ho — dung viec ma co do dang cho");
        }

        @Test
        @DisplayName("Tự đổi mật khẩu ở trang tài khoản thì mọi liên kết còn treo hết hiệu lực")
        void changingPasswordInvalidatesOutstandingLinks() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);

            authService.changePassword(acc.id(), acc.password(), "MatKhauMoi9", "MatKhauMoi9");

            assertNull(resetService.findAccountFor(token));
        }

        @Test
        @DisplayName("Quản trị viên đặt lại mật khẩu cũng cắt luôn liên kết còn treo")
        void adminResetInvalidatesOutstandingLinks() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);

            adminService.resetPassword(userId(ADMIN), acc.id(), "TempPass1");

            assertNull(resetService.findAccountFor(token));
        }
    }

    private Account newAccount() {
        String email = "test-auth-" + System.nanoTime() + "@gmail.com";
        String password = "MatKhauGoc7";
        User created = authService.register("Khach Test", email, null, password, password);
        return new Account(created.getUserId(), email, password);
    }

    private String requestAndCapture(Account acc) {
        inbox.clear();
        resetService.request(acc.email(), BASE_URL, ip());
        String token = inbox.tokenInLink();
        assertNotNull(token, "Khong tim thay lien ket dat lai mat khau trong tin vua gui");
        return token;
    }

    private static int liveTokens(int userId) {
        return count("SELECT COUNT(*) FROM dbo.PasswordResetToken "
                + "WHERE user_id = ? AND used_at IS NULL AND expires_at > SYSDATETIME()", userId);
    }

    private static int auditCount(int userId, String action) {
        return count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_id = ? AND action = ?",
                userId, action);
    }

    private static boolean mustChange(int userId) {
        Boolean flag = scalar(Boolean.class,
                "SELECT must_change_password FROM dbo.Users WHERE user_id = ?", userId);
        return Boolean.TRUE.equals(flag);
    }

    private static String ip() {
        int n = IP_COUNTER.getAndIncrement();
        return "10.90." + (n / 250 % 250) + "." + (n % 250);
    }

    private static String message(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        throw new AssertionError("Dang le phai nem loi");
    }

    private record Account(int id, String email, String password) {
    }

    private static final class Inbox implements NotificationSender {

        private static final Pattern LINK = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]+)");

        private String lastContent;

        @Override
        public String getChannel() {
            return "TEST";
        }

        @Override
        public boolean send(String recipient, String subject, String content) {
            lastContent = content;
            return true;
        }

        void clear() {
            lastContent = null;
        }

        String tokenInLink() {
            if (lastContent == null) {
                return null;
            }
            Matcher m = LINK.matcher(lastContent);
            return m.find() ? m.group(1) : null;
        }
    }
}
