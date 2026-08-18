package com.fastfood.auth;

import com.fastfood.common.util.WebUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Địa chỉ quay về sau khi đăng nhập")
class SafeRedirectTest {

    private static final String FALLBACK = "/menu";

    @Nested
    @DisplayName("Bị từ chối, trả về trang mặc định")
    class Rejected {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "//evil.com/x",
                "///evil.com",
                "/\\evil.com",
                "/\\/evil.com",
                "/javascript:alert(1)",
                "/data:text/html,x",
                "/http://evil.com",
                "/menu\nLocation: http://evil.com",
                "/menu\rSet-Cookie: a=b",
                "/menu\tx",
                "menu",
                "http://evil.com",
                "",
                "   ",
        })
        void rejects(String target) {
            assertEquals(FALLBACK, WebUtil.safeRedirect(target, FALLBACK));
        }

        @Test
        @DisplayName("null trả về trang mặc định chứ không nổ")
        void rejectsNull() {
            assertEquals(FALLBACK, WebUtil.safeRedirect(null, FALLBACK));
        }
    }

    @Nested
    @DisplayName("Được chấp nhận")
    class Accepted {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "/",
                "/cart",
                "/order/history",
                "/admin/users?role=CASHIER&keyword=an",
        })
        void accepts(String target) {
            assertEquals(target, WebUtil.safeRedirect(target, FALLBACK));
        }

        @Test
        @DisplayName("Dấu hai chấm trong chuỗi truy vấn là hợp lệ")
        void allowsColonInsideQueryString() {
            String target = "/staff/orders?from=2026-08-15T07:30";
            assertEquals(target, WebUtil.safeRedirect(target, FALLBACK));
        }
    }
}
