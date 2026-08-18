package com.fastfood.payment;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PaymentInitResult;
import com.fastfood.integration.payment.SePayGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Cổng thanh toán SePay")
class SePayGatewayTest {

    private static final String API_KEY = "khoa-bi-mat-cua-toi";

    private final SePayGateway gateway =
            new SePayGateway("0011223344", "Vietcombank", "CUA HANG ABC", API_KEY, "FF");

    @Nested
    @DisplayName("Đọc mã thanh toán từ nội dung chuyển khoản")
    class DocMa {

        @Test
        @DisplayName("Nội dung do chính hệ thống sinh ra thì đọc lại được")
        void roundTrip() {
            assertEquals(57, gateway.paymentIdFrom(gateway.transferContent(57)));
        }

        @Test
        @DisplayName("Đọc được khi ngân hàng chèn thêm chữ quanh mã")
        void readsFromRealBankContent() {
            assertEquals(57, gateway.paymentIdFrom("SEVN63DC8E5C FF57 chuyen tien"));
            assertEquals(57, gateway.paymentIdFrom("NGUYEN VAN A chuyen tien FF57"));
            assertEquals(1234, gateway.paymentIdFrom("ff1234"),
                    "Nhiều ngân hàng viết hoa hoặc viết thường lại toàn bộ nội dung");
        }

        @Test
        @DisplayName("Không nhận nhầm mã nằm lọt giữa một chuỗi khác")
        void doesNotMatchInsideAnotherToken() {
            assertNull(gateway.paymentIdFrom("SEVNFF12ABCD chuyen tien"));
            assertNull(gateway.paymentIdFrom("REF9FF12"));
        }

        @Test
        @DisplayName("Không nhận mã dính liền chữ số phía sau")
        void doesNotTruncateALongerNumber() {
            assertEquals(5712, gateway.paymentIdFrom("FF5712"));
        }

        @Test
        @DisplayName("Tiền chuyển vào không mang mã nào thì trả về rỗng, không phải lỗi")
        void unrelatedTransferHasNoPaymentId() {
            assertNull(gateway.paymentIdFrom("NGUYEN VAN B chuyen tien an trua"));
            assertNull(gateway.paymentIdFrom("FF"), "Thiếu phần số thì không phải mã");
            assertNull(gateway.paymentIdFrom("FF0"), "Mã thanh toán bắt đầu từ 1");
            assertNull(gateway.paymentIdFrom(null));
        }

        @Test
        @DisplayName("Dãy số dài bất thường không bị hiểu thành mã")
        void absurdlyLongNumberIsRejected() {
            assertNull(gateway.paymentIdFrom("FF99999999999999999999"),
                    "Không chặn thì con số này tràn kiểu int và thành một mã hợp lệ nào đó");
        }
    }

    @Nested
    @DisplayName("Xác thực lệnh gọi về")
    class XacThuc {

        @Test
        @DisplayName("Đúng khoá thì nhận")
        void acceptsCorrectKey() {
            assertTrue(gateway.verifySignature(callbackWithKey(API_KEY)));
        }

        @Test
        @DisplayName("Sai khoá, thiếu khoá, hoặc khoá gần đúng đều bị từ chối")
        void rejectsEverythingElse() {
            assertFalse(gateway.verifySignature(callbackWithKey("khoa-khac")));
            assertFalse(gateway.verifySignature(callbackWithKey(API_KEY + "x")));
            assertFalse(gateway.verifySignature(callbackWithKey(API_KEY.substring(0, 5))));
            assertFalse(gateway.verifySignature(callbackWithKey("")));
            assertFalse(gateway.verifySignature(callbackWithKey(null)));
        }

        @Test
        @DisplayName("Chưa cấu hình khoá thì từ chối tất, kể cả lệnh gọi không mang khoá")
        void rejectsAllWhenNoKeyConfigured() {
            SePayGateway chuaCauHinh =
                    new SePayGateway("0011223344", "Vietcombank", "CUA HANG ABC", "", "FF");

            assertFalse(chuaCauHinh.verifySignature(callbackWithKey(null)));
            assertFalse(chuaCauHinh.verifySignature(callbackWithKey("")));
            assertFalse(chuaCauHinh.verifySignature(callbackWithKey("bat-ky-thu-gi")));
            assertFalse(chuaCauHinh.isConfigured(),
                    "Thiếu khoá thì chưa đủ điều kiện thu tiền thật");
        }

        private GatewayCallback callbackWithKey(String key) {
            GatewayCallback cb = new GatewayCallback();
            cb.setSignature(key);
            return cb;
        }
    }

    @Nested
    @DisplayName("Mã QR và trang thanh toán")
    class MaQR {

        @Test
        @DisplayName("Địa chỉ ảnh QR mang đủ tài khoản, số tiền và nội dung")
        void qrUrlCarriesEverythingTheBankAppNeeds() {
            String url = gateway.qrImageUrl(new BigDecimal("150000.00"), gateway.transferContent(57));

            assertTrue(url.startsWith("https://qr.sepay.vn/img?"), "Nhận được: " + url);
            assertTrue(url.contains("acc=0011223344"), "Nhận được: " + url);
            assertTrue(url.contains("bank=Vietcombank"), "Nhận được: " + url);
            assertTrue(url.contains("des=FF57"), "Nhận được: " + url);
            assertTrue(url.contains("amount=150000"),
                    "Ứng dụng ngân hàng nhận số tiền nguyên đồng, không nhận phần thập phân. "
                            + "Nhận được: " + url);
        }

        @Test
        @DisplayName("Khởi tạo thanh toán dẫn về trang QR trong chính ứng dụng")
        void initiateStaysInsideTheApplication() {
            PaymentInitResult init =
                    gateway.initiate(57, 12, new BigDecimal("150000.00"), "http://localhost:8080/fastfood");

            assertEquals("http://localhost:8080/fastfood/payment/sepay?paymentId=57",
                    init.getRedirectUrl());
            assertEquals("FF57", init.getExternalTransactionId());
        }

        @Test
        @DisplayName("Địa chỉ trả ra ghép thêm được tham số, vì nơi gọi còn gắn mã đơn vào")
        void redirectUrlAcceptsAnExtraParameter() {
            String url = gateway.initiate(57, 12, new BigDecimal("150000"), "http://x").getRedirectUrl()
                    + "&orderId=12";

            assertEquals("http://x/payment/sepay?paymentId=57&orderId=12", url,
                    "PaymentStartServlet nối chuỗi \"&orderId=\" vào sau, nên địa chỉ trả ra "
                            + "bắt buộc phải đã có dấu hỏi");
        }
    }
}
