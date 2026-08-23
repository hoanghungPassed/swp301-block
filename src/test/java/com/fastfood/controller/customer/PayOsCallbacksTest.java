package com.fastfood.controller.customer;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.fastfood.testsupport.FakePayOs;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Đọc kết quả PayOS gửi về")
class PayOsCallbacksTest {

    private static final BigDecimal SO_TIEN = new BigDecimal("185000");
    private static final String REFERENCE = "TF250101999";
    private static final long ORDER_CODE = 57L;

    private final FakePayOs payos = new FakePayOs();
    private final PayOsGateway gateway = payos.gateway();

    /** Dựng sẵn một liên kết đã trả tiền, để hai đường báo kết quả cùng nói về một giao dịch. */
    private JsonObject lienKetDaTra() {
        gateway.initiate(57, 12, SO_TIEN, "http://localhost:8080/fastfood");
        payos.traTien(ORDER_CODE, SO_TIEN, REFERENCE);
        return gateway.lookup(ORDER_CODE);
    }

    @Test
    @DisplayName("Hai đường báo kết quả dựng ra CÙNG một mã giao dịch")
    void haiDuongCungMaGiaoDich() {
        /* Đây là chốt chặn chống thu tiền hai lần. Webhook và lượt khách quay lại thường cùng
           báo về một lần trả tiền; chỉ khi hai bên dựng ra cùng một mã giao dịch thì lần thứ
           hai mới đụng ràng buộc duy nhất trên bảng giao dịch và bị bỏ qua. Dựng lệch nhau là
           cùng một khoản tiền được ghi nhận hai lần, và không có gì báo động. */
        GatewayCallback tuWebhook = PayOsCallbacks.fromWebhook(
                payos.webhook(ORDER_CODE, SO_TIEN, REFERENCE, true), gateway);
        GatewayCallback tuTraCuu = PayOsCallbacks.fromLookup(lienKetDaTra(), gateway);

        assertEquals(tuWebhook.getExternalTransactionId(), tuTraCuu.getExternalTransactionId());
        assertEquals("PAYOS-" + REFERENCE, tuWebhook.getExternalTransactionId());
    }

    @Nested
    @DisplayName("Từ webhook")
    class TuWebhook {

        private final GatewayCallback cb = PayOsCallbacks.fromWebhook(
                payos.webhook(ORDER_CODE, SO_TIEN, REFERENCE, true), gateway);

        @Test
        @DisplayName("Đọc ra đúng mã khoản thu, số tiền và kết quả")
        void docDungCacTruong() {
            assertEquals(57, cb.getPaymentId());
            assertEquals(0, SO_TIEN.compareTo(cb.getAmount()));
            assertTrue(cb.isSuccess());
        }

        @Test
        @DisplayName("Giữ nguyên khối data để còn kiểm lại được chữ ký")
        void giuNguyenKhoiData() {
            assertTrue(gateway.verifySignature(cb),
                    "Them bot mot truong giua luc doc va luc kiem la moi webhook that bi tu choi");
        }

        @Test
        @DisplayName("Không tin cậy sẵn: webhook phải qua cửa chữ ký")
        void khongTinCaySan() {
            assertFalse(cb.isTrusted(),
                    "Bat trusted o day la bo han cua chu ky, ma webhook thi ai cung POST vao duoc");
        }

        @Test
        @DisplayName("Giao dịch hỏng thì không được coi là đã trả, dù lời gọi webhook hợp lệ")
        void giaoDichHongThiKhongPhaiDaTra() {
            /* Mức ngoài (success) nói lời gọi webhook có hợp lệ không; mức trong (data.code) mới
               nói tiền có về không. Tin mỗi mức ngoài là ghi nhận một lần trả tiền hỏng. */
            JsonObject payload = payos.webhook(ORDER_CODE, SO_TIEN, REFERENCE, true);
            payload.getAsJsonObject("data").addProperty("code", "99");

            assertFalse(PayOsCallbacks.fromWebhook(payload, gateway).isSuccess());
        }
    }

    @Nested
    @DisplayName("Từ lần tự đi hỏi cổng")
    class TuTraCuu {

        @Test
        @DisplayName("Đánh dấu tin cậy vì dữ liệu đến từ lời gọi máy-với-máy của chính mình")
        void danhDauTinCay() {
            GatewayCallback cb = PayOsCallbacks.fromLookup(lienKetDaTra(), gateway);

            assertTrue(cb.isTrusted());
            assertTrue(cb.isSuccess());
            assertEquals(57, cb.getPaymentId());
        }

        @Test
        @DisplayName("Số tiền lấy ở amountPaid — số ngân hàng thật sự chuyển")
        void soTienLayOAmountPaid() {
            JsonObject link = lienKetDaTra();
            /* Trả thiếu: liên kết ghi 185.000 nhưng ngân hàng chỉ chuyển 10.000. Lấy nhầm sang
               trường amount thì hai số luôn khớp và nhánh đối soát tay không bao giờ chạy. */
            link.addProperty("amountPaid", 10000);

            assertEquals(0, new BigDecimal("10000")
                    .compareTo(PayOsCallbacks.fromLookup(link, gateway).getAmount()));
        }

        @Test
        @DisplayName("Còn đang chờ thì chưa ghi nhận gì cả")
        void conDangChoThiChuaGhiGi() {
            gateway.initiate(57, 12, SO_TIEN, "http://localhost:8080/fastfood");

            assertNull(PayOsCallbacks.fromLookup(gateway.lookup(ORDER_CODE), gateway),
                    "Ghi nhan luc con PENDING la dong mot khoan thu ma tien dang tren duong ve");
        }

        @Test
        @DisplayName("Khách huỷ thì ghi là hỏng, kèm mã giao dịch riêng của lần huỷ")
        void khachHuyThiGhiLaHong() {
            gateway.initiate(57, 12, SO_TIEN, "http://localhost:8080/fastfood");
            payos.dong(ORDER_CODE, "CANCELLED");

            GatewayCallback cb = PayOsCallbacks.fromLookup(gateway.lookup(ORDER_CODE), gateway);
            assertFalse(cb.isSuccess());
            assertEquals("PAYOS-link-57-CANCELLED", cb.getExternalTransactionId());
            assertNotEquals("PAYOS-" + REFERENCE, cb.getExternalTransactionId(),
                    "Trung ma voi lan tra tien that thi mot trong hai lan bi nuot mat");
        }

        @Test
        @DisplayName("Trạng thái lạ thì không đoán bừa")
        void trangThaiLaThiKhongDoan() {
            gateway.initiate(57, 12, SO_TIEN, "http://localhost:8080/fastfood");
            payos.dong(ORDER_CODE, "MOT_TRANG_THAI_MOI");

            /* Đoán "đã trả" là mất món; đoán "hỏng" là đóng nhầm một khoản còn sống. Để lượt
               webhook hoặc bộ hẹn giờ quá hạn xử lý. */
            assertNull(PayOsCallbacks.fromLookup(gateway.lookup(ORDER_CODE), gateway));
        }
    }
}
