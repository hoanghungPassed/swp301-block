package com.fastfood.payment;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.fastfood.integration.payment.PaymentInitResult;
import com.fastfood.testsupport.FakePayOs;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Cổng thanh toán PayOS")
class PayOsGatewayTest {

    private static final String BASE_URL = "http://localhost:8080/fastfood";
    private static final String RETURN_URL = BASE_URL + PayOsGateway.RETURN_PATH;

    private final FakePayOs payos = new FakePayOs();
    private final PayOsGateway gateway = payos.gateway();

    @Nested
    @DisplayName("Chữ ký")
    class ChuKy {

        /* Hai giá trị dưới đây tính TAY bằng HMAC-SHA256 ngoài Java, không lấy từ chính mã đang
           kiểm. Đây là chỗ duy nhất trong bộ test neo được vào thuật toán thật: mọi chỗ khác
           đều so chữ ký do cùng một hàm sinh ra, nên đổi sai khuôn chuỗi đem ký thì chúng vẫn
           xanh — chỉ có hai con số này đỏ lên. */

        @Test
        @DisplayName("Lời xin liên kết ký trên đúng năm trường, đúng thứ tự PayOS quy định")
        void chuKyLoiXinLienKet() {
            assertEquals("00bdba58d5e211f0c20cc178feb3f1a0ae0d61165dccb8b81014990bfb81ab82",
                    gateway.signPaymentRequest(185000, RETURN_URL, "Don hang 12", 57, RETURN_URL),
                    "Sai khuon chuoi ky thi PayOS tu choi moi lan mo cong, khong noi ro vi sao");
        }

        @Test
        @DisplayName("Kết quả gửi về ký trên toàn bộ khối data, khoá xếp theo bảng chữ cái")
        void chuKyKhoiData() {
            Map<String, String> data = new LinkedHashMap<>();
            /* Cố ý đưa vào theo thứ tự lộn xộn: hàm ký phải tự xếp lại, không được tin vào
               thứ tự nó nhận được. */
            data.put("reference", "TF250101999");
            data.put("orderCode", "57");
            data.put("amount", "185000");
            data.put("description", "Don hang 12");
            data.put("code", "00");
            data.put("counterAccountName", "");

            assertEquals("9c04af3127ddf611c7474058b2c742e6fec8f5fe0e5e119d85be04bfd81afd21",
                    gateway.signData(data));
        }

        @Test
        @DisplayName("Trường null thành chuỗi rỗng chứ không bị bỏ ra khỏi chuỗi ký")
        void nullThanhChuoiRong() {
            JsonObject data = new JsonObject();
            data.addProperty("code", "00");
            data.add("counterAccountName", com.google.gson.JsonNull.INSTANCE);

            Map<String, String> phang = PayOsGateway.flatten(data);
            assertEquals("", phang.get("counterAccountName"),
                    "Bo han truong null ra thi chu ky lech voi PayOS o dung nhung giao dich "
                    + "khong co thong tin nguoi chuyen");
            assertEquals(2, phang.size());
        }

        @Test
        @DisplayName("Số giữ nguyên chữ số PayOS gửi, không đọc thành số rồi in lại")
        void soGiuNguyenChuSo() {
            JsonObject data = new JsonObject();
            data.addProperty("amount", 3000);

            assertEquals("3000", PayOsGateway.flatten(data).get("amount"),
                    "In lai thanh 3000.0 la chu ky lech tren MOI webhook co so tien tron");
        }
    }

    @Nested
    @DisplayName("Kiểm chữ ký trên webhook")
    class KiemChuKy {

        private final JsonObject payload =
                payos.webhook(57, new BigDecimal("185000"), "TF250101999", true);

        @Test
        @DisplayName("Khối data nguyên vẹn thì chữ ký khớp")
        void nguyenVenThiKhop() {
            assertTrue(gateway.verifySignature(callback(payload, false)));
        }

