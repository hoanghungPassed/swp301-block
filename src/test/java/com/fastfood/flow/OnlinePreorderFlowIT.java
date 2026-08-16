package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.MockPaymentGateway;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.service.shared.PaymentService;
import com.fastfood.service.shared.ScheduleService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toàn bộ vòng đời một đơn đặt trước, chạy thật qua tầng Service.
 * <p>
 * Đây là bài test nói lên giá trị cốt lõi của hệ thống: đơn đã thanh toán vẫn <b>nằm chờ</b>,
 * chỉ tới sát giờ hẹn mới xuống bếp. Nếu chỗ này hỏng thì món ra sớm và nguội, còn cả bộ chỉ
 * số đúng hẹn mất ý nghĩa.
 */
@DisplayName("Vòng đời đơn đặt trước, từ giỏ hàng tới lúc giao món")
class OnlinePreorderFlowIT extends IntegrationTestBase {

    private final CartService cartService = new CartService();
    private final CustomerOrderService customerOrders = new CustomerOrderService();
    private final StaffOrderService staffOrders = new StaffOrderService();
    private final PaymentService paymentService = new PaymentService();
    private final ScheduleService scheduleService = new ScheduleService();
    private final KitchenService kitchenService = new KitchenService();

    private int customerId;

    @BeforeEach
    void freshCustomer() {
        customerId = userId(CUSTOMER_2);
        // Mỗi lúc chỉ cho một đơn chờ thanh toán, nên phải dọn đơn dở của bài trước.
        exec("UPDATE dbo.Orders SET order_status = 'EXPIRED', expired_at = ? " +
             "WHERE customer_id = ? AND order_status = 'PENDING_PAYMENT'", LocalDateTime.now(), customerId);
        exec("DELETE ci FROM dbo.CartItem ci JOIN dbo.Cart c ON c.cart_id = ci.cart_id WHERE c.user_id = ?",
             customerId);
    }

    // ------------------------------------------------------------------ luồng đầy đủ

