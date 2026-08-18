package com.fastfood.service.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
