package com.fastfood.auth;

import com.fastfood.common.constant.RoleName;
import com.fastfood.filter.AuthenticationFilter;
import com.fastfood.filter.RoleAuthorizationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.servlet.annotation.WebServlet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ranh giới quyền theo <b>địa chỉ URL</b>, đọc từ chính ba nguồn quyết định nó: chú thích
 * {@code @WebServlet} trên từng servlet, danh sách trang công khai trong
 * {@link AuthenticationFilter}, và các tiền tố khai báo cho bộ lọc phân quyền trong
 * {@code web.xml}.
 * <p>
 * <b>Vì sao cần.</b> Ba nguồn đó nằm ở ba tệp khác nhau và không tệp nào biết tệp kia. Thêm một
 * màn hình mới là thêm một dòng vào nguồn thứ nhất, còn hai nguồn kia phải tự nhớ mà sửa theo —
 * và quên thì <b>không có gì hỏng</b>:
 * <ul>
 *   <li>Quên khai báo tiền tố trong {@code web.xml}: màn hình vẫn chạy đúng cho người có quyền,
 *       chỉ là nó cũng chạy đúng cho mọi tài khoản khác.</li>
 *   <li>Thêm nhầm một địa chỉ vào danh sách công khai: khách chưa đăng nhập vào được, mà không
 *       màn hình nào báo lỗi.</li>
 *   <li>Đặt một màn hình của khách dưới tiền tố {@code /staff/}: khách nhận 403 trên chính
 *       trang của mình — cái này thì lộ ngay, nhưng chỉ lộ khi có người mở đúng trang đó.</li>
 * </ul>
 * Cả ba đều là lỗi im lặng, và cả ba đều là lỗi về quyền. Đây là bài {@code *Test} chứ không
 * phải {@code *IT}: nó chỉ đọc chú thích và tệp cấu hình, không cần cơ sở dữ liệu.
 */
@DisplayName("Ranh giới quyền theo địa chỉ URL")
class RoutePolicyTest {

    private static final Path SRC = Path.of("src", "main", "java");
    private static final Path WEB_XML = Path.of("src", "main", "webapp", "WEB-INF", "web.xml");

    /** Hai trang cố ý mở cho người chưa đăng nhập — xem ghi chú ở {@code controller/customer}. */
    private static final Set<String> TRANG_CONG_KHAI_CO_Y = Set.of("/menu", "/product/detail");

    /**
     * Địa chỉ cổng thanh toán gọi vào từ máy chủ của họ. Bắt buộc phải mở vì lệnh gọi ấy không
     * mang theo phiên của bất kỳ ai; đổi lại, chúng không đọc dữ liệu của người dùng ra và phải
     * tự chứng minh bằng chữ ký hoặc khoá API.
     */
    private static final Set<String> CONG_THANH_TOAN_GOI_VE =
            Set.of("/payment/callback", "/payment/sepay/webhook");

    // ------------------------------------------------------------------ đọc nguồn

    /** Địa chỉ khai báo bằng {@code @WebServlet}, gom theo gói con của {@code controller}. */
    private static Map<String, List<String>> diaChiTheoGoi() throws Exception {
        Map<String, List<String>> theoGoi = new LinkedHashMap<>();
        for (String goi : List.of("customer", "staff", "kitchen", "admin", "auth", "api")) {
            List<String> diaChi = new ArrayList<>();
            Path thuMuc = SRC.resolve("com/fastfood/controller/" + goi);
            try (Stream<Path> tep = Files.list(thuMuc)) {
                for (Path p : tep.toList()) {
                    String ten = p.getFileName().toString();
                    if (!ten.endsWith(".java") || ten.equals("package-info.java")) {
                        continue;
                    }
                    Class<?> lop = Class.forName("com.fastfood.controller." + goi + "."
                            + ten.substring(0, ten.length() - 5));
                    WebServlet khaiBao = lop.getAnnotation(WebServlet.class);
                    if (khaiBao != null) {
                        diaChi.addAll(List.of(khaiBao.value()));
                    }
                }
            }
            assertFalse(diaChi.isEmpty(), "Khong doc duoc servlet nao trong controller/" + goi);
            theoGoi.put(goi, diaChi);
        }
        return theoGoi;
    }

    /** Các tiền tố mà {@code web.xml} gắn bộ lọc phân quyền vào. */
    private static Set<String> tienToPhanQuyen() throws Exception {
        String xml = Files.readString(WEB_XML);
        int dau = xml.indexOf("04-RoleAuthorizationFilter</filter-name>", xml.indexOf("<filter-mapping>"));
        assertTrue(dau > 0, "Khong thay filter-mapping cua RoleAuthorizationFilter trong web.xml");
        int cuoi = xml.indexOf("</filter-mapping>", dau);

        Set<String> mau = new LinkedHashSet<>();
        Matcher m = Pattern.compile("<url-pattern>([^<]+)</url-pattern>")
                .matcher(xml.substring(dau, cuoi));
        while (m.find()) {
            mau.add(m.group(1).trim());
        }
        assertFalse(mau.isEmpty(), "filter-mapping cua RoleAuthorizationFilter khong co url-pattern nao");
        return mau;
    }

