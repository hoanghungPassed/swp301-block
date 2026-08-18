package com.fastfood.auth;

import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.ValidationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Chính sách mật khẩu")
class PasswordPolicyTest {

    @Nested
    @DisplayName("Mật khẩu bị từ chối")
    class Rejected {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "Abc123",
                "Abcd123",
                "abcdefgh",
                "12345678",
                "password1",
                "PASSWORD1",
                " Matkhau7",
                "Matkhau1 ",
                "        ",
        })
        void tooWeak(String password) {
            assertThrows(ValidationException.class,
                    () -> ValidationUtil.requirePasswordStrength(password));
        }

        @Test
        @DisplayName("Rỗng và null đều báo lỗi thay vì lọt qua")
        void emptyAndNull() {
            assertThrows(ValidationException.class, () -> ValidationUtil.requirePasswordStrength(null));
            assertThrows(ValidationException.class, () -> ValidationUtil.requirePasswordStrength(""));
        }

        @Test
        @DisplayName("Dài quá giới hạn của bcrypt thì bị chặn, không bị cắt cụt lặng lẽ")
        void beyondBcryptLimit() {
            String tooLong = "a1" + "x".repeat(71);
            assertThrows(ValidationException.class,
                    () -> ValidationUtil.requirePasswordStrength(tooLong));
        }

        @Test
        @DisplayName("Giới hạn đếm theo byte, không theo ký tự")
        void limitCountsBytesNotChars() {
            String vietnamese = "Mật1" + "ườ".repeat(20);
            assertTrue(vietnamese.length() < 72, "Bai test nay chi co nghia khi chuoi ngan hon 72 KY TU");
            assertThrows(ValidationException.class,
                    () -> ValidationUtil.requirePasswordStrength(vietnamese));
        }
    }

    @Nested
    @DisplayName("Mật khẩu được chấp nhận")
    class Accepted {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "Matkhau7",
                "MatKhauMoi9",
                "TempPass1",
                "MatKhauGoc7",
                "co dau va so 9",
                "Mật khẩu số 1",
        })
        void strongEnough(String password) {
            assertDoesNotThrow(() -> ValidationUtil.requirePasswordStrength(password));
        }
    }

    @Nested
    @DisplayName("Mật khẩu tạm do quản trị viên đặt hộ")
    class Temporary {

        @Test
        @DisplayName("Luôn qua được chính sách — nếu không thì chức năng đặt lại tự hỏng")
        void alwaysPassesPolicy() {
            for (int i = 0; i < 200; i++) {
                String temp = PasswordUtil.randomTemporary();
                assertDoesNotThrow(() -> ValidationUtil.requirePasswordStrength(temp),
                        () -> "Mat khau tam khong qua duoc chinh sach: " + temp);
            }
        }

        @Test
        @DisplayName("Mỗi lần một khác")
        void differsEveryTime() {
            assertNotEquals(PasswordUtil.randomTemporary(), PasswordUtil.randomTemporary());
        }

        @Test
        @DisplayName("Không chứa ký tự dễ đọc nhầm")
        void avoidsAmbiguousCharacters() {
            for (int i = 0; i < 200; i++) {
                String temp = PasswordUtil.randomTemporary();
                for (char c : "0O1lI".toCharArray()) {
                    assertEquals(-1, temp.indexOf(c),
                            "Mat khau tam chua ky tu de doc nham '" + c + "': " + temp);
                }
            }
        }

        @Test
        @DisplayName("Băm rồi vẫn đối chiếu lại được")
        void hashRoundTrips() {
            String temp = PasswordUtil.randomTemporary();
            assertTrue(PasswordUtil.matches(temp, PasswordUtil.hash(temp)));
        }
    }
}