        @Test
        @DisplayName("Sửa số tiền trong khối data thì chữ ký không còn khớp")
        void suaSoTienThiKhongKhop() {
            payload.getAsJsonObject("data").addProperty("amount", 1000);
            assertFalse(gateway.verifySignature(callback(payload, false)),
                    "Sua duoc so tien ma van qua nghia la ai cung tu ha gia don cua minh");
        }

        @Test
        @DisplayName("Không có chữ ký thì không qua")
        void thieuChuKyThiKhongQua() {
            payload.addProperty("signature", "");
            assertFalse(gateway.verifySignature(callback(payload, false)));
        }

        @Test
        @DisplayName("Sai checksum key thì không qua")
        void saiChecksumKeyThiKhongQua() {
            PayOsGateway khac = new PayOsGateway(FakePayOs.CLIENT_ID, FakePayOs.API_KEY,
                    "checksum-key-khac", "", 15, 0, payos);
            assertFalse(khac.verifySignature(callback(payload, false)));
        }

        @Test
        @DisplayName("Dữ liệu tự đi hỏi cổng thì không mang chữ ký nào, và cũng không cần")
        void duLieuTuHoiThiKhongCoChuKy() {
            /* Đường khách quay lại không có chữ ký để kiểm — PayOS không ký các tham số trên
               địa chỉ quay về. Chỗ dựa của nó là cờ trusted, và verifySignature phải từ chối
               thẳng thay vì lỡ tay chấp nhận một gói không chữ ký. */
            GatewayCallback cb = new GatewayCallback();
            cb.setTrusted(true);
            assertFalse(gateway.verifySignature(cb));
            assertTrue(cb.isTrusted());
        }

        private GatewayCallback callback(JsonObject payload, boolean trusted) {
            GatewayCallback cb = new GatewayCallback();
            cb.setParams(PayOsGateway.flatten(payload.getAsJsonObject("data")));
            cb.setSignature(PayOsGateway.text(payload, "signature"));
            cb.setTrusted(trusted);
            return cb;
        }
    }

    @Nested
    @DisplayName("Mở cổng thanh toán")
    class MoCong {

        @Test
        @DisplayName("Trả về địa chỉ trả tiền, mã liên kết và chuỗi VietQR của PayOS")
        void traVeDuThongTin() {
            PaymentInitResult init = gateway.initiate(57, 12, new BigDecimal("185000.00"), BASE_URL);

            assertEquals("https://pay.payos.vn/web/link-57", init.getRedirectUrl());
            assertEquals("link-57", init.getExternalTransactionId());
            assertNotNull(init.getQrContent());
            assertEquals(init.getQrContent(), init.qrPayload(),
                    "Man hinh quay ve mac dinh phai ve chuoi VietQR, khong ve dia chi web");
        }

        @Test
        @DisplayName("Mở lại cùng một khoản thu thì dùng lại liên kết cũ, không đẻ thêm cái mới")
        void moLaiThiDungLienKetCu() {
            PaymentInitResult lan1 = gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL);
            PaymentInitResult lan2 = gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL);