    @Test
    @DisplayName("Đi hết một vòng: đặt → trả tiền → chờ → xuống bếp → xong → giao món")
    void fullHappyPath() {
        Order order = placeAndPay();

        // --- sau khi trả tiền: đã xác nhận, có mã nhận hàng, NHƯNG bếp chưa thấy ---
        assertEquals("CONFIRMED", statusOf(order.getOrderId()));
        assertNotNull(pickupCodeOf(order.getOrderId()), "Trả tiền xong phải có mã nhận hàng");
        assertNull(releasedAtOf(order.getOrderId()),
                "Đây là điểm cốt lõi: đơn đã trả tiền vẫn phải nằm chờ, chưa được xuống bếp");

        LocalDateTime plannedRelease = scalar(LocalDateTime.class,
                "SELECT kitchen_release_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId());
        LocalDateTime pickup = scalar(LocalDateTime.class,
                "SELECT pickup_time FROM dbo.Orders WHERE order_id = ?", order.getOrderId());
        assertEquals(pickup.minusMinutes(20), plannedRelease,
                "Kế hoạch vào bếp phải bằng giờ hẹn trừ thời gian chuẩn bị (BR-08)");

        // --- tới giờ: bộ hẹn giờ đưa xuống bếp ---
        dueNow(order.getOrderId());
        assertTrue(scheduleService.releaseDueOrders() >= 1);
        assertNotNull(releasedAtOf(order.getOrderId()), "Tới giờ thì bếp phải thấy đơn");

        // --- bếp làm món ---
        OrderItem item = staffOrders.findById(order.getOrderId()).getItems().get(0);
        kitchenService.claim(item.getOrderItemId(), userId(KITCHEN_1));
        assertEquals("PREPARING", statusOf(order.getOrderId()),
                "Món đầu tiên được nhận thì cả đơn chuyển sang đang làm — do hệ thống tự suy ra (BR-11)");

        boolean orderBecameReady = kitchenService.markReady(item.getOrderItemId(), userId(KITCHEN_1));
        assertTrue(orderBecameReady, "Món cuối xong thì cả đơn phải sẵn sàng");
        assertEquals("READY", statusOf(order.getOrderId()));
        assertNotNull(scalar(LocalDateTime.class,
                "SELECT ready_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId()),
                "ready_at là mẫu số của chỉ số đúng hẹn, thiếu là hỏng cả báo cáo");

        // --- bàn giao hai đầu: bếp đưa món ra quầy, quầy xác nhận đã cầm ---
        // Tách làm hai bước là có lý do: mỗi bên tự xác nhận phần việc của mình, nên khi phát
        // hiện thiếu món thì biết ngay món dừng lại ở đâu.
        kitchenService.handOverToCounter(item.getOrderItemId(), userId(KITCHEN_1));
        staffOrders.receiveAtCounter(item.getOrderItemId(), userId(CASHIER_1));
        assertEquals("READY", statusOf(order.getOrderId()),
                "Cầm món từ bếp không đổi trạng thái đơn — món vẫn chưa ra khỏi cửa hàng");

        // --- giao món cho khách ---
        String code = pickupCodeOf(order.getOrderId());
        staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), code);

        assertEquals("COMPLETED", statusOf(order.getOrderId()));
        assertNotNull(scalar(LocalDateTime.class,
                "SELECT picked_up_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId()));
        assertEquals(userId(CASHIER_1), (int) scalar(Integer.class,
                "SELECT handoff_by_user_id FROM dbo.Orders WHERE order_id = ?", order.getOrderId()),
                "Phải truy được ai đã đưa món ra khỏi cửa hàng (BR-16)");
    }

    // ------------------------------------------------------------------ chống trùng

    @Test
    @DisplayName("Cổng thanh toán gửi kết quả về hai lần: không thu tiền hai lần (NFR-06)")
    void duplicateCallbackIsIgnored() {
        int productId = anyOrderableProductId();
        cartService.addProduct(customerId, productId, 1);
        Order order = customerOrders.createOnlineOrder(customerId, safePickupTime(), "idem-" + System.nanoTime());

        Callback cb = startPayment(order.getOrderId());

        assertEquals(PaymentService.CallbackResult.PAID, paymentService.handleCallback(cb.toGateway()));
        assertEquals(PaymentService.CallbackResult.DUPLICATE, paymentService.handleCallback(cb.toGateway()),
                "Lần gửi thứ hai phải bị nhận ra và bỏ qua");

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Payment WHERE order_id = ? AND payment_status = 'PAID'",
                order.getOrderId()), "Không được sinh thêm khoản thu");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.PaymentTransaction WHERE external_transaction_id = ?",
                cb.externalId), "Một mã giao dịch chỉ ghi được đúng một lần");
    }

    /**
     * Với cổng thu bằng chuyển khoản, số tiền do chính khách gõ vào ứng dụng ngân hàng: mã QR
     * điền sẵn 200.000đ không ngăn được ai đó sửa thành 10.000đ rồi chuyển với đúng nội dung
     * ấy. Nếu "đã có tiền về" là đủ để xác nhận đơn thì đó là cách mua hàng giá bao nhiêu
     * cũng được.
     */
    @Test
    @DisplayName("Tiền về không đúng số tiền của đơn thì đơn không được xác nhận")
    void wrongAmountDoesNotConfirmTheOrder() {
        cartService.addProduct(customerId, anyOrderableProductId(), 1);
        Order order = customerOrders.createOnlineOrder(customerId, safePickupTime(),
                "idem-lech-" + System.nanoTime());

        Callback dung = startPayment(order.getOrderId());
        Callback thieu = dung.withAmount(order.getOrderId(), new BigDecimal("10000"));

        assertEquals(PaymentService.CallbackResult.AMOUNT_MISMATCH,
                paymentService.handleCallback(thieu.toGateway()));

        assertEquals("PENDING_PAYMENT", statusOf(order.getOrderId()),
                "Đơn phải nằm nguyên ở chỗ cũ và hết hiệu lực theo bộ hẹn giờ như mọi đơn "
                        + "không ai trả tiền");
        assertEquals(0, count("SELECT COUNT(*) FROM dbo.Payment WHERE order_id = ? "
                        + "AND payment_status = 'PAID'", order.getOrderId()),
                "Không được ghi nhận đồng nào");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.PaymentTransaction "
                        + "WHERE external_transaction_id = ? AND status = 'MISMATCH'", thieu.externalId),
                "Khoản tiền vẫn phải nằm lại trong sổ đối soát, và nằm dưới đúng tên của nó: "
                        + "tiền đã thật sự rời tài khoản khách, chỉ là không đúng số");

        // Trả đúng số tiền sau đó vẫn xác nhận được đơn như thường
        assertEquals(PaymentService.CallbackResult.PAID, paymentService.handleCallback(dung.toGateway()));
        assertEquals("CONFIRMED", statusOf(order.getOrderId()));
    }

    @Test
    @DisplayName("Khách bấm đặt hàng hai lần chỉ tạo một đơn (NFR-07)")
    void doubleSubmitCreatesOneOrder() {
        cartService.addProduct(customerId, anyOrderableProductId(), 1);
        String key = "idem-double-" + System.nanoTime();
        LocalDateTime pickup = safePickupTime();

        Order first = customerOrders.createOnlineOrder(customerId, pickup, key);
        Order second = customerOrders.createOnlineOrder(customerId, pickup, key);

        assertEquals(first.getOrderId(), second.getOrderId(),
                "Bấm hai lần phải trả về cùng một đơn, không phải hai đơn");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Orders WHERE idempotency_key = ?", key));
    }

    @Test
    @DisplayName("Bộ hẹn giờ chạy lại không đưa đơn xuống bếp lần nữa (NFR-05)")
    void schedulerIsIdempotent() {
        Order order = placeAndPay();
        dueNow(order.getOrderId());

        scheduleService.releaseDueOrders();
        LocalDateTime firstRelease = releasedAtOf(order.getOrderId());

        scheduleService.releaseDueOrders();
        LocalDateTime afterSecondRun = releasedAtOf(order.getOrderId());

        assertEquals(firstRelease, afterSecondRun,
                "Chạy lại không được ghi đè thời điểm đã đưa xuống bếp");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.AuditLog " +
                        "WHERE entity_type = 'ORDER' AND entity_id = ? AND action = 'KDS_RELEASE'",
                String.valueOf(order.getOrderId())),
                "Chỉ được ghi đúng một lần đưa xuống bếp trong nhật ký");
    }

    @Test
    @DisplayName("Tin báo món sẵn sàng chỉ gửi một lần")
    void readyNotificationIsSentOnce() {
        Order order = readyOrder();

        // Gọi lại việc tổng hợp trạng thái: mọi lần sau đều không được sinh thêm tin nhắn
        OrderItem item = staffOrders.findById(order.getOrderId()).getItems().get(0);
        assertThrows(BusinessException.class,
                () -> kitchenService.markReady(item.getOrderItemId(), userId(KITCHEN_1)),
                "Món đã xong rồi thì không đánh dấu xong lần nữa được");

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Notification " +
                        "WHERE order_id = ? AND event_type = 'ORDER_READY'", order.getOrderId()),
                "Khách không được nhận tin trùng");
    }

    // ------------------------------------------------------------------ điều kiện giao món

    @Test
    @DisplayName("Mã nhận hàng sai thì không giao món, và lần thử được ghi lại")
    void wrongPickupCodeIsRejected() {
        Order order = readyOrder();

        assertThrows(BusinessException.class,
                () -> staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), "SAI-MA"));

        assertEquals("READY", statusOf(order.getOrderId()), "Đơn phải giữ nguyên trạng thái");
        assertTrue(count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'ORDER' " +
                        "AND entity_id = ? AND action = 'PICKUP_VERIFY_FAILED'",
                String.valueOf(order.getOrderId())) >= 1,
                "Lần đưa sai mã phải để lại dấu vết");
    }

    @Test
    @DisplayName("Món còn trong bếp thì không giao được, dù đơn đã sẵn sàng và mã đúng")
    void cannotHandoffWhileFoodIsStillInTheKitchen() {
        Order order = readyButStillInKitchen();
        String code = pickupCodeOf(order.getOrderId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), code));

        assertTrue(e.getMessage().contains("chưa được nhận tại quầy"),
                "Đơn sẵn sàng chỉ nghĩa là bếp nấu xong; giao cho khách trước khi quầy cầm "
                + "được món là cách chắc chắn nhất để phát thiếu món. Thông báo: " + e.getMessage());
        assertEquals("READY", statusOf(order.getOrderId()));
    }

    @Test
    @DisplayName("Quầy cầm món từ bếp không làm đổi trạng thái đơn")
    void receivingAtCounterDoesNotChangeOrderStatus() {
        Order order = readyAtCounter();
        OrderItem item = staffOrders.findById(order.getOrderId()).getItems().get(0);

        staffOrders.receiveAtCounter(item.getOrderItemId(), userId(CASHIER_1));

        assertEquals("READY", statusOf(order.getOrderId()),
                "Đây là bàn giao nội bộ giữa bếp và quầy, khách chưa nhận được gì");
    }

    @Test
    @DisplayName("Cầm một món hai lần thì lần sau bị từ chối")
    void receivingTwiceIsRejected() {
        Order order = readyAtCounter();
        OrderItem item = staffOrders.findById(order.getOrderId()).getItems().get(0);

        staffOrders.receiveAtCounter(item.getOrderItemId(), userId(CASHIER_1));

        assertThrows(BusinessException.class,
                () -> staffOrders.receiveAtCounter(item.getOrderItemId(), userId(CASHIER_1)),
                "Bấm đúp không được ghi đè người đã cầm món");
    }

    @Test
    @DisplayName("Đơn chưa xong món thì không giao được dù có mã đúng")
    void cannotHandoffBeforeReady() {
        Order order = placeAndPay();
        String code = pickupCodeOf(order.getOrderId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), code));
        assertTrue(e.getMessage().contains("chưa sẵn sàng"), e.getMessage());
    }

    @Test
    @DisplayName("Bấm giao món hai lần không hoàn tất đơn hai lần")
    void handoffIsIdempotent() {
        Order order = readyOrder();
        String code = pickupCodeOf(order.getOrderId());

        staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), code);
        LocalDateTime firstPickup = scalar(LocalDateTime.class,
                "SELECT picked_up_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId());

        assertThrows(BusinessException.class,
                () -> staffOrders.handoff(order.getOrderId(), userId(CASHIER_1), code));

        assertEquals(firstPickup, scalar(LocalDateTime.class,
                "SELECT picked_up_at FROM dbo.Orders WHERE order_id = ?", order.getOrderId()),
                "Bấm đúp không được ghi đè thời điểm giao món");
    }

    @Test
    @DisplayName("Đơn đã hoàn tiền thì không giao món, và nói rõ lý do")
    void refundedOrderCannotBeHandedOff() {
        Order order = readyOrder();
        exec("UPDATE dbo.Payment SET payment_status = 'REFUNDED', refunded_at = ? WHERE order_id = ?",
             LocalDateTime.now(), order.getOrderId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> staffOrders.handoff(order.getOrderId(), userId(CASHIER_1),
                        pickupCodeOf(order.getOrderId())));

        assertTrue(e.getMessage().contains("hoàn tiền"),
                "Phải phân biệt 'chưa từng thu' với 'đã thu rồi hoàn': " + e.getMessage());
    }

    // ------------------------------------------------------------------ giờ hẹn

    @Test
    @DisplayName("Giờ hẹn quá gần thì bị từ chối (BR-05)")
    void pickupTimeTooSoonIsRejected() {
        cartService.addProduct(customerId, anyOrderableProductId(), 1);
        assertThrows(com.fastfood.common.exception.ValidationException.class,
                () -> customerOrders.createOnlineOrder(customerId, LocalDateTime.now().plusMinutes(5), null));
    }

    @Test
    @DisplayName("Giờ hẹn ngoài giờ mở cửa thì bị từ chối")
    void pickupTimeOutsideOpeningHoursIsRejected() {
        cartService.addProduct(customerId, anyOrderableProductId(), 1);
        LocalDateTime threeAm = LocalDateTime.now().toLocalDate().plusDays(1).atTime(3, 0);
        assertThrows(com.fastfood.common.exception.ValidationException.class,
                () -> customerOrders.createOnlineOrder(customerId, threeAm, null),
                "Không có ràng buộc này thì bộ hẹn giờ đẩy đơn xuống bếp lúc 2 giờ 40 sáng");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    /**
     * Giờ hẹn luôn hợp lệ bất kể test chạy lúc nào trong ngày: trưa hôm sau nằm gọn trong
     * giờ mở cửa và cách hiện tại thừa thời gian.
     */
    private static LocalDateTime safePickupTime() {
        return LocalDateTime.now().toLocalDate().plusDays(1).atTime(12, 0);
    }

    private Order placeAndPay() {
        cartService.addProduct(customerId, anyOrderableProductId(), 1);
        Order order = customerOrders.createOnlineOrder(customerId, safePickupTime(), "idem-" + System.nanoTime());
        Callback cb = startPayment(order.getOrderId());
        assertEquals(PaymentService.CallbackResult.PAID, paymentService.handleCallback(cb.toGateway()));
        return order;
    }

    /**
     * Đơn đã sẵn sàng và quầy đã cầm đủ món — tức là sẵn sàng giao cho khách.
     * <p>
     * Đi qua đúng bốn bước thật chứ không sửa thẳng trạng thái trong cơ sở dữ liệu: bếp làm
     * xong, bếp bàn giao ra quầy, quầy xác nhận đã cầm, rồi mới tới lượt khách.
     */
    private Order readyOrder() {
        Order order = readyAtCounter();
        for (OrderItem item : staffOrders.findById(order.getOrderId()).getItems()) {
            staffOrders.receiveAtCounter(item.getOrderItemId(), userId(CASHIER_1));
        }
        return order;
    }

    /** Bếp đã bàn giao món ra quầy nhưng thu ngân chưa xác nhận cầm. */
    private Order readyAtCounter() {
        Order order = readyButStillInKitchen();
        for (OrderItem item : staffOrders.findById(order.getOrderId()).getItems()) {
            kitchenService.handOverToCounter(item.getOrderItemId(), userId(KITCHEN_1));
        }
        return order;
    }

    /** Bếp đã nấu xong nhưng món vẫn còn trong bếp, chưa bàn giao ra quầy. */
    private Order readyButStillInKitchen() {
        Order order = placeAndPay();
        dueNow(order.getOrderId());
        scheduleService.releaseDueOrders();
        OrderItem item = staffOrders.findById(order.getOrderId()).getItems().get(0);
        kitchenService.claim(item.getOrderItemId(), userId(KITCHEN_1));
        kitchenService.markReady(item.getOrderItemId(), userId(KITCHEN_1));
        return order;
    }

    /** Kéo kế hoạch vào bếp về quá khứ để bộ hẹn giờ nhặt được ngay, khỏi phải chờ thật. */
    private void dueNow(int orderId) {
        exec("UPDATE dbo.Orders SET kitchen_release_at = ? WHERE order_id = ?",
             LocalDateTime.now().minusMinutes(1), orderId);
    }

    private Callback startPayment(int orderId) {
        String redirect = paymentService.startOnlinePayment(orderId, customerId, "http://test");
        Map<String, String> q = queryParams(redirect);
        return new Callback(Integer.parseInt(q.get("paymentId")), q.get("txnId"),
                new BigDecimal(q.get("amount")), q.get("sig"));
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> map = new HashMap<>();
        String query = URI.create(url).getQuery();
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            map.put(pair.substring(0, i), pair.substring(i + 1));
        }
        return map;
    }

    private static String statusOf(int orderId) {
        return text("SELECT order_status FROM dbo.Orders WHERE order_id = ?", orderId);
    }

    private static String pickupCodeOf(int orderId) {
        return text("SELECT pickup_code FROM dbo.Orders WHERE order_id = ?", orderId);
    }

    private static LocalDateTime releasedAtOf(int orderId) {
        return scalar(LocalDateTime.class,
                "SELECT released_to_kds_at FROM dbo.Orders WHERE order_id = ?", orderId);
    }

    /** Kết quả cổng thanh toán gửi về, dựng lại từ chính địa chỉ mà cổng trả ra. */
    private record Callback(int paymentId, String externalId, BigDecimal amount, String signature) {

        /**
         * Cùng lần thanh toán ấy nhưng cổng báo về một số tiền khác.
         * <p>
         * Ký lại bằng chính cổng chứ không sửa tay số tiền trong bản cũ: sửa tay thì chữ ký hỏng
         * và bài test chỉ chứng minh được rằng chữ ký hoạt động — điều đã có bài khác lo. Cái
         * cần dựng lại ở đây là một lệnh gọi về <b>hợp lệ về mọi mặt</b> mà số tiền vẫn không
         * khớp, đúng như khi khách sửa số tiền ngay trong ứng dụng ngân hàng.
         */
        Callback withAmount(int orderId, BigDecimal other) {
            String url = new MockPaymentGateway()
                    .initiate(paymentId, orderId, other, "http://test").getRedirectUrl();
            Map<String, String> q = queryParams(url);
            return new Callback(paymentId, q.get("txnId"), other, q.get("sig"));
        }

        GatewayCallback toGateway() {
            GatewayCallback cb = new GatewayCallback();
            cb.setPaymentId(paymentId);
            cb.setExternalTransactionId(externalId);
            cb.setSuccess(true);
            cb.setAmount(amount);
            cb.setSignature(signature);
            cb.setRawPayload("{\"test\":true}");
            return cb;
        }
    }
}