    /** Một địa chỉ có rơi vào tiền tố nào của bộ lọc phân quyền không. */
    private static boolean bocBoiPhanQuyen(String diaChi, Set<String> mau) {
        for (String pattern : mau) {
            if (pattern.endsWith("/*") && diaChi.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
            if (pattern.equals(diaChi)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ bài kiểm

    @Nested
    @DisplayName("Màn hình của khách hàng")
    class Khach {

        @Test
        @DisplayName("Mọi trang của khách đều bắt đăng nhập, trừ hai trang công khai cố ý")
        void everyCustomerPageRequiresLogin() throws Exception {
            List<String> mo_cho_khach_vang_lai = new ArrayList<>();
            for (String diaChi : diaChiTheoGoi().get("customer")) {
                if (AuthenticationFilter.isPublicPath(diaChi)
                        && !TRANG_CONG_KHAI_CO_Y.contains(diaChi)
                        && !CONG_THANH_TOAN_GOI_VE.contains(diaChi)) {
                    mo_cho_khach_vang_lai.add(diaChi);
                }
            }

            assertTrue(mo_cho_khach_vang_lai.isEmpty(),
                    "Những địa chỉ này lọt vào danh sách công khai nên ai cũng mở được: "
                            + mo_cho_khach_vang_lai + ". Giỏ hàng, lịch sử đơn, hộp thông báo và "
                            + "trang tài khoản đều gắn với một người cụ thể — mở ra là mở dữ liệu "
                            + "của họ cho người lạ.");
        }

        @Test
        @DisplayName("Không màn hình nào của khách rơi vào tiền tố dành cho nhân viên")
        void customerPagesAreNotUnderStaffPrefixes() throws Exception {
            Set<String> mau = tienToPhanQuyen();

            for (String diaChi : diaChiTheoGoi().get("customer")) {
                assertFalse(bocBoiPhanQuyen(diaChi, mau),
                        "Địa chỉ " + diaChi + " rơi vào tiền tố phân quyền. Nhánh mặc định của "
                                + "requiredRole trả về ADMIN, nên khách sẽ nhận 403 trên chính "
                                + "trang của mình.");
            }
        }

        @Test
        @DisplayName("Hai trang công khai đúng là hai trang đã ghi trong tài liệu")
        void publicPagesAreExactlyTheDocumentedTwo() throws Exception {
            List<String> cong_khai = new ArrayList<>();
            for (String diaChi : diaChiTheoGoi().get("customer")) {
                if (AuthenticationFilter.isPublicPath(diaChi)) {
                    cong_khai.add(diaChi);
                }
            }

            assertTrue(cong_khai.containsAll(TRANG_CONG_KHAI_CO_Y),
                    "Thực đơn và chi tiết món phải mở cho người chưa đăng nhập — đóng lại là "
                            + "bắt khách tạo tài khoản trước khi biết cửa hàng bán gì. Nhận được: "
                            + cong_khai);
        }
    }

    @Nested
    @DisplayName("Màn hình đặc quyền")
    class DacQuyen {

        @Test
        @DisplayName("Mọi servlet của thu ngân, bếp và quản trị đều đi qua bộ lọc phân quyền")
        void everyPrivilegedServletIsCovered() throws Exception {
            Set<String> mau = tienToPhanQuyen();
            Map<String, List<String>> theoGoi = diaChiTheoGoi();
            List<String> khong_ai_canh = new ArrayList<>();

            for (String goi : List.of("staff", "kitchen", "admin")) {
                for (String diaChi : theoGoi.get(goi)) {
                    if (!bocBoiPhanQuyen(diaChi, mau)) {
                        khong_ai_canh.add(goi + " → " + diaChi);
                    }
                }
            }

            assertTrue(khong_ai_canh.isEmpty(),
                    "Những địa chỉ này không nằm dưới tiền tố nào của bộ lọc phân quyền, nghĩa là "
                            + "bất kỳ tài khoản nào đăng nhập cũng vào được: " + khong_ai_canh
                            + ". Thêm tiền tố tương ứng vào filter-mapping trong web.xml.");
        }

        @Test
        @DisplayName("Dữ liệu JSON của bếp cũng được canh, không chỉ trang có giao diện")
        void kdsJsonEndpointIsCovered() throws Exception {
            Set<String> mau = tienToPhanQuyen();
            List<String> api = diaChiTheoGoi().get("api");
            List<String> cua_bep = api.stream().filter(d -> d.startsWith("/api/kds/")).toList();

            assertFalse(cua_bep.isEmpty(), "Khong thay endpoint /api/kds/* nao");
            for (String diaChi : cua_bep) {
                assertTrue(bocBoiPhanQuyen(diaChi, mau),
                        "Hàng chờ bếp lộ ra qua hai đường, trang và JSON. Thiếu đường thứ hai thì "
                                + "bất kỳ ai đăng nhập cũng đọc được toàn bộ việc của bếp: " + diaChi);
            }
        }

        @Test
        @DisplayName("Không địa chỉ đặc quyền nào lọt vào danh sách trang công khai")
        void privilegedPathsAreNeverPublic() throws Exception {
            Map<String, List<String>> theoGoi = diaChiTheoGoi();

            for (String goi : List.of("staff", "kitchen", "admin")) {
                for (String diaChi : theoGoi.get(goi)) {
                    assertFalse(AuthenticationFilter.isPublicPath(diaChi),
                            "Địa chỉ " + diaChi + " vừa đặc quyền vừa công khai — bộ lọc đăng nhập "
                                    + "cho qua thì bộ lọc phân quyền không có ai để xét quyền.");
                }
            }
        }
    }

    @Nested
    @DisplayName("Bảng ánh xạ vai trò")
    class AnhXaVaiTro {

        @Test
        @DisplayName("Mỗi tiền tố dẫn về đúng vai trò của nó")
        void prefixesMapToTheRightRole() {
            assertEquals(RoleName.CASHIER, RoleAuthorizationFilter.requiredRole("/staff/pos"));
            assertEquals(RoleName.KITCHEN, RoleAuthorizationFilter.requiredRole("/kitchen/queue"));
            assertEquals(RoleName.KITCHEN, RoleAuthorizationFilter.requiredRole("/api/kds/queue"));
            assertEquals(RoleName.ADMIN, RoleAuthorizationFilter.requiredRole("/admin/dashboard"));
        }

        @Test
        @DisplayName("Địa chỉ lạ rơi về vai trò chặt nhất, không phải mở cửa")
        void unknownPathFallsBackToTheStrictestRole() {
            assertEquals(RoleName.ADMIN, RoleAuthorizationFilter.requiredRole("/mot-dia-chi-la"));
            assertEquals(RoleName.ADMIN, RoleAuthorizationFilter.requiredRole(""));
            assertEquals(RoleName.ADMIN, RoleAuthorizationFilter.requiredRole(null));
        }

        @Test
        @DisplayName("Tiền tố phải khớp trọn một đoạn đường dẫn, không phải khớp chuỗi")
        void prefixMatchIsNotSubstringMatch() {
            assertEquals(RoleName.ADMIN, RoleAuthorizationFilter.requiredRole("/staffroom"),
                    "\"/staffroom\" không phải là một trang của thu ngân. Khớp theo chuỗi thì một "
                            + "địa chỉ đặt tên vô tình như vậy sẽ được xét bằng quyền thu ngân.");
        }
    }

    @Nested
    @DisplayName("Tài nguyên tĩnh và trang đăng nhập")
    class CongKhai {

        @Test
        @DisplayName("Ảnh, CSS và JavaScript mở cho mọi người")
        void assetsAreOpen() {
            assertTrue(AuthenticationFilter.isPublicPath("/assets/css/main.css"));
            assertTrue(AuthenticationFilter.isPublicPath("/assets/js/app.js"));
        }

        @Test
        @DisplayName("Luồng đăng nhập và quên mật khẩu mở cho người đang không vào được tài khoản")
        void authFlowIsOpen() {
            for (String diaChi : List.of("/login", "/register", "/forgot-password", "/reset-password")) {
                assertTrue(AuthenticationFilter.isPublicPath(diaChi),
                        diaChi + " phải mở: chỉ người đang KHÔNG vào được tài khoản mới cần tới nó");
            }
        }

        @Test
        @DisplayName("Cổng thanh toán gọi về được, vì nó không mang theo phiên của khách")
        void gatewayCallbackIsOpen() {
            for (String diaChi : CONG_THANH_TOAN_GOI_VE) {
                assertTrue(AuthenticationFilter.isPublicPath(diaChi),
                        diaChi + ": cổng gọi vào từ máy chủ của họ. Bù lại, dữ liệu phải qua kiểm "
                                + "tra chữ ký hoặc khoá API, số tiền phải khớp, và mã giao dịch "
                                + "được chống trùng ở tầng cơ sở dữ liệu");
            }
        }

        @Test
        @DisplayName("Một địa chỉ chưa từng khai báo thì mặc định là đóng")
        void unknownPathIsClosedByDefault() {
            assertFalse(AuthenticationFilter.isPublicPath("/mot-man-hinh-moi"),
                    "Liệt kê trang công khai an toàn hơn liệt kê trang cần bảo vệ: quên khai báo "
                            + "thì hậu quả là bắt đăng nhập thừa, không phải để lộ dữ liệu");
            assertFalse(AuthenticationFilter.isPublicPath(null));
        }
    }
}
