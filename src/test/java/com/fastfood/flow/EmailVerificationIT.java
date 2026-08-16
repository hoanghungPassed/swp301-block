package com.fastfood.flow;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.AuthService;
import com.fastfood.service.auth.EmailVerificationService;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xác thực địa chỉ email của tài khoản khách tự đăng ký — chạy thật xuống cơ sở dữ liệu.
 * <p>
 * Ba nhóm ở đây trả lời ba câu khác nhau, và cả ba đều là loại lỗi <b>không lộ ra trên màn
 * hình</b>: hệ thống vẫn chạy đúng với người dùng bình thường trong khi rào chắn thì không có.
 * <ul>
 *   <li><b>Mã có thật sự dùng một lần và có hạn không</b> — cùng bộ câu hỏi đã đặt cho mã đặt
 *       lại mật khẩu ở {@code AuthFlowIT}, vì hai luồng có cùng hình dáng và cùng chỗ dễ sai.</li>
 *   <li><b>Bấm lại lá thư cũ có bị báo lỗi oan không</b> — trình duyệt và bộ quét liên kết của
 *       các dịch vụ thư đều hay tải trước đường dẫn trong thư, nên lượt bấm thật của người dùng
 *       rất thường là lượt thứ hai. Báo "liên kết hỏng" ở đúng lượt đó là dựng ra một lỗi không
 *       có thật, và người dùng sẽ đi xin thư mới cho một việc đã xong.</li>
 *   <li><b>Chốt chặn đặt hàng có nằm ở tầng Service không</b> — nút bị ẩn ngoài giao diện vẫn
 *       gửi được yêu cầu bằng tay, nên bài test gọi thẳng Service, không đi qua trang.</li>
 * </ul>
 * <p>
 * Mã gốc chỉ lấy được từ lá thư, đúng như người dùng thật: ở bảng chỉ có bản băm.
 */
@DisplayName("Xác thực địa chỉ email")
class EmailVerificationIT extends IntegrationTestBase {

    private static final String BASE_URL = "http://localhost:8080/fastfood";

    private final AuthService authService = new AuthService();
    private final CartService cartService = new CartService();
    private final CustomerOrderService customerOrders = new CustomerOrderService();

    /** Hộp thư giả: giữ lại tin cuối cùng để bài test đọc được liên kết trong đó. */
    private final Inbox inbox = new Inbox();
    private final EmailVerificationService verifyService = new EmailVerificationService(inbox);

    // ------------------------------------------------------------------ cấp mã và xác nhận

    @Nested
    @DisplayName("Bấm liên kết trong thư")
    class Confirm {

        @Test
        @DisplayName("Tài khoản mới đăng ký thì chưa được xác thực")
        void newAccountStartsUnverified() {
            Account acc = newAccount();

            assertFalse(verified(acc.id()),
                    "Nguoi dang ky moi chi GO ra mot dia chi, chua chung minh mo duoc hop thu do");
        }

        @Test
        @DisplayName("Bấm liên kết một lần là xác thực xong")
        void linkVerifiesTheAddress() {
            Account acc = newAccount();
            String token = sendAndCapture(acc);

            assertEquals(EmailVerificationService.Result.VERIFIED, verifyService.confirm(token, "10.1.1.1"));
            assertTrue(verified(acc.id()));
        }

        @Test
        @DisplayName("Bấm lại lần nữa báo \"đã xác thực rồi\", không phải liên kết hỏng")
        void secondClickIsNotAnError() {
            Account acc = newAccount();
            String token = sendAndCapture(acc);
            verifyService.confirm(token, "10.1.1.1");

            // Đây là lượt bấm mà trình duyệt hoặc bộ quét thư đã tiêu mất mã trước người dùng.
            assertEquals(EmailVerificationService.Result.ALREADY_VERIFIED,
                    verifyService.confirm(token, "10.1.1.1"),
                    "Bao lien ket hong o day la dung ra mot loi khong co that");
        }

        @Test
        @DisplayName("Mã bịa ra thì không xác thực được gì")
        void madeUpTokenIsRejected() {
            assertEquals(EmailVerificationService.Result.INVALID,
                    verifyService.confirm("khong-phai-ma-that", "10.1.1.1"));
        }

