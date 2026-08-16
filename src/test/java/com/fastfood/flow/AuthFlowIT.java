package com.fastfood.flow;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.model.entity.User;
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

/**
 * Đăng nhập, chống dò mật khẩu, và luồng quên mật khẩu — chạy thật xuống cơ sở dữ liệu.
 * <p>
 * Bài này bổ sung cho {@code AdminAccountIT}: ở đó là mật khẩu do quản trị viên đặt hộ, ở đây là
 * đường người dùng tự đi. Ba chỗ được soi kỹ, vì cả ba đều là loại lỗi <b>không lộ ra trên màn
 * hình</b> — hệ thống vẫn chạy đúng với người dùng bình thường trong khi rào chắn thì không có:
 * <ul>
 *   <li>Cửa có thật sự khoá lại sau nhiều lần thử sai, và khoá đúng cặp email + máy.</li>
 *   <li>Mã đặt lại mật khẩu có thật sự chỉ dùng được một lần, và có hết hiệu lực khi mật khẩu
 *       đổi bằng đường khác.</li>
 *   <li>Trang quên mật khẩu có im lặng như nhau với email có thật và email không tồn tại.</li>
 * </ul>
 */
@DisplayName("Đăng nhập, chống dò mật khẩu và quên mật khẩu")
class AuthFlowIT extends IntegrationTestBase {

    private static final String BASE_URL = "http://localhost:8080/fastfood";

    /** Cấp địa chỉ máy cho từng lần gọi {@link #ip()} — xem ghi chú ở đó. */
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    private final AuthService authService = new AuthService();
    private final AdminService adminService = new AdminService();

    /** Hộp thư giả: giữ lại tin cuối cùng để bài test đọc được liên kết trong đó. */
    private final Inbox inbox = new Inbox();
    private final PasswordResetService resetService = new PasswordResetService(inbox);

    // ------------------------------------------------------------------ đăng nhập

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

        /**
         * Nhật ký của một lần thất bại phải sống sót. Ghi nó trong cùng giao dịch với thao tác
         * bị từ chối là tự xoá bằng chứng — giao dịch bị huỷ, dòng nhật ký huỷ theo, và chuyện
         * đáng ghi nhất lại là chuyện duy nhất không để lại dấu vết.
         */
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

    // ------------------------------------------------------------------ chống dò

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

        /**
         * Khoá theo mình email thì bất kỳ ai cũng vô hiệu hoá được tài khoản người khác bằng
         * cách gõ sai năm lần — cơ chế bảo vệ trở thành cơ chế phá hoại.
         */
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

    // ------------------------------------------------------------------ quên mật khẩu

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

        /**
         * Mã nằm trong liên kết gửi cho người dùng. Lưu nguyên văn xuống bảng nghĩa là bất kỳ ai
         * đọc được bảng — bản sao lưu, ảnh chụp màn hình lúc trình bày — đều chiếm được tài khoản
         * trong thời gian mã còn hạn.
         */
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

        /**
         * Hạn 15 phút là thứ thu hẹp khoảng thời gian một liên kết bị lộ còn giá trị — hộp thư
         * mở trên máy dùng chung, ảnh chụp màn hình, thư chuyển tiếp nhầm. Không có bài test thì
         * cột {@code expires_at} chỉ là một cột dữ liệu đẹp mà chưa chắc có ai đọc tới.
         */
        @Test
        @DisplayName("Quá hạn thì mã hết tác dụng, dù chưa ai dùng tới nó")
        void expiredTokenIsRejected() {
            Account acc = newAccount();
            String token = requestAndCapture(acc);
            assertNotNull(resetService.findAccountFor(token), "Ma phai con dung duoc truoc khi het han");

            // Đẩy hạn về quá khứ thay vì chờ 15 phút thật
            exec("UPDATE dbo.PasswordResetToken SET expires_at = DATEADD(MINUTE, -1, SYSDATETIME()) "
                    + "WHERE user_id = ?", acc.id());

            assertNull(resetService.findAccountFor(token));
            assertThrows(ValidationException.class,
                    () -> resetService.complete(token, "MatKhauMoi9", "MatKhauMoi9", ip()));
        }

        /**
         * Xin liên kết xong rồi mới bị khoá tài khoản. Không kiểm lại lúc dùng thì một liên kết
         * xin ra trước thời điểm khoá vẫn mở được cửa sau đó — đúng cánh cửa mà việc khoá tài
         * khoản sinh ra để đóng.
         */
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

        /**
         * Đổi mật khẩu là lúc người dùng muốn đóng mọi đường vào cũ. Một liên kết xin từ trước
         * mà vẫn dùng được sau đó thì đúng cánh cửa họ vừa khoá lại là cánh cửa còn mở.
         */
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

    // ------------------------------------------------------------------ tiện ích

    /** Một tài khoản khách mới toanh cho mỗi bài, để các bài không giẫm lên nhau. */
    private Account newAccount() {
        String email = "test-auth-" + System.nanoTime() + "@gmail.com";
        String password = "MatKhauGoc7";
        User created = authService.register("Khach Test", email, null, password, password);
        return new Account(created.getUserId(), email, password);
    }

    /**
     * Xin một mã và lấy lại mã gốc từ tin vừa gửi.
     * <p>
     * Không đọc được từ cơ sở dữ liệu, và đó chính là điều đang cần: ở bảng chỉ có bản băm.
     * Đường duy nhất còn lại đúng bằng đường của người dùng thật — mở tin, lấy liên kết.
     */
    private String requestAndCapture(Account acc) {
        inbox.clear();
        resetService.request(acc.email(), BASE_URL, ip());
        String token = inbox.tokenInLink();
        assertNotNull(token, "Khong tim thay lien ket dat lai mat khau trong tin vua gui");
        return token;
    }

    /** Số mã còn dùng được của một tài khoản. */
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

    /**
     * Mỗi lần gọi một địa chỉ riêng, để bộ đếm số lần thử sai của bài này không dính sang bài khác.
     * <p>
     * Đếm tăng dần chứ không lấy theo đồng hồ. Bản cũ dùng {@code System.nanoTime() % 250} cho cả
     * hai nhóm số, mà hai lần đọc đồng hồ cách nhau vài nano thì gần như luôn cho cùng phần dư —
     * nên xác suất hai địa chỉ trùng nhau không phải 1/62500 như hình thức của nó gợi ra, mà là
     * khoảng 1/250. Trùng một lần là {@code lockIsPerMachine} đỏ: "máy khác" hoá ra lại chính là
     * máy vừa bị khoá. Một bài test chập chờn còn tệ hơn không có bài nào, vì lần đỏ tiếp theo
     * sẽ bị cho qua như một lần chập chờn nữa.
     */
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

    /**
     * Hộp thư giả thay cho kênh gửi tin thật.
     * <p>
     * Giữ lại nội dung tin cuối cùng để bài test lấy được liên kết trong đó — đúng thao tác của
     * người dùng khi họ mở thư. Đây cũng là bài kiểm tra ngầm cho chính nội dung tin: liên kết
     * phải có thật trong đó và phải dùng được, chứ không chỉ có mã nằm đâu đó trong bảng.
     */
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

        /** Mã nằm trong liên kết của tin cuối cùng, hoặc null nếu không có tin nào. */
        String tokenInLink() {
            if (lastContent == null) {
                return null;
            }
            Matcher m = LINK.matcher(lastContent);
            return m.find() ? m.group(1) : null;
        }
    }
}
