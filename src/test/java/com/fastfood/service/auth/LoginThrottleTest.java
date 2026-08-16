package com.fastfood.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đếm số lần đăng nhập sai.
 * <p>
 * Bài test nằm cùng gói với lớp được kiểm để dùng được hàm dựng và {@code clear()} ở mức gói.
 * Cả hai cố tình không công khai: {@link LoginThrottle} phải là <b>một</b> bản dùng chung cho
 * cả ứng dụng, và một hàm dựng công khai chính là lời mời tạo ra bản thứ hai — khi đó mỗi bản
 * chỉ thấy một nửa số lần thử, ngưỡng năm lần trên thực tế thành mười, mà không có gì báo lỗi.
 */
@DisplayName("Chống dò mật khẩu")
class LoginThrottleTest {

    private static final String EMAIL = "customer1@gmail.com";
    private static final String IP = "192.168.1.50";

    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottle();
    }

    @Test
    @DisplayName("Chưa thử lần nào thì cửa mở")
    void openByDefault() {
        assertFalse(throttle.isLocked(EMAIL, IP));
        assertNull(throttle.lockRemaining(EMAIL, IP));
    }

    @Test
    @DisplayName("Bốn lần sai vẫn còn thử được, lần thứ năm thì khoá")
    void locksOnTheFifthFailure() {
        for (int i = 1; i <= 4; i++) {
            assertFalse(throttle.recordFailure(EMAIL, IP), "Lan " + i + " chua duoc khoa");
            assertFalse(throttle.isLocked(EMAIL, IP), "Lan " + i + " chua duoc khoa");
        }
        assertTrue(throttle.recordFailure(EMAIL, IP), "Lan thu nam phai khoa cua lai");
        assertTrue(throttle.isLocked(EMAIL, IP));
        assertNotNull(throttle.lockRemaining(EMAIL, IP));
    }

    /**
     * Giá trị trả về của {@code recordFailure} là tín hiệu để ghi nhật ký đúng một lần cho mỗi
     * đợt. Trả true ở mọi lần thử tiếp theo thì bảng nhật ký đầy những dòng LOGIN_BLOCKED giống
     * hệt nhau, và đợt tấn công thứ hai chìm mất giữa chúng.
     */
    @Test
    @DisplayName("Chỉ báo \"vừa bị khoá\" đúng một lần cho mỗi đợt")
    void reportsTheLockOnlyOnce() {
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(EMAIL, IP);
        }
        assertTrue(throttle.recordFailure(EMAIL, IP));
        assertFalse(throttle.recordFailure(EMAIL, IP), "Lan thu sau khong duoc bao la vua bi khoa");
        assertFalse(throttle.recordFailure(EMAIL, IP));
    }

    /**
     * Khoá theo mình email thì bất kỳ ai cũng vô hiệu hoá được tài khoản người khác bằng cách
     * gõ sai năm lần — biến một cơ chế bảo vệ thành một cơ chế phá hoại.
     */
    @Test
    @DisplayName("Khoá theo cặp email và máy: máy khác vẫn đăng nhập được")
    void locksPerMachineNotPerAccount() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure(EMAIL, "10.0.0.1");
        }
        assertTrue(throttle.isLocked(EMAIL, "10.0.0.1"));
        assertFalse(throttle.isLocked(EMAIL, "10.0.0.2"),
                "Chu tai khoan ngoi may khac khong duoc bi khoa lay");
    }

    @Test
    @DisplayName("Cùng một máy dò email khác thì đếm riêng")
    void countsPerEmail() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("a@example.com", IP);
        }
        assertTrue(throttle.isLocked("a@example.com", IP));
        assertFalse(throttle.isLocked("b@example.com", IP));
    }

    @Test
    @DisplayName("Đăng nhập được thì bộ đếm về không")
    void successClearsTheCounter() {
        for (int i = 0; i < 4; i++) {
            throttle.recordFailure(EMAIL, IP);
        }
        throttle.recordSuccess(EMAIL, IP);
        // Bốn lần sai trước đó phải biến mất hẳn, nếu không thì một lần gõ nhầm nữa là khoá.
        assertFalse(throttle.recordFailure(EMAIL, IP));
        assertFalse(throttle.isLocked(EMAIL, IP));
    }

    @Test
    @DisplayName("Email khác hoa thường vẫn là cùng một tài khoản")
    void emailIsCaseInsensitive() {
        for (int i = 0; i < 5; i++) {
            throttle.recordFailure("Customer1@Gmail.com", IP);
        }
        assertTrue(throttle.isLocked("customer1@gmail.com", IP),
                "Doi hoa thuong khong duoc dung de vuot qua bo dem");
    }
}
