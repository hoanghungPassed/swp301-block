package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Quy tắc huỷ đơn (BR-12).
 * <p>
 * Mốc chặn là <b>bếp đã bắt đầu làm hay chưa</b>, không phải <b>đơn đã xuống bếp hay chưa</b>.
 * Hai thời điểm này cách nhau 20 phút, và suốt khoảng đó có thể chưa đầu bếp nào nhận việc —
 * từ chối huỷ lúc ấy là bắt khách trả tiền cho một món chưa ai động tới.
 */
@DisplayName("Khách huỷ đơn: chặn theo việc bếp đã bắt tay chưa")
class CancelRuleIT extends IntegrationTestBase {

    private final CustomerOrderService customerOrders = new CustomerOrderService();
    private final StaffOrderService staffOrders = new StaffOrderService();
    private final KitchenService kitchenService = new KitchenService();

    @Test
    @DisplayName("Đơn đã xuống bếp nhưng chưa ai nhận việc: vẫn huỷ được")
    void releasedButUntouchedOrderCanStillBeCancelled() {
        Fixture f = confirmedOrder(true);   // đã xuống bếp

        customerOrders.cancelByCustomer(f.orderId, f.customerId);

        assertEquals("CANCELLED", statusOf(f.orderId),
                "Chưa tốn nguyên liệu thì không có lý do gì từ chối khách");
    }

    @Test
    @DisplayName("Đơn chưa xuống bếp: huỷ được")
    void scheduledOrderCanBeCancelled() {
        Fixture f = confirmedOrder(false);

        customerOrders.cancelByCustomer(f.orderId, f.customerId);

        assertEquals("CANCELLED", statusOf(f.orderId));
    }

    @Test
    @DisplayName("Bếp đã nhận việc: không huỷ được nữa")
    void cancelIsBlockedOnceCookingStarted() {
        Fixture f = confirmedOrder(true);
        kitchenService.claim(f.orderItemId, userId(KITCHEN_1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> customerOrders.cancelByCustomer(f.orderId, f.customerId));

        assertEquals("PREPARING", statusOf(f.orderId), "Không được kéo trạng thái bếp lùi lại");
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("Bếp đã bắt đầu"),
                "Thông báo phải nói rõ vì sao: " + e.getMessage());
    }

    @Test
    @DisplayName("Huỷ đơn đã trả tiền thì tự hoàn tiền toàn phần")
    void cancellingPaidOrderRefundsAutomatically() {
        Fixture f = confirmedOrder(false);
        payFor(f.orderId);

        customerOrders.cancelByCustomer(f.orderId, f.customerId);

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Payment " +
                        "WHERE order_id = ? AND payment_status = 'REFUNDED'", f.orderId),
                "Khách huỷ đơn đã trả tiền thì phải được hoàn ngay, không phải đi hỏi nhân viên");
        assertNotNull(scalar(LocalDateTime.class,
                "SELECT refunded_at FROM dbo.Payment WHERE order_id = ?", f.orderId));
    }

    @Test
    @DisplayName("Hoàn tiền không lặp: huỷ xong bấm hoàn lần nữa cũng không trừ thêm")
    void refundIsIdempotent() {
        Fixture f = confirmedOrder(false);
        payFor(f.orderId);
        customerOrders.cancelByCustomer(f.orderId, f.customerId);

        // Đơn đã hoàn rồi; đường hoàn tiền sót phải nhận ra và từ chối
        BusinessException e = assertThrows(BusinessException.class,
                () -> new com.fastfood.service.shared.PaymentService()
                        .refund(f.orderId, userId(CASHIER_1), "thu ngan bam nham lan hai"));

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Payment " +
                        "WHERE order_id = ? AND payment_status = 'REFUNDED'", f.orderId),
                "Không được sinh thêm khoản hoàn: " + e.getMessage());
    }

    @Test
    @DisplayName("Hoàn tiền bắt buộc nhập lý do (BR-24)")
    void refundRequiresReason() {
        Fixture f = confirmedOrder(false);
        payFor(f.orderId);
        customerOrders.cancelByCustomer(f.orderId, f.customerId);

        assertThrows(ValidationException.class,
                () -> new com.fastfood.service.shared.PaymentService().refund(f.orderId, userId(CASHIER_1), "  "));
    }

    @Test
    @DisplayName("Huỷ đơn của người khác thì báo không tìm thấy, không báo không có quyền (BR-21)")
    void cannotCancelSomeoneElsesOrder() {
        Fixture f = confirmedOrder(false);

        assertThrows(NotFoundException.class,
                () -> customerOrders.cancelByCustomer(f.orderId, userId(CUSTOMER_2)),
                "Trả 'không tìm thấy' để người ngoài không dò được mã đơn nào có thật");
    }

    @Test
    @DisplayName("Thu ngân huỷ được cả đơn bếp đang làm, nhưng phải nêu lý do")
    void staffCanCancelLaterButMustGiveReason() {
        Fixture f = confirmedOrder(true);
        kitchenService.claim(f.orderItemId, userId(KITCHEN_1));

        assertThrows(ValidationException.class,
                () -> staffOrders.cancelByStaff(f.orderId, userId(CASHIER_1), ""));

        staffOrders.cancelByStaff(f.orderId, userId(CASHIER_1), "khach goi dien xin huy");

        assertEquals("CANCELLED", statusOf(f.orderId));
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'ORDER' " +
                        "AND entity_id = ? AND action = 'ORDER_CANCELLED' AND new_value LIKE '%khach goi dien%'",
                String.valueOf(f.orderId)),
                "Lý do phải nằm trong nhật ký, không chỉ hiện trên màn hình");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private record Fixture(int orderId, int orderItemId, int customerId) {
    }

    /** Đơn đặt trước đã xác nhận, một món đang chờ bếp. */
    private Fixture confirmedOrder(boolean releasedToKitchen) {
        int customerId = userId(CUSTOMER_1);
        LocalDateTime pickup = LocalDateTime.now().plusHours(3);
        LocalDateTime release = pickup.minusMinutes(20);

        exec("INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, " +
             "order_status, pickup_time, kitchen_release_at, released_to_kds_at, pickup_code, created_at) " +
             "VALUES (?, ?, 'ONLINE_PREORDER', 50000, 'CONFIRMED', ?, ?, ?, ?, ?)",
             customerId, customerId, pickup, release,
             releasedToKitchen ? LocalDateTime.now() : null,
             "TC" + (System.nanoTime() % 100000000L), LocalDateTime.now());

        int orderId = scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");
        exec("INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, " +
             "quantity, item_status) VALUES (?, ?, N'Mon test', 50000, 1, 'WAITING')",
             orderId, anyOrderableProductId());
        int itemId = scalar(Integer.class, "SELECT MAX(order_item_id) FROM dbo.OrderItem");

        return new Fixture(orderId, itemId, customerId);
    }

    private void payFor(int orderId) {
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, " +
             "created_at, paid_at) VALUES (?, 'ONLINE_GATEWAY', 50000, 'PAID', 1, ?, ?)",
             orderId, LocalDateTime.now(), LocalDateTime.now());
    }

    private static String statusOf(int orderId) {
        return text("SELECT order_status FROM dbo.Orders WHERE order_id = ?", orderId);
    }
}
