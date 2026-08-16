package com.fastfood.filter;

import com.fastfood.common.util.CsrfUtil;
import com.fastfood.testsupport.FakeHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hành vi thật của bộ lọc chống giả mạo yêu cầu, chạy qua yêu cầu và phản hồi giả.
 * <p>
 * {@code CsrfTokenPresenceTest} đã canh phía biểu mẫu — mọi form POST đều mang ô ẩn. Bài này
 * canh phía còn lại: <b>máy chủ có thật sự từ chối</b> khi ô ẩn đó vắng mặt hay sai. Thiếu một
 * trong hai nửa thì nửa kia vô nghĩa: token có mà không ai kiểm cũng như không, và kiểm mà
 * biểu mẫu không gửi thì mọi nút đều hỏng.
 * <p>
 * Điều được khẳng định ở mọi bài dưới đây là <b>chuỗi bộ lọc có đi tiếp hay không</b>. Chặn mà
 * vẫn gọi tiếp thì servlet vẫn chạy và dữ liệu vẫn bị ghi, chỉ khác là kèm theo một mã lỗi
 * không ai đọc — đó là dạng "đã sửa" nguy hiểm nhất, vì nhìn log thì tưởng đã chặn được.
 */
@DisplayName("Bộ lọc chống giả mạo yêu cầu")
class CsrfFilterTest {

    private static final String TOKEN = "ma-hop-le-cua-phien-nay";

    private final CsrfFilter filter = new CsrfFilter();

    @Nested
    @DisplayName("Yêu cầu ghi dữ liệu")
    class YeuCauGhi {

        @Test
        @DisplayName("POST không kèm mã thì bị chặn, và chuỗi bộ lọc dừng lại")
        void postWithoutTokenIsBlocked() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/admin/users").method("POST")
                    .sessionAttribute("csrfToken", TOKEN);
            FakeHttp.Response resp = FakeHttp.response();
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(req.build(), resp.build(), chain.build());