        @Test
        @DisplayName("Mã hết hạn thì không dùng được nữa")
        void expiredTokenIsRejected() {
            Account acc = newAccount();
            String token = sendAndCapture(acc);
            // Đẩy hạn về quá khứ thay vì chờ hai mươi tư giờ.
            exec("UPDATE dbo.EmailVerificationToken SET expires_at = ? WHERE user_id = ?",
                    LocalDateTime.now().minusMinutes(1), acc.id());

            assertEquals(EmailVerificationService.Result.INVALID, verifyService.confirm(token, "10.1.1.1"));
            assertFalse(verified(acc.id()));
        }

        @Test
        @DisplayName("Xác thực xong thì mã bị tiêu, không còn mã nào sống")
        void tokenIsConsumed() {
            Account acc = newAccount();
            String token = sendAndCapture(acc);

            verifyService.confirm(token, "10.1.1.1");

            assertEquals(0, liveTokens(acc.id()));
        }

        @Test
        @DisplayName("Cả hai mốc đều có dòng nhật ký: thư đã gửi, và địa chỉ đã xác thực")
        void bothStepsAreAudited() {
            Account acc = newAccount();
            String token = sendAndCapture(acc);
            verifyService.confirm(token, "10.1.1.1");

            assertEquals(1, auditCount(acc.id(), AuditAction.EMAIL_VERIFY_SENT));
            assertEquals(1, auditCount(acc.id(), AuditAction.EMAIL_VERIFIED));
        }
    }

    // ------------------------------------------------------------------ gửi lại thư

    @Nested
    @DisplayName("Gửi lại thư xác thực")
    class Resend {

        @Test
        @DisplayName("Thư mới cắt hiệu lực của thư cũ")
        void newLinkKillsTheOldOne() {
            Account acc = newAccount();
            String first = sendAndCapture(acc);
            String second = sendAndCapture(acc);

            assertNotEquals(first, second);
            assertEquals(EmailVerificationService.Result.INVALID, verifyService.confirm(first, "10.1.1.1"),
                    "Moi lan gui lai ma them mot lien ket con song la them mot canh cua");
            assertEquals(EmailVerificationService.Result.VERIFIED, verifyService.confirm(second, "10.1.1.1"));
        }

        @Test
        @DisplayName("Xin quá nhiều lần thì bị chặn")
        void tooManyRequestsAreBlocked() {
            Account acc = newAccount();
            // Trần mặc định là 5 lần trong 15 phút — xem security.verify.maxRequests.
            for (int i = 0; i < 5; i++) {
                verifyService.resend(load(acc.id()), BASE_URL, "10.1.1.1");
            }

            assertThrows(BusinessException.class,
                    () -> verifyService.resend(load(acc.id()), BASE_URL, "10.1.1.1"),
                    "Khong co tran thi nut nay thanh cong cu doi thu vao hop thu nguoi khac");
        }

        @Test
        @DisplayName("Đã xác thực rồi thì không gửi lại nữa")
        void verifiedAccountCannotAskAgain() {
            Account acc = newAccount();
            verifyService.confirm(sendAndCapture(acc), "10.1.1.1");

            assertThrows(BusinessException.class,
                    () -> verifyService.resend(load(acc.id()), BASE_URL, "10.1.1.1"));
        }

        @Test
        @DisplayName("Thư gửi đi có liên kết dùng được, không chỉ có mã nằm trong bảng")
        void mailCarriesAWorkingLink() {
            Account acc = newAccount();

            String token = sendAndCapture(acc);

            assertNotNull(token, "Khong tim thay lien ket xac thuc trong thu vua gui");
            assertEquals(EmailVerificationService.Result.VERIFIED, verifyService.confirm(token, "10.1.1.1"));
        }
    }

    // ------------------------------------------------------------------ chốt chặn đặt hàng

    @Nested
    @DisplayName("Chốt chặn đặt đơn online")
    class OrderGate {

        @Test
        @DisplayName("Chưa xác thực thì không đặt được đơn, dù giỏ hàng hợp lệ")
        void unverifiedCustomerCannotOrder() {
            Account acc = newAccount();
            cartService.addProduct(acc.id(), anyOrderableProductId(), 1);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> customerOrders.createOnlineOrder(acc.id(), safePickupTime(), key()));

            // Câu từ chối phải mang theo địa chỉ: khách gõ nhầm email lúc đăng ký thường nhận
            // ra ngay ở đây, và đây là lần đầu họ nhìn lại địa chỉ mình đã gõ.
            assertTrue(e.getMessage().contains(acc.email()),
                    "Loi chan phai noi ro dia chi nao dang cho xac thuc: " + e.getMessage());
        }

