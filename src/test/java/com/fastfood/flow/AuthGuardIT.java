package com.fastfood.flow;

import com.fastfood.common.util.WebUtil;
import com.fastfood.filter.AuthenticationFilter;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.admin.AdminService;
import com.fastfood.service.auth.AuthService;
import com.fastfood.testsupport.FakeHttp;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bộ lọc đăng nhập và đồng bộ phiên")
class AuthGuardIT extends IntegrationTestBase {

    private final AuthService authService = new AuthService();
    private final AdminService adminService = new AdminService();
    private final AuthenticationFilter filter = new AuthenticationFilter();

    @Nested
    @DisplayName("Cửa vào")
    class CuaVao {

        @Test
        @DisplayName("Chưa đăng nhập mà mở trang cần đăng nhập thì bị đưa về cửa đăng nhập")
        void anonymousIsSentToLogin() throws Exception {
            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/cart").build(), resp.build(), chain.build());

            assertFalse(chain.ran());
            assertEquals("/login", resp.redirectedTo());
        }

        @Test
        @DisplayName("Trang khách định vào được nhớ lại, kèm cả bộ lọc trên địa chỉ")
        void theIntendedPageIsRemembered() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/order/history").queryString("status=READY");

            filter.doFilter(req.build(), FakeHttp.response().build(), FakeHttp.chain().build());

            assertEquals("/order/history?status=READY",
                    req.sessionValue(WebUtil.REDIRECT_AFTER_LOGIN),
                    "Quen chuoi tham so thi dang nhap xong khach quay lai mot danh sach khong loc");
        }

        @Test
        @DisplayName("Trang công khai đi thẳng, không cần phiên nào")
        void publicPagesPassThrough() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/menu").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @Test
        @DisplayName("Tài khoản còn hiệu lực thì đi qua bình thường")
        void activeAccountPassesThrough() throws Exception {
            FakeHttp.Session session = signedIn(newAccount());
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/cart").in(session).build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }
    }

    @Nested
    @DisplayName("Phiên bám theo trạng thái thật của tài khoản")
    class DongBoPhien {

        @Test
        @DisplayName("Quản trị viên khoá tài khoản thì phiên đang mở bị chấm dứt")
        void lockingAnAccountEndsItsLiveSession() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);

            adminService.setUserStatus(userId(ADMIN), id, "LOCKED");

            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    resp.build(), chain.build());

            assertFalse(chain.ran(), "Tai khoan da bi khoa van thao tac duoc");
            assertEquals("/login", resp.redirectedTo());
            assertEquals(1, session.invalidations(), "Phien phai bi huy, khong chi bi chan mot lan");
        }

        @Test
        @DisplayName("Yêu cầu ghi luôn soi lại ngay, không đợi hết nhịp")
        void writesAlwaysRevalidateImmediately() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);

            filter.doFilter(FakeHttp.request("/cart").in(session).build(),
                    FakeHttp.response().build(), FakeHttp.chain().build());

            adminService.setUserStatus(userId(ADMIN), id, "LOCKED");

            FakeHttp.Chain xem = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/cart").in(session).build(),
                    FakeHttp.response().build(), xem.build());
            assertTrue(xem.ran(),
                    "Trong cua so 30 giay, mot lan XEM trang van di qua — day la danh doi da biet");

            FakeHttp.Chain ghi = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    FakeHttp.response().build(), ghi.build());
            assertFalse(ghi.ran(),
                    "Thao tac GHI bang quyen vua bi thu hoi van la thao tac khong duoc phep");
        }

        @Test
        @DisplayName("Đổi vai trò thì phiên đang mở nhận vai trò mới, không đợi đăng nhập lại")
        void roleChangeReachesTheLiveSession() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            assertEquals("CUSTOMER", session.currentUser().getRoleName());

            Integer kitchenRole = scalar(Integer.class, "SELECT role_id FROM dbo.Role WHERE name = 'KITCHEN'");
            adminService.setUserRole(userId(ADMIN), id, kitchenRole);

            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    FakeHttp.response().build(), FakeHttp.chain().build());

            assertEquals("KITCHEN", session.currentUser().getRoleName(),
                    "Ha quyen mot tai khoan ma phien cu van giu vai tro cu thi ha quyen khong co "
                            + "tac dung gi cho toi het gio phien");
        }

        @Test
        @DisplayName("Trang công khai cũng đồng bộ, để thanh điều hướng không nói dối")
        void publicPagesSyncTooWhenSignedIn() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.setUserStatus(userId(ADMIN), id, "LOCKED");

            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/menu").in(session).method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran(), "Trang cong khai van phai mo ra — chi la mo ra o trang thai khach");
            assertFalse(session.alive(), "Phien cua tai khoan da khoa phai bi huy");
        }

        @Test
        @DisplayName("Bản làm mới cũng không mang bản băm mật khẩu vào phiên")
        void refreshedUserCarriesNoPasswordHash() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.updateUserInfo(userId(ADMIN), id, "Ten Moi", null);

            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    FakeHttp.response().build(), FakeHttp.chain().build());

            assertEquals("Ten Moi", session.currentUser().getFullName(), "Ban chup phai duoc lam moi");
            assertNull(session.currentUser().getPasswordHash(),
                    "Mot dong ${me.passwordHash} go nham o bat ky trang nao se in ban bam ra HTML");
        }
    }

    @Nested
    @DisplayName("Tài khoản đang bị buộc đổi mật khẩu")
    class BuocDoiMatKhau {

        @Test
        @DisplayName("Bị giữ lại ở trang tài khoản, không đi được màn hình nào khác")
        void heldAtTheProfilePage() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.resetPassword(userId(ADMIN), id, "TempPass1");

            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    resp.build(), chain.build());

            assertFalse(chain.ran());
            assertEquals("/profile", resp.redirectedTo());
        }

        @Test
        @DisplayName("Trang tài khoản thì vào được — nếu không thì không còn đường nào tự gỡ ra")
        void theProfilePageItselfStaysOpen() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.resetPassword(userId(ADMIN), id, "TempPass1");

            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/profile").in(session).method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @Test
        @DisplayName("Vẫn thoát ra được")
        void logoutStaysReachable() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.resetPassword(userId(ADMIN), id, "TempPass1");

            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/logout").in(session).method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @Test
        @DisplayName("Tự đặt mật khẩu mới xong thì rào chắn mở ra ngay")
        void changingThePasswordLiftsTheHold() throws Exception {
            int id = newAccount();
            FakeHttp.Session session = signedIn(id);
            adminService.resetPassword(userId(ADMIN), id, "TempPass1");

            authService.changePassword(id, "TempPass1", "MatKhauMoi9", "MatKhauMoi9");

            FakeHttp.Chain chain = FakeHttp.chain();
            filter.doFilter(FakeHttp.request("/cart").in(session).method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran(), "Lam dung viec duoc yeu cau roi thi phai duoc di tiep");
        }
    }

    private int newAccount() {
        String email = "test-guard-" + System.nanoTime() + "@gmail.com";
        return authService.register("Khach Test", email, null, "MatKhauGoc7", "MatKhauGoc7")
                .getUserId();
    }

    private FakeHttp.Session signedIn(int userId) {
        User user = authService.findById(userId);
        assertNotNull(user, "Khong tim thay tai khoan vua tao");
        return FakeHttp.session().signedInAs(user);
    }
}
