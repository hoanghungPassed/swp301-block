package com.fastfood.auth;

import com.fastfood.common.util.WebUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lọc địa chỉ quay về sau khi đăng nhập.
 * <p>
 * Đây là chỗ dễ nhìn nhầm nhất trong cả phần xác thực: mọi chuỗi dưới đây đều <i>bắt đầu bằng
 * một dấu chéo</i>, nên nhìn qua đều giống đường dẫn nội bộ. Kiểm tra "có bắt đầu bằng /" là
 * bài kiểm tra ai cũng viết đầu tiên, và nó để lọt gần hết danh sách này.
 */
@DisplayName("Địa chỉ quay về sau khi đăng nhập")
class SafeRedirectTest {

    private static final String FALLBACK = "/menu";

    @Nested
    @DisplayName("Bị từ chối, trả về trang mặc định")
    class Rejected {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {
                "//evil.com/x",          // trình duyệt hiểu là địa chỉ tuyệt đối, giữ nguyên giao thức
                "///evil.com",           // ba chéo cũng vậy
                "/\\evil.com",           // chéo ngược, trình duyệt quy về dạng trên
                "/\\/evil.com",
                "/javascript:alert(1)",  // tên giao thức nấp trong phần đường dẫn
                "/data:text/html,x",
                "/http://evil.com",
                "/menu\nLocation: http://evil.com",   // chẻ đôi phần đầu của phản hồi HTTP
                "/menu\rSet-Cookie: a=b",
                "/menu\tx",
                "menu",                  // đường dẫn tương đối, không bắt đầu bằng /
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

        /**
         * Bản đầu tiên cấm dấu hai chấm ở khắp chuỗi, nên mọi bộ lọc theo thời điểm đều bị vứt
         * đi và người dùng lặng lẽ rơi về trang chủ sau khi đăng nhập. Dấu hai chấm sau dấu hỏi
         * không phải tên giao thức — trình duyệt đã chốt xong đích đến từ trước đó.
         */
        @Test
        @DisplayName("Dấu hai chấm trong chuỗi truy vấn là hợp lệ")
        void allowsColonInsideQueryString() {
            String target = "/staff/orders?from=2026-08-15T07:30";
            assertEquals(target, WebUtil.safeRedirect(target, FALLBACK));
        }
    }
}
