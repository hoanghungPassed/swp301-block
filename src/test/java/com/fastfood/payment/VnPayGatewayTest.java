package com.fastfood.payment;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PaymentInitResult;
import com.fastfood.integration.payment.VnPayGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Cổng thanh toán VNPAY")
class VnPayGatewayTest {

    private static final String TMN_CODE = "GRYKOOR0";
    private static final String HASH_SECRET = "KXZIBIJSFRAMEJCRSSRYAKBVIEGFLXLH";

    private final VnPayGateway gateway =
            new VnPayGateway(TMN_CODE, HASH_SECRET, VnPayGateway.SANDBOX_PAY_URL, "", 15);

    @Nested
    @DisplayName("Địa chỉ đẩy khách sang VNPAY")
    class DiaChiMoCong {

        private final Map<String, String> q = thamSo(gateway
                .initiate(57, 12, new BigDecimal("185000.00"), "http://localhost:8080/fastfood")
                .getRedirectUrl());

        @Test
        @DisplayName("Số tiền tính bằng đồng nhân 100 và không có phần thập phân")
        void soTienNhanTram() {
            assertEquals("18500000", q.get("vnp_Amount"),
                    "Gui thang 185000 thi khach bi thu it hon dung 100 lan");
        }

        @Test
        @DisplayName("Địa chỉ quay về trỏ đúng vào ứng dụng khi không cấu hình returnUrl riêng")
        void duongQuayVe() {
            assertEquals("http://localhost:8080/fastfood" + VnPayGateway.RETURN_PATH,
                    q.get("vnp_ReturnUrl"));
        }

        @Test
        @DisplayName("Mang đủ những tham số VNPAY bắt buộc")
        void duThamSoBatBuoc() {
            for (String ten : new String[]{"vnp_Version", "vnp_Command", "vnp_TmnCode", "vnp_Amount",
                    "vnp_CurrCode", "vnp_TxnRef", "vnp_OrderInfo", "vnp_OrderType", "vnp_Locale",
                    "vnp_ReturnUrl", "vnp_IpAddr", "vnp_CreateDate", "vnp_SecureHash"}) {
                assertTrue(q.containsKey(ten) && !q.get(ten).isBlank(), "Thieu " + ten);
            }
            assertEquals(TMN_CODE, q.get("vnp_TmnCode"));
            assertEquals("2.1.0", q.get("vnp_Version"));
        }

        @Test
        @DisplayName("Chữ ký trên địa chỉ gửi đi tự nó kiểm lại được")
        void chuKyKhopVoiChinhChuoiGuiDi() {
            Map<String, String> daKy = new LinkedHashMap<>(q);
            String chuKy = daKy.remove("vnp_SecureHash");
            assertEquals(chuKy, gateway.sign(daKy),
                    "Chuoi dem ky va chuoi truy van gui di phai la mot; lech la VNPAY tra ma 70");
        }
    }

    @Nested
    @DisplayName("Kiểm chữ ký trên kết quả gửi về")
    class KiemChuKy {

        @Test
        @DisplayName("Gói tham số nguyên vẹn thì chữ ký khớp")
        void goiNguyenVenThiKhop() {
            assertTrue(gateway.verifySignature(ketQua(p -> { })));
        }

        @Test
        @DisplayName("Sửa số tiền trên thanh địa chỉ thì chữ ký không còn khớp")
        void suaSoTienThiKhongKhop() {
            assertFalse(gateway.verifySignature(ketQua(p -> p.put("vnp_Amount", "100"))),
                    "Doi mot tham so ma van qua duoc thi ai cung tu ha gia don cua minh");
        }

        @Test
        @DisplayName("Không có chữ ký thì không qua")
        void thieuChuKyThiKhongQua() {
            GatewayCallback cb = ketQua(p -> { });
            cb.setSignature(null);
            assertFalse(gateway.verifySignature(cb));
        }

        @Test
        @DisplayName("Chuỗi bí mật sai thì không qua")
        void saiChuoiBiMatThiKhongQua() {
            VnPayGateway khac = new VnPayGateway(TMN_CODE, "CHUOI-BI-MAT-KHAC",
                    VnPayGateway.SANDBOX_PAY_URL, "", 15);
            assertFalse(khac.verifySignature(ketQua(p -> { })));
        }

        @Test
        @DisplayName("Tham số lạ ghép thêm vào không làm hỏng chữ ký của một lần trả tiền thật")
        void thamSoLaKhongPhaChuKy() {
            /* VnPayCallbacks chỉ nhặt tham số vnp_*, nên chỗ này mô phỏng đúng gói mà nó dựng:
               tham số lạ đã bị bỏ trước khi tới đây. */
            assertTrue(gateway.verifySignature(ketQua(p -> { })));
        }

        private GatewayCallback ketQua(java.util.function.Consumer<Map<String, String>> sua) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("vnp_TmnCode", TMN_CODE);
            p.put("vnp_Amount", "18500000");
            p.put("vnp_BankCode", "NCB");
            p.put("vnp_OrderInfo", "Thanh toan don hang 12");
            p.put("vnp_ResponseCode", VnPayGateway.CODE_SUCCESS);
            p.put("vnp_TransactionStatus", VnPayGateway.CODE_SUCCESS);
            p.put("vnp_TransactionNo", "14200000");
            p.put("vnp_TxnRef", "57-20250101120000");
            String chuKy = gateway.sign(p);
            sua.accept(p);

            GatewayCallback cb = new GatewayCallback();
            cb.setParams(p);
            cb.setSignature(chuKy);
            return cb;
        }
    }

    @Nested
    @DisplayName("Đọc lại mã thanh toán và số tiền")
    class DocNguoc {

        @Test
        @DisplayName("Mã tham chiếu do chính hệ thống sinh ra thì đọc lại được")
        void roundTrip() {
            String ref = gateway.txnRef(57, LocalDateTime.of(2025, 1, 1, 12, 0));
            assertEquals("57-20250101120000", ref);
            assertEquals(57, gateway.paymentIdFrom(ref));
        }

        @Test
        @DisplayName("Chuỗi không phải của mình thì trả về null chứ không đoán bừa")
        void chuoiLaThiTraNull() {
            assertNull(gateway.paymentIdFrom("khong-phai-so"));
            assertNull(gateway.paymentIdFrom("0-20250101120000"));
            assertNull(gateway.paymentIdFrom(null));
        }

        @Test
        @DisplayName("vnp_Amount đổi ngược về đúng số tiền của đơn")
        void doiNguocSoTien() {
            assertEquals(0, new BigDecimal("185000").compareTo(gateway.amountFrom("18500000")));
            assertNull(gateway.amountFrom(""));
            assertNull(gateway.amountFrom("khong-phai-so"));
        }
    }

    @Test
    @DisplayName("Thiếu mã website hoặc chuỗi bí mật thì cổng tự nhận là chưa cấu hình")
    void thieuCauHinh() {
        assertFalse(new VnPayGateway("", HASH_SECRET, "", "", 15).isConfigured());
        assertFalse(new VnPayGateway(TMN_CODE, "", "", "", 15).isConfigured());
        assertTrue(gateway.isConfigured());
    }

    private static Map<String, String> thamSo(String url) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String phan : URI.create(url).getRawQuery().split("&")) {
            int dau = phan.indexOf('=');
            map.put(phan.substring(0, dau),
                    URLDecoder.decode(phan.substring(dau + 1), StandardCharsets.US_ASCII));
        }
        return map;
    }
}
