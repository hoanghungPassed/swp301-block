package com.fastfood.auth;

import com.fastfood.common.util.CsrfUtil;
import com.fastfood.common.util.SecureToken;
import com.fastfood.testsupport.FakeHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chất lượng của chuỗi bí mật dùng cho mã chống giả mạo và mã đặt lại mật khẩu.
 * <p>
 * Cả hai cơ chế đứng vững trên đúng một giả định: <b>người ngoài không đoán được chuỗi này</b>.
 * Giả định đó không có màn hình nào kiểm chứng hộ — mã sinh kém vẫn trông y hệt mã sinh tốt,
 * và hệ thống vẫn chạy đúng cho tới ngày có người thử đoán.
 */
@DisplayName("Mã bí mật dùng một lần")
class CsrfTokenTest {

    @Nested
    @DisplayName("Sinh mã")
    class SinhMa {

        @Test
        @DisplayName("Nghìn mã liên tiếp không trùng nhau lần nào")
        void tokensNeverRepeat() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                assertTrue(seen.add(SecureToken.generate()), "Sinh ra hai ma giong nhau");
            }
        }

        /** 32 byte ngẫu nhiên: quá rộng để dò hết trong khoảng thời gian mã còn hạn. */
        @Test
        @DisplayName("Đủ dài để không dò hết được")
        void longEnough() {
            String token = SecureToken.generate();
            assertTrue(token.length() >= 40,
                    "Ma chi dai " + token.length() + " ky tu — 32 byte ma hoa base64 phai ra 43");
        }

        /** Mã đi trong địa chỉ và trong ô ẩn của biểu mẫu, nên không được cần mã hoá thêm. */
        @Test
        @DisplayName("Chỉ gồm ký tự an toàn cho địa chỉ URL")
        void urlSafe() {
            for (int i = 0; i < 200; i++) {
                String token = SecureToken.generate();
                assertTrue(token.matches("[A-Za-z0-9_-]+"),
                        "Ma chua ky tu phai ma hoa moi dat vao URL duoc: " + token);
            }
        }
    }

    @Nested
    @DisplayName("Băm và đối chiếu")
    class BamVaDoiChieu {

        @Test
        @DisplayName("Cùng một mã luôn cho cùng một bản băm, dài đúng 64 ký tự")
        void hashIsDeterministic() {
            String token = SecureToken.generate();
            assertEquals(SecureToken.hash(token), SecureToken.hash(token));
            assertEquals(64, SecureToken.hash(token).length());
        }

        @Test
        @DisplayName("Hai mã khác nhau cho hai bản băm khác nhau")
        void hashSeparatesTokens() {
            assertNotEquals(SecureToken.hash("a"), SecureToken.hash("b"));
        }

        /**
         * {@code String.equals} dừng ngay ở ký tự đầu tiên khác nhau, nên thời gian trả lời tiết
         * lộ mình đã đoán đúng được mấy ký tự — đó là cách bẻ khoá từng ký tự một. Bài test không
         * đo được thời gian một cách đáng tin, nhưng kiểm được rằng kết quả đúng ở mọi trường hợp
         * biên mà cách so sánh này hay làm sai.
         */
        @Test
        @DisplayName("So khớp đúng ở mọi trường hợp biên")
        void comparisonEdgeCases() {
            assertTrue(SecureToken.matches("abc", "abc"));
            assertFalse(SecureToken.matches("abc", "abd"));
            assertFalse(SecureToken.matches("abc", "abcd"), "Chuoi dai hon khong duoc coi la khop");
            assertFalse(SecureToken.matches("abc", "ab"), "Chuoi ngan hon khong duoc coi la khop");
            assertFalse(SecureToken.matches("abc", ""));
            assertFalse(SecureToken.matches(null, "abc"));
            assertFalse(SecureToken.matches("abc", null));
            assertFalse(SecureToken.matches(null, null), "Hai ben deu trong khong phai la khop");
        }
    }

    @Nested
    @DisplayName("Mã gắn với phiên")
    class MaCuaPhien {

        @Test
        @DisplayName("Đọc nhiều lần trong một phiên vẫn ra cùng một mã")
        void stableWithinASession() {
            var req = FakeHttp.request("/menu").build();

            assertEquals(CsrfUtil.token(req), CsrfUtil.token(req));
        }

        @Test
        @DisplayName("Hai phiên khác nhau nhận hai mã khác nhau")
        void differsBetweenSessions() {
            assertNotEquals(CsrfUtil.token(FakeHttp.request("/menu").build()),
                    CsrfUtil.token(FakeHttp.request("/menu").build()));
        }

        /**
         * Gọi ngay sau khi đăng nhập. Phiên cũ đã bị huỷ nên mã cũ phải bỏ theo — mang sang phiên
         * mới thì mã mà kẻ tấn công đọc được từ phiên trước vẫn còn dùng được.
         */
        @Test
        @DisplayName("Cấp lại mã thì mã cũ hết giá trị")
        void rotateReplacesTheToken() {
            var req = FakeHttp.request("/login").build();
            String before = CsrfUtil.token(req);

            String after = CsrfUtil.rotate(req.getSession());

            assertNotEquals(before, after);
            assertEquals(after, CsrfUtil.token(req), "Ma moi phai la ma tu do ve sau doc duoc");
        }

        @Test
        @DisplayName("Chỉ mã của chính phiên đó mới được chấp nhận")
        void onlyTheOwnTokenIsAccepted() {
            String token = "ma-cua-phien";
            var ok = FakeHttp.request("/cart").method("POST")
                    .sessionAttribute("csrfToken", token).param(CsrfUtil.PARAM, token).build();
            var wrong = FakeHttp.request("/cart").method("POST")
                    .sessionAttribute("csrfToken", token).param(CsrfUtil.PARAM, "ma-khac").build();
            var missing = FakeHttp.request("/cart").method("POST")
                    .sessionAttribute("csrfToken", token).build();
            var noSession = FakeHttp.request("/cart").method("POST")
                    .param(CsrfUtil.PARAM, token).build();

            assertTrue(CsrfUtil.isValid(ok));
            assertFalse(CsrfUtil.isValid(wrong));
            assertFalse(CsrfUtil.isValid(missing));
            assertFalse(CsrfUtil.isValid(noSession), "Chua co phien thi khong co ma nao dung ca");
        }
    }
}