            assertEquals(lan1.getRedirectUrl(), lan2.getRedirectUrl());
            assertEquals(1, payos.soLanTaoLienKet(),
                    "Man hinh quay dung lai trang nay moi lan mo. De them lien ket moi la cung "
                    + "mot don co may cho tra tien con song, ma khong co duong hoan tien tu dong");
        }

        @Test
        @DisplayName("Liên kết cũ đã huỷ thì không dùng lại, và lỗi nói rõ cổng trả về gì")
        void lienKetDaHuyThiKhongDungLai() {
            gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL);
            payos.dong(57, "CANCELLED");

            BusinessException e = assertThrows(BusinessException.class,
                    () -> gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL));
            assertTrue(e.getMessage().contains("Đơn thanh toán đã tồn tại"),
                    "Cau bao loi phai mang loi cua PayOS ra, khong nuot mat: " + e.getMessage());
        }

        @Test
        @DisplayName("Liên kết cũ mang số tiền khác thì từ chối chứ không trả về nó")
        void lienKetLechTienThiTuChoi() {
            gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL);

            /* Cùng mã khoản thu nhưng số tiền khác: dấu hiệu mã đơn đang đụng vào dữ liệu của
               lần cài đặt trước. Trả về liên kết cũ là để khách trả số tiền của một đơn không
               còn tồn tại. */
            assertThrows(BusinessException.class,
                    () -> gateway.initiate(57, 12, new BigDecimal("99000"), BASE_URL));
        }

        @Test
        @DisplayName("Thiếu khoá thì hỏng ngay tại bước mở cổng, không âm thầm bỏ qua")
        void thieuKhoaThiHongNgay() {
            PayOsGateway chuaCauHinh =
                    new PayOsGateway("", FakePayOs.API_KEY, FakePayOs.CHECKSUM_KEY, "", 15, 0, payos);

            assertFalse(chuaCauHinh.isConfigured());
            assertThrows(BusinessException.class,
                    () -> chuaCauHinh.initiate(57, 12, new BigDecimal("185000"), BASE_URL));
            assertTrue(gateway.isConfigured());
        }
    }

    @Nested
    @DisplayName("Mã đơn gửi sang PayOS")
    class MaDon {

        @Test
        @DisplayName("Không đặt khoảng dịch thì mã đơn chính là mã khoản thu")
        void khongDichThiTrungMaKhoanThu() {
            assertEquals(57L, gateway.orderCode(57));
            assertEquals(57, gateway.paymentIdFrom(57L));
        }

        @Test
        @DisplayName("Đặt khoảng dịch thì cộng vào lúc gửi đi và trừ ra lúc đọc kết quả về")
        void dichThiCongVaoRoiTruRa() {
            PayOsGateway dich = payos.gateway(1000L);
            assertEquals(1057L, dich.orderCode(57));
            assertEquals(57, dich.paymentIdFrom(1057L));
        }

        @Test
        @DisplayName("Mã đơn của lần cài đặt khác thì trả về null chứ không đoán bừa")
        void maLaThiTraNull() {
            PayOsGateway dich = payos.gateway(1000L);
            assertNull(dich.paymentIdFrom(999L), "So nho hon khoang dich khong phai cua minh");
            assertNull(dich.paymentIdFrom(1000L), "Dung bang khoang dich thi ra ma khoan thu 0");
        }
    }

    @Nested
    @DisplayName("Tra cứu trạng thái")
    class TraCuu {

        @Test
        @DisplayName("Đọc được trạng thái và số tiền thật sự đã trả")
        void docDuocTrangThai() {
            gateway.initiate(57, 12, new BigDecimal("185000"), BASE_URL);
            payos.traTien(57, new BigDecimal("185000"), "TF250101999");

            JsonObject link = gateway.lookup(57);
            assertEquals("PAID", PayOsGateway.text(link, "status"));
            assertEquals(185000L, PayOsGateway.number(link, "amountPaid"));
        }

        @Test
        @DisplayName("Mã đơn không có thật thì trả về null chứ không ném lỗi")
        void maKhongCoThatThiTraNull() {
            /* Ai cũng gõ tay được một orderCode vào đường quay lại. Ném lỗi ở đây là biến một
               địa chỉ bịa thành trang lỗi 500. */
            assertNull(gateway.lookup(999999));
        }
    }

    @Test
    @DisplayName("Số tiền gửi sang PayOS là số nguyên đồng")
    void soTienNguyenDong() {
        assertEquals(185000L, PayOsGateway.vndAmount(new BigDecimal("185000.00")));
    }

    @Test
    @DisplayName("Mô tả không quá 25 ký tự vì nó đi vào nội dung chuyển khoản")
    void moTaKhongQuaDai() {
        assertEquals("Don hang 12", PayOsGateway.description(12));
        assertTrue(PayOsGateway.description(1234567890).length() <= 25,
                "Ngan hang cat phan thua, ma chuoi bi cat thi khong con khop voi thu da ky");
    }

    @Test
    @DisplayName("Tên cổng đi vào bảng giao dịch là PAYOS")
    void tenCong() {
        assertEquals("PAYOS", gateway.getName());
    }
}
