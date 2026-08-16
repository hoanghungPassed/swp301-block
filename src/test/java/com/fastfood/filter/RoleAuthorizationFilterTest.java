package com.fastfood.filter;

import com.fastfood.model.entity.User;
import com.fastfood.testsupport.FakeHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hành vi thật của bộ lọc phân quyền.
 * <p>
 * {@code RoutePolicyTest} đã kiểm bảng ánh xạ tiền tố → vai trò, tức là <i>quyết định</i> có
 * đúng không. Bài này kiểm việc <i>thi hành</i> quyết định đó: bộ lọc có thật sự dừng chuỗi lại,
 * và có trả về đúng loại phản hồi cho đúng loại địa chỉ.
 * <p>
 * Ranh giới cần nhớ: bộ lọc này chỉ xét quyền theo <b>địa chỉ</b>. Quyền theo dữ liệu — khách
 * chỉ xem được đơn của chính mình — phải đọc cơ sở dữ liệu nên nằm ở tầng Service, và được
 * canh bởi các bài {@code *IT} tương ứng.
 */
@DisplayName("Bộ lọc phân quyền theo vai trò")
class RoleAuthorizationFilterTest {

    private final RoleAuthorizationFilter filter = new RoleAuthorizationFilter();

    @Nested
    @DisplayName("Ma trận vai trò và khu vực")
    class MaTran {