        @Test
        @DisplayName("Chặn ở tầng Service, không phải chỉ ẩn nút ngoài giao diện")
        void gateIsInTheServiceLayer() {
            Account acc = newAccount();
            cartService.addProduct(acc.id(), anyOrderableProductId(), 1);

            assertThrows(BusinessException.class,
                    () -> customerOrders.createOnlineOrder(acc.id(), safePickupTime(), key()));

            // Không có đơn nào lọt xuống, kể cả đơn dở dang: chốt chặn nằm trước mọi thao tác ghi.
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Orders WHERE customer_id = ?", acc.id()));
        }

        @Test
        @DisplayName("Xác thực xong thì đặt đơn được ngay")
        void verifiedCustomerCanOrder() {
            Account acc = newAccount();
            cartService.addProduct(acc.id(), anyOrderableProductId(), 1);
            verifyService.confirm(sendAndCapture(acc), "10.1.1.1");

            assertNotNull(customerOrders.createOnlineOrder(acc.id(), safePickupTime(), key()));
        }

        @Test
        @DisplayName("Giỏ hàng vẫn nguyên sau khi bị chặn")
        void cartSurvivesTheRejection() {
            Account acc = newAccount();
            cartService.addProduct(acc.id(), anyOrderableProductId(), 2);

            assertThrows(BusinessException.class,
                    () -> customerOrders.createOnlineOrder(acc.id(), safePickupTime(), key()));

            // Khách bị chặn vì một việc chưa làm xong, không phải vì họ chọn sai món. Mất giỏ ở
            // đây là bắt họ chọn lại từ đầu sau khi xác thực.
            assertEquals(2, count("SELECT ISNULL(SUM(ci.quantity), 0) FROM dbo.CartItem ci " +
                    "JOIN dbo.Cart c ON c.cart_id = ci.cart_id WHERE c.user_id = ?", acc.id()));
        }
    }

    // ------------------------------------------------------------------ tiện ích

    /** Một tài khoản khách mới toanh cho mỗi bài, để các bài không giẫm lên nhau. */
    private Account newAccount() {
        String email = "test-verify-" + System.nanoTime() + "@gmail.com";
        User created = authService.register("Khach Test", email, null, "MatKhauGoc7", "MatKhauGoc7");
        return new Account(created.getUserId(), email);
    }

    /**
     * Gửi một lá thư và lấy lại mã gốc từ chính lá thư đó.
     * <p>
     * Không đọc được từ cơ sở dữ liệu, và đó chính là điều đang cần: ở bảng chỉ có bản băm.
     * Đường duy nhất còn lại đúng bằng đường của người dùng thật — mở thư, lấy liên kết.
     */
    private String sendAndCapture(Account acc) {
        inbox.clear();
        verifyService.resend(load(acc.id()), BASE_URL, "10.1.1.1");
        String token = inbox.tokenInLink();
        assertNotNull(token, "Khong tim thay lien ket xac thuc trong thu vua gui");
        return token;
    }

    private User load(int userId) {
        return authService.findById(userId);
    }

    private static boolean verified(int userId) {
        return Boolean.TRUE.equals(scalar(Boolean.class,
                "SELECT email_verified FROM dbo.Users WHERE user_id = ?", userId));
    }

    /** Số mã còn dùng được của một tài khoản. */
    private static int liveTokens(int userId) {
        return count("SELECT COUNT(*) FROM dbo.EmailVerificationToken "
                + "WHERE user_id = ? AND used_at IS NULL AND expires_at > SYSDATETIME()", userId);
    }

    private static int auditCount(int userId, String action) {
        return count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_id = ? AND action = ?",
                userId, action);
    }

    private static LocalDateTime safePickupTime() {
        return LocalDateTime.now().toLocalDate().plusDays(1).atTime(12, 0);
    }

    private static String key() {
        return "idem-verify-" + System.nanoTime();
    }

    private record Account(int id, String email) {
    }

    /**
     * Hộp thư giả thay cho kênh gửi tin thật.
     * <p>
     * Giữ lại nội dung tin cuối cùng để bài test lấy được liên kết trong đó — đúng thao tác của
     * người dùng khi họ mở thư. Đây cũng là bài kiểm tra ngầm cho chính nội dung tin: liên kết
     * phải có thật trong đó và phải dùng được, chứ không chỉ có mã nằm đâu đó trong bảng.
     */
    private static final class Inbox implements NotificationSender {

        private static final Pattern LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

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