            assertFalse(chain.ran(),
                    "Bo loc tra ve 403 nhung van goi tiep — servlet van chay va du lieu van bi ghi");
            assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status());
            assertEquals("/WEB-INF/views/error/403.jsp", req.forwardedTo());
        }

        @Test
        @DisplayName("POST kèm mã đúng thì đi tiếp bình thường")
        void postWithValidTokenPassesThrough() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/admin/users").method("POST")
                    .sessionAttribute("csrfToken", TOKEN)
                    .param(CsrfUtil.PARAM, TOKEN);
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(req.build(), FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        /**
         * Đây là chính cuộc tấn công mà bộ lọc sinh ra để chặn: trang khác không đọc được mã của
         * phiên nên chỉ đoán được, và một mã đoán sai phải bị đối xử y hệt như không có mã.
         */
        @Test
        @DisplayName("Mã sai bị từ chối y như không có mã")
        void wrongTokenIsRejected() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/admin/users").method("POST")
                    .sessionAttribute("csrfToken", TOKEN)
                    .param(CsrfUtil.PARAM, "ma-doan-bua");
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(req.build(), FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran());
        }

        /**
         * Mã đúng của <b>phiên khác</b> cũng vô dụng. Nếu không thì kẻ tấn công chỉ cần tự mở
         * một phiên, lấy mã của mình, rồi gắn vào biểu mẫu gửi đi dưới danh nghĩa nạn nhân.
         */
        @Test
        @DisplayName("Mã của phiên khác không dùng được")
        void tokenFromAnotherSessionIsRejected() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/admin/users").method("POST")
                    .sessionAttribute("csrfToken", "ma-cua-nan-nhan")
                    .param(CsrfUtil.PARAM, "ma-cua-ke-tan-cong");
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(req.build(), FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran());
        }

        /** Chưa có phiên thì không có mã nào đúng cả — kể cả một chuỗi rỗng khớp với null. */
        @Test
        @DisplayName("Không có phiên thì mọi mã đều sai")
        void noSessionMeansNoValidToken() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/cart").method("POST").param(CsrfUtil.PARAM, "").build(),
                    FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
        @DisplayName("Mọi phương thức làm thay đổi dữ liệu đều phải có mã")
        void everyUnsafeMethodNeedsAToken(String method) throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/admin/users").method(method)
                            .sessionAttribute("csrfToken", TOKEN).build(),
                    FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran(), method + " di qua duoc ma khong can ma chong gia mao");
        }
    }

    @Nested
    @DisplayName("Yêu cầu chỉ đọc")
    class YeuCauDoc {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
        @DisplayName("Không đổi gì ở máy chủ thì không cần mã")
        void safeMethodsPassWithoutAToken(String method) throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/admin/users").method(method).build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @Test
        @DisplayName("Trang được dựng ra luôn nhận sẵn mã để đặt vào biểu mẫu")
        void everyRenderedPageGetsAToken() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/menu");

            filter.doFilter(req.build(), FakeHttp.response().build(), FakeHttp.chain().build());

            Object token = req.attribute(CsrfUtil.REQUEST_ATTRIBUTE);
            assertNotNull(token, "Trang khong co ma thi moi bieu mau tren do gui len se bi tu choi");
            assertFalse(token.toString().isBlank());
        }

        @Test
        @DisplayName("Mã giữ nguyên trong suốt một phiên, để nhiều thẻ mở cùng lúc đều dùng được")
        void tokenIsStableAcrossRequests() throws Exception {
            FakeHttp.Request first = FakeHttp.request("/menu").sessionAttribute("csrfToken", TOKEN);
            FakeHttp.Request second = FakeHttp.request("/cart").sessionAttribute("csrfToken", TOKEN);

            filter.doFilter(first.build(), FakeHttp.response().build(), FakeHttp.chain().build());
            filter.doFilter(second.build(), FakeHttp.response().build(), FakeHttp.chain().build());

            assertEquals(first.attribute(CsrfUtil.REQUEST_ATTRIBUTE),
                    second.attribute(CsrfUtil.REQUEST_ATTRIBUTE),
                    "Ma doi giua hai lan tai trang thi the mo truoc se hong khi the mo sau gui di — "
                            + "man hinh quay va man hinh bep mo nhieu the cung luc la chuyen binh thuong");
        }

        /**
         * Ảnh và CSS không cần mã, và cũng không nên vì thế mà cấp phiên cho mọi người lạ ghé
         * qua: mỗi phiên là một dòng trong bộ đếm phiên đang hoạt động của màn hình quản trị.
         */
        @Test
        @DisplayName("Tài nguyên tĩnh đi thẳng, không tạo phiên")
        void staticAssetsSkipTheFilterEntirely() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/assets/css/main.css");
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(req.build(), FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
            assertNull(req.attribute(CsrfUtil.REQUEST_ATTRIBUTE),
                    "Xin the anh cung tao mot phien thi bo dem phien dang hoat dong thanh vo nghia");
        }
    }

    @Nested
    @DisplayName("Hai ngoại lệ có chủ ý")
    class NgoaiLe {

        /**
         * Cổng thanh toán gọi vào từ máy chủ của họ, không đi qua trang nào của mình nên không
         * thể có mã. Chỗ này được canh bằng thứ khác: chữ ký của cổng, và ràng buộc duy nhất
         * trên mã giao dịch để một lần gọi trùng không tính thành hai lần trả tiền.
         */
        @Test
        @DisplayName("Cổng thanh toán gọi về được, dù không có mã")
        void gatewayCallbackIsExempt() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/payment/callback").method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        /**
         * Với SePay còn một lý do thứ hai ngoài chuyện không có mã: dữ liệu về ở thân yêu cầu
         * dạng JSON, mà kiểm mã thì phải đọc tham số — đọc tham số của một POST là đọc hết thân,
         * và servlet đứng sau sẽ nhận được một thân rỗng.
         */
        @Test
        @DisplayName("Webhook SePay gọi về được, dù không có mã")
        void sepayWebhookIsExempt() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/payment/sepay/webhook").method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertTrue(chain.ran());
        }

        @Test
        @DisplayName("Miễn trừ đúng một địa chỉ đó, không lan sang địa chỉ bắt đầu bằng nó")
        void exemptionDoesNotLeakToNeighbouringPaths() throws Exception {
            FakeHttp.Chain chain = FakeHttp.chain();

            filter.doFilter(FakeHttp.request("/payment/callback/admin").method("POST").build(),
                    FakeHttp.response().build(), chain.build());

            assertFalse(chain.ran(),
                    "Mien tru khop theo tien to thi them mot doan vao sau la vuot qua duoc bo loc");
        }

        /**
         * Trang gọi bằng JavaScript đọc kết quả dạng JSON. Trả về HTML của trang 403 thì chỗ
         * đọc kết quả hỏng, và màn hình bếp im lặng đứng lại thay vì báo phiên đã hết hạn.
         */
        @Test
        @DisplayName("Địa chỉ JSON chỉ nhận mã trạng thái, không nhận trang HTML")
        void jsonEndpointsGetAStatusNotAPage() throws Exception {
            FakeHttp.Request req = FakeHttp.request("/api/kds/queue").method("POST");
            FakeHttp.Response resp = FakeHttp.response();

            filter.doFilter(req.build(), resp.build(), FakeHttp.chain().build());

            assertEquals(HttpServletResponse.SC_FORBIDDEN, resp.status());
            assertNull(req.forwardedTo(), "Khong duoc chuyen tiep sang trang HTML");
        }
    }

    /**
     * Trang 403 cũng có thanh điều hướng, và trên đó nút Thoát là một biểu mẫu. Từ chối rồi mới
     * phát mã thì đúng cái nút để người dùng gỡ mình ra khỏi tình huống lại là cái nút duy nhất
     * không bấm được.
     */
    @Test
    @DisplayName("Ngay cả khi từ chối, trang lỗi vẫn nhận được mã để dựng nút Thoát")
    void rejectedRequestStillGetsATokenForTheErrorPage() throws Exception {
        FakeHttp.Request req = FakeHttp.request("/admin/users").method("POST")
                .sessionAttribute("csrfToken", TOKEN);

        filter.doFilter(req.build(), FakeHttp.response().build(), FakeHttp.chain().build());

        assertEquals(TOKEN, req.attribute(CsrfUtil.REQUEST_ATTRIBUTE));
    }
}
