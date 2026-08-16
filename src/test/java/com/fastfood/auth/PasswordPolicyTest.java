package com.fastfood.auth;

import com.fastfood.common.exception.ValidationException;
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
                "Abc123",        // 6 ký tự, ngắn hơn mức tối thiểu
                "Abcd123",       // 7 ký tự
                "abcdefgh",      // không có số
                "12345678",      // không có chữ, mà cũng nằm trong danh sách phổ biến
                "password1",     // đủ chữ đủ số đủ dài, nhưng ai cũng thử nó đầu tiên
                "PASSWORD1",     // và hoa thường không làm nó bớt phổ biến
                " Matkhau7",     // khoảng trắng đầu, gần như luôn là do dán nhầm
                "Matkhau1 ",     // khoảng trắng cuối
                "        ",      // chỉ toàn khoảng trắng
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

        /**
         * Bcrypt chỉ đọc 72 byte đầu và bỏ im lặng phần còn lại. Không chặn thì hai mật khẩu
         * khác nhau từ byte thứ 73 trở đi cùng mở được một tài khoản — và không có gì trên màn
         * hình cho thấy chuyện đó đang xảy ra.
         */
        @Test
        @DisplayName("Dài quá giới hạn của bcrypt thì bị chặn, không bị cắt cụt lặng lẽ")
        void beyondBcryptLimit() {
            String tooLong = "a1" + "x".repeat(71);   // 73 byte
            assertThrows(ValidationException.class,
                    () -> ValidationUtil.requirePasswordStrength(tooLong));
        }

        /** Tiếng Việt có dấu tốn tới ba byte mỗi chữ, nên đếm ký tự là đếm sai. */
        @Test
        @DisplayName("Giới hạn đếm theo byte, không theo ký tự")
        void limitCountsBytesNotChars() {
            String vietnamese = "Mật1" + "ườ".repeat(20);   // 44 ký tự nhưng hơn 72 byte
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
                "Matkhau7",         // đúng mức tối thiểu
                "MatKhauMoi9",      // các bài test tích hợp đang dùng
                "TempPass1",
                "MatKhauGoc7",
                "co dau va so 9",   // khoảng trắng ở giữa thì hoàn toàn hợp lệ
                "Mật khẩu số 1",    // tiếng Việt có dấu
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

        /**
         * Điều làm mật khẩu tạm khác một chuỗi cố định gõ cứng trong mã trang: nó khác nhau ở
         * mỗi lần. Trước đây mọi tài khoản được đặt lại đều về cùng một chuỗi mà cả lớp biết.
         */
        @Test
        @DisplayName("Mỗi lần một khác")
        void differsEveryTime() {
            assertNotEquals(PasswordUtil.randomTemporary(), PasswordUtil.randomTemporary());
        }

        /** Sinh ra để đọc qua điện thoại: 0 với O, 1 với l đọc sai là phải gọi lại lần nữa. */
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
