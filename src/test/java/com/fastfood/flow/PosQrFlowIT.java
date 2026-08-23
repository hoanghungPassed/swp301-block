package com.fastfood.flow;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.fastfood.model.dto.Dtos.PosLine;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.service.shared.PaymentService;
import com.fastfood.service.shared.ScheduleService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.FakePayOs;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Khách quét mã QR trả tiền tại quầy")
class PosQrFlowIT extends IntegrationTestBase {

    private final StaffOrderService staffOrders = new StaffOrderService();
    private final ScheduleService schedule = new ScheduleService();
    /* Cổng PayOS thật trên một đường truyền giả — xem FakePayOs. Không đòi mạng, không đòi
       khoá thật, mà phần dựng mã đơn và phần đọc kết quả về vẫn là mã sản phẩm. */
    private final FakePayOs payos = new FakePayOs();
    private final PayOsGateway gateway = payos.gateway();
    private final PaymentService paymentService = new PaymentService(gateway);

    private List<PosLine> oneItem() {
        return List.of(new PosLine(anyOrderableProductId(), 2));
    }

    private int paymentIdOf(int orderId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 payment_id FROM dbo.Payment WHERE order_id = ? ORDER BY attempt_no DESC",
                orderId);
        assertNotNull(id, "Đơn phải có khoản thu");
        return id;
    }

    private int posTerminalTransactions(int orderId) {
        return count("SELECT COUNT(*) FROM dbo.PaymentTransaction t " +
                "JOIN dbo.Payment p ON p.payment_id = t.payment_id " +
                "WHERE p.order_id = ? AND t.gateway = 'POS_TERMINAL'", orderId);
    }

    @Test
    @DisplayName("Bấm quét mã QR: đơn có mã nhưng bếp chưa thấy, tiền còn đang chờ")
    void qrOrderWaitsForMoneyBeforeReachingKitchen() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());

        assertEquals("CONFIRMED", order.getOrderStatus());
        assertEquals("POS", order.getOrderSource());
        assertNull(order.getReleasedToKdsAt(),
                "Chưa thu được tiền thì bếp chưa được làm món");
        assertEquals("PENDING", text("SELECT payment_status FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()));
        assertEquals("ONLINE_GATEWAY", text("SELECT method FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()));
        assertEquals(0, posTerminalTransactions(order.getOrderId()),
                "Chưa ai xác nhận tiền thì chưa có gì để đối soát");
    }

    @Test
    @DisplayName("Thu ngân bấm Xong: tiền được ghi nhận và đơn xuống bếp trong một nhịp")
    void doneRecordsMoneyAndReleasesToKitchen() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());

        Order done = staffOrders.confirmQrPayment(order.getOrderId(), userId(CASHIER_1));

        assertNotNull(done.getReleasedToKdsAt(), "Bấm Xong rồi thì bếp phải thấy đơn");
        assertEquals("PAID", text("SELECT payment_status FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()));
        assertEquals(1, posTerminalTransactions(order.getOrderId()),
                "Lần xác nhận bằng tay phải để lại dấu vết đối soát");
        assertEquals("POS-QR-" + paymentIdOf(order.getOrderId()),
                text("SELECT t.external_transaction_id FROM dbo.PaymentTransaction t " +
                     "JOIN dbo.Payment p ON p.payment_id = t.payment_id WHERE p.order_id = ?",
                     order.getOrderId()));
    }

    @Test
    @DisplayName("Cổng đã báo tiền về: bấm Xong chỉ mở đường xuống bếp, không ghi đè dấu vết của cổng")
    void moneyConfirmedByGatewayIsNotOverwritten() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        exec("UPDATE dbo.Payment SET payment_status = 'PAID', paid_at = SYSDATETIME() WHERE order_id = ?",
                order.getOrderId());

        Order done = staffOrders.confirmQrPayment(order.getOrderId(), userId(CASHIER_1));

        assertNotNull(done.getReleasedToKdsAt());
        assertEquals(0, posTerminalTransactions(order.getOrderId()),
                "Tiền do cổng báo về đã có giao dịch của cổng; thêm một dòng POS_TERMINAL nữa là "
                        + "đối soát đếm thành hai lần thu");
    }

    @Test
    @DisplayName("Bấm Xong hai lần không thu tiền hai lần")
    void confirmingTwiceChangesNothing() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        staffOrders.confirmQrPayment(order.getOrderId(), userId(CASHIER_1));
        staffOrders.confirmQrPayment(order.getOrderId(), userId(CASHIER_1));

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Payment WHERE order_id = ? " +
                "AND payment_status = 'PAID'", order.getOrderId()));
        assertEquals(1, posTerminalTransactions(order.getOrderId()));
    }

    @Test
    @DisplayName("Đơn đã hết hiệu lực thì không xác nhận tiền được nữa")
    void expiredOrderCannotBeConfirmed() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        exec("UPDATE dbo.Orders SET created_at = DATEADD(HOUR, -2, SYSDATETIME()) WHERE order_id = ?",
                order.getOrderId());
        schedule.expireAbandonedCounterOrders();

        BusinessException e = assertThrows(BusinessException.class,
                () -> staffOrders.confirmQrPayment(order.getOrderId(), userId(CASHIER_1)));

        assertTrue(e.getMessage().contains("EXPIRED"), "Thông báo phải nói rõ đơn đang ở đâu: "
                + e.getMessage());
        assertNull(scalar(java.time.LocalDateTime.class,
                "SELECT released_to_kds_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId()));
    }

    @Test
    @DisplayName("Khách bỏ đi giữa chừng: đơn treo được tác vụ nền dọn đi")
    void abandonedQrOrderIsCleanedUp() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        exec("UPDATE dbo.Orders SET created_at = DATEADD(HOUR, -2, SYSDATETIME()) WHERE order_id = ?",
                order.getOrderId());

        schedule.expireAbandonedCounterOrders();

        assertEquals("EXPIRED", text("SELECT order_status FROM dbo.Orders WHERE order_id = ?",
                order.getOrderId()));
        assertEquals("FAILED", text("SELECT payment_status FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()));
    }

    @Test
    @DisplayName("Đơn đã có tiền mà thu ngân chưa bấm Xong thì tác vụ nền không được đụng vào")
    void paidOrderIsNeverCleanedUp() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        exec("UPDATE dbo.Payment SET payment_status = 'PAID', paid_at = SYSDATETIME() WHERE order_id = ?",
                order.getOrderId());
        exec("UPDATE dbo.Orders SET created_at = DATEADD(HOUR, -2, SYSDATETIME()) WHERE order_id = ?",
                order.getOrderId());

        schedule.expireAbandonedCounterOrders();

        assertEquals("CONFIRMED", text("SELECT order_status FROM dbo.Orders WHERE order_id = ?",
                order.getOrderId()),
                "Trong đơn này có tiền thật của khách, phải để người xử lý chứ không tự đóng");
    }

    @Test
    @DisplayName("Cổng báo tiền về trước khi thu ngân bấm Xong: ghi nhận tiền nhưng bếp vẫn chưa thấy đơn")
    void gatewayMoneyWaitsForTheCashier() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());

        assertEquals(PaymentService.CallbackResult.PAID,
                paymentService.handleCallback(callbackFor(order)));

        assertEquals("PAID", text("SELECT payment_status FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()));
        assertEquals("CONFIRMED", text("SELECT order_status FROM dbo.Orders WHERE order_id = ?",
                order.getOrderId()));
        assertNull(scalar(java.time.LocalDateTime.class,
                "SELECT released_to_kds_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId()),
                "Tiền về là điều kiện cần, còn quyết định đưa xuống bếp vẫn là của thu ngân");
    }

    @Test
    @DisplayName("Tiền về sau khi đơn đã hết hiệu lực: giữ nguyên PAID và để lại vết đối soát")
    void moneyArrivingAfterExpiryIsFlaggedForReconciliation() {
        Order order = staffOrders.createPosQrOrder(userId(CASHIER_1), oneItem());
        exec("UPDATE dbo.Orders SET created_at = DATEADD(HOUR, -2, SYSDATETIME()) WHERE order_id = ?",
                order.getOrderId());
        schedule.expireAbandonedCounterOrders();

        assertEquals(PaymentService.CallbackResult.ORDER_GONE,
                paymentService.handleCallback(callbackFor(order)));

        assertEquals("PAID", text("SELECT payment_status FROM dbo.Payment WHERE order_id = ?",
                order.getOrderId()),
                "Bộ hẹn giờ đã đóng khoản thu này là FAILED, nhưng rồi tiền về thật. Để nguyên "
                + "FAILED nghĩa là sổ sách nói không thu được trong khi sao kê ngân hàng nói có");
        assertNotNull(scalar(java.time.LocalDateTime.class,
                "SELECT paid_at FROM dbo.Payment WHERE order_id = ?", order.getOrderId()));
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'PAYMENT' " +
                        "AND entity_id = ? AND action = 'PAYMENT_ORPHANED'",
                        String.valueOf(paymentIdOf(order.getOrderId()))),
                "Không hoàn tự động được thì ít nhất phải tra ra được khoản tiền này");
    }

    /**
     * Một lần PayOS báo tiền về cho đơn tại quầy.
     *
     * <p>Đánh dấu {@code trusted} vì đây mô phỏng đường khách quay lại: PayOS không ký các
     * tham số trên địa chỉ quay về, nên đường ấy tự gọi ngược sang PayOS hỏi trạng thái thật
     * rồi mới ghi — xem PayOsReturnServlet.
     */
    private GatewayCallback callbackFor(Order order) {
        int paymentId = paymentIdOf(order.getOrderId());
        BigDecimal amount = money("SELECT amount FROM dbo.Payment WHERE payment_id = ?", paymentId);
        long orderCode = gateway.orderCode(paymentId);

        GatewayCallback cb = new GatewayCallback();
        cb.setTrusted(true);
        cb.setPaymentId(paymentId);
        /* Mã giao dịch phải khác nhau giữa các đơn: bảng giao dịch có ràng buộc duy nhất trên
           cột này, nên dùng chung một hằng số thì đơn thứ hai trong cùng lần chạy bị coi là
           gọi trùng. Mã đơn đã đủ duy nhất rồi. */
        cb.setExternalTransactionId("PAYOS-TF" + orderCode);
        cb.setSuccess(true);
        cb.setAmount(amount);
        cb.setRawPayload("{\"orderCode\":" + orderCode + "}");
        return cb;
    }
}