        @ParameterizedTest(name = "{0} vào {1}")
        @CsvSource({
                "CASHIER, /staff/pos",
                "CASHIER, /staff/counter",
                "KITCHEN, /kitchen/queue",
                "KITCHEN, /api/kds/queue",
                "ADMIN,   /admin/dashboard",
        })
        @DisplayName("Đúng vai trò của khu vực thì vào được")
        void ownRoleGetsIn(String role, String path) throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request(path).signedInAs(role).build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @ParameterizedTest(name = "{0} vào {1}")
        @CsvSource({
                "CUSTOMER, /staff/pos",
                "CUSTOMER, /kitchen/queue",
                "CUSTOMER, /admin/dashboard",
                "CUSTOMER, /api/kds/queue",
                "CASHIER,  /kitchen/queue",
                "CASHIER,  /admin/users",
                "KITCHEN,  /staff/pos",
                "KITCHEN,  /admin/users",
        })
        @DisplayName("Sai vai trò thì bị chặn, và chuỗi bộ lọc dừng lại")
        void wrongRoleIsBlocked(String role, String path) throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request(path).signedInAs(role).build(),
                    FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran(),
                    role + " di qua duoc " + path + " — chan ma van goi tiep thi servlet van chay");
        }

        /** Quản trị viên xem được mọi màn hình vận hành để hỗ trợ và kiểm tra khi có sự cố. */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/staff/pos", "/kitchen/queue", "/api/kds/queue", "/admin/users"})
        @DisplayName("Quản trị viên đi được khắp nơi")
        void adminGoesEverywhere(String path) throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request(path).signedInAs("ADMIN").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }
    }

    @Nested
    @DisplayName("Cách từ chối")
    class CachTuChoi {

        @Test
        @DisplayName("Chưa đăng nhập thì đưa về trang đăng nhập, không phải trang 403")
        void anonymousIsSentToLogin() throws Exception {
            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/admin/users").build(), resp.build(), chain.build());

            assertFalse(chain.ran());
            assertEquals("/login", resp.redirectedTo(),
                    "Nguoi chua dang nhap can o dang nhap, khong can mot trang bao ho khong co quyen");
        }

        @Test
        @DisplayName("Đã đăng nhập nhưng sai quyền thì nhận trang 403 kèm lời giải thích")
        void wrongRoleGetsTheErrorPage() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/admin/users").signedInAs("CASHIER");
            FakeHttp.Response resp = FakeHttp.response();

            filter.doFilter(req.build(), resp.build(), FakeHttp.chain().build());

            assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status());
            assertEquals("/WEB-INF/views/error/403.jsp", req.forwardedTo());
            assertNull(resp.redirectedTo(), "403 la mot trang, khong phai mot lan chuyen huong");
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"CUSTOMER, 403", "'', 401"})
        @DisplayName("Địa chỉ JSON chỉ nhận mã trạng thái, không nhận trang HTML")
        void jsonEndpointsGetAStatusNotAPage(String role, int expectedStatus) throws Exception {
            FakeHttp.Request req = FakeHttp.request("/api/kds/queue");
            if (!role.isEmpty()) {
                req.signedInAs(role);
            }
            FakeHttp.Response resp = FakeHttp.response();

            filter.doFilter(req.build(), resp.build(), FakeHttp.chain().build());

            assertEquals(expectedStatus, resp.status());
            assertNull(req.forwardedTo(), "Trang goi bang JavaScript nhan ve HTML se hong cho doc ket qua");
            assertNull(resp.redirectedTo(),
                    "Chuyen huong tra ve HTML cua trang dang nhap — cung mot cai bay");
        }
    }

    @Nested
    @DisplayName("Dữ liệu vai trò hỏng")
    class VaiTroHong {

        /**
         * Vai trò đọc lên từ cơ sở dữ liệu nên về nguyên tắc luôn nằm trong bốn giá trị đã biết.
         * Nhưng nếu có ngày nó không nằm, kết cục phải là "không có quyền" chứ không phải một
         * trang lỗi 500 — và tuyệt đối không phải là đi tiếp.
         */
        @ParameterizedTest(name = "vai trò = \"{0}\"")
        @ValueSource(strings = {"SUPERUSER", "customer ", "", "ADMIN2"})
        @DisplayName("Tên vai trò lạ dẫn tới 403, không phải lỗi 500 và không phải cửa mở")
        void unknownRoleIsDeniedNotCrashed(String roleName) throws Exception {
            User user = new User();
            user.setUserId(1);
            user.setRoleName(roleName);
            user.setStatus("ACTIVE");
            FakeHttp.Chain chain = FakeHttp.chain();
            FakeHttp.Response resp = FakeHttp.response();

            filter.doFilter(FakeHttp.request("/admin/users").signedInAs(user).build(),
                    resp.build(), chain.build());

            assertFalse(chain.ran());
            assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status());
        }

        @Test
        @DisplayName("Vai trò để trống cũng vậy")
        void nullRoleIsDenied() throws Exception {
            User user = new User();
            user.setUserId(1);
            user.setStatus("ACTIVE");
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/admin/users").signedInAs(user).build(),
                    FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran());
        }

        /** Chữ hoa chữ thường trong cơ sở dữ liệu không được biến thành chuyện phân quyền. */
        @Test
        @DisplayName("Vai trò khác hoa thường vẫn nhận ra đúng")
        void roleMatchIsCaseInsensitive() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/admin/users").signedInAs("admin").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }
    }

    /**
     * Bộ lọc đọc đường dẫn qua {@link RequestPath}, tức là ghép {@code servletPath} với
     * {@code pathInfo} — đúng hai mảnh mà máy chủ dùng để chọn servlet. Tự cắt chuỗi từ
     * {@code getRequestURI()} thì với ánh xạ dạng tiền tố, hai bên sẽ nhìn hai đường dẫn khác nhau.
     */
    @Test
    @DisplayName("Servlet ánh xạ dạng tiền tố vẫn được xét đúng khu vực")
    void prefixMappedServletIsResolvedCorrectly() throws Exception {
        FakeHttp.Chain chain = FakeHttp.chain();

        filter.doFilter(FakeHttp.request("/kitchen", "/queue/item").signedInAs("CUSTOMER").build(),
                FakeHttp.response().build(), chain.build());

        assertFalse(chain.ran(), "Khach di vao duoc khu vuc bep vi duong dan bi ghep sai");
    }
}
