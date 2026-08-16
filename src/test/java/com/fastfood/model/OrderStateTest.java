package com.fastfood.model;

import com.fastfood.common.constant.KdsReleaseState;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.constant.PaymentStatus;
import com.fastfood.config.AppConfig;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.model.entity.Payment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Các giá trị <b>suy ra</b> của đơn hàng: trạng thái đưa xuống bếp, cờ khách đến muộn, món ra
 * trễ hẹn, còn huỷ được hay không.
 * <p>
 * Không có giá trị nào ở đây được lưu thành cột. Đó là quyết định thiết kế: lưu thêm cột nghĩa
 * là phải cập nhật đúng lúc ở mọi nơi, và chỉ cần sót một chỗ là dữ liệu tự mâu thuẫn với
 * chính nó. Đổi lại, công thức suy ra phải đúng — nên nó được kiểm ở đây.
 */
@DisplayName("Trạng thái suy ra của đơn hàng")
class OrderStateTest {

    @BeforeAll
    static void loadConfig() {
        AppConfig.init();
    }

    @Nested
    @DisplayName("Trạng thái đưa xuống bếp")
    class ReleaseState {

        @Test
        @DisplayName("Chưa xác nhận thì bếp chưa hề biết tới đơn")
        void pendingOrderIsNotReleased() {
            assertEquals(KdsReleaseState.NOT_RELEASED, order(OrderStatus.PENDING_PAYMENT).getReleaseState());
        }

        @Test
        @DisplayName("Đã xác nhận nhưng chưa tới giờ thì đang xếp lịch")
        void confirmedOrderIsScheduled() {
            assertEquals(KdsReleaseState.SCHEDULED, order(OrderStatus.CONFIRMED).getReleaseState());
        }

        @Test
        @DisplayName("Có mốc thực tế thì đã xuống bếp — mốc thực tế thắng mọi suy đoán")
        void releasedTimestampWins() {
            Order o = order(OrderStatus.CONFIRMED);
            o.setReleasedToKdsAt(LocalDateTime.now());
            assertEquals(KdsReleaseState.RELEASED_TO_KDS, o.getReleaseState());
        }
    }

    @Nested
    @DisplayName("Khách đến muộn (BR-17)")
    class Overdue {

        @Test
        @DisplayName("Quá giờ hẹn hơn ngưỡng thì bị đánh dấu")
        void pastThresholdIsOverdue() {
            Order o = readyOnlineOrder(LocalDateTime.now()
                    .minusMinutes(AppConfig.pickupOverdueMinutes() + 10));
            assertTrue(o.isOverdue());
        }

        @Test
        @DisplayName("Chưa quá ngưỡng thì chưa bị đánh dấu")
        void withinThresholdIsNotOverdue() {
            Order o = readyOnlineOrder(LocalDateTime.now().minusMinutes(5));
            assertFalse(o.isOverdue());
        }

        @Test
        @DisplayName("Đơn tại quầy không bao giờ bị đánh dấu đến muộn — khách đứng ngay đó")
        void posOrderIsNeverOverdue() {
            Order o = readyOnlineOrder(LocalDateTime.now().minusHours(5));
            o.setOrderSource("POS");
            assertFalse(o.isOverdue());
        }

        @Test
        @DisplayName("Đơn chưa sẵn sàng thì chưa tính là khách đến muộn")
        void onlyReadyOrdersCanBeOverdue() {
            Order o = readyOnlineOrder(LocalDateTime.now().minusHours(5));
            o.setOrderStatus(OrderStatus.PREPARING.name());
            assertFalse(o.isOverdue(), "Đây là lỗi của bếp chứ không phải của khách");
        }
    }

    @Nested
    @DisplayName("Món ra trễ hẹn — mẫu số của chỉ số đúng hẹn")
    class LateReady {

        @Test
        @DisplayName("Xong sau giờ hẹn là trễ")
        void readyAfterPickupIsLate() {
            Order o = order(OrderStatus.READY);
            o.setPickupTime(LocalDateTime.now());
            o.setReadyAt(LocalDateTime.now().plusMinutes(10));
            assertTrue(o.isLateReady());
        }

        @Test
        @DisplayName("Xong trước giờ hẹn là đúng hẹn")
        void readyBeforePickupIsOnTime() {
            Order o = order(OrderStatus.READY);
            o.setPickupTime(LocalDateTime.now().plusMinutes(10));
            o.setReadyAt(LocalDateTime.now());
            assertFalse(o.isLateReady());
        }

        @Test
        @DisplayName("Chưa xong món thì chưa kết luận được")
        void notReadyYetIsNotLate() {
            Order o = order(OrderStatus.PREPARING);
            o.setPickupTime(LocalDateTime.now().minusHours(1));
            assertFalse(o.isLateReady());
        }
    }

    @Nested
    @DisplayName("Khách còn huỷ được không (BR-12)")
    class Cancellable {

        @Test
        @DisplayName("Đơn chờ thanh toán thì luôn huỷ được")
        void pendingPaymentIsAlwaysCancellable() {
            assertTrue(order(OrderStatus.PENDING_PAYMENT).isCancellable(),
                    "Chưa thu đồng nào, bếp chưa thấy đơn — không cho huỷ thì khách bị kẹt");
        }

        @Test
        @DisplayName("Đã xác nhận mà mọi món còn chờ thì huỷ được")
        void confirmedWithAllItemsWaitingIsCancellable() {
            Order o = order(OrderStatus.CONFIRMED);
            o.setItems(List.of(item("WAITING"), item("WAITING")));
            assertTrue(o.isCancellable());
        }

        @Test
        @DisplayName("Chỉ cần một món đã vào bếp là không huỷ được nữa")
        void oneItemInProgressBlocksCancel() {
            Order o = order(OrderStatus.CONFIRMED);
            o.setItems(List.of(item("WAITING"), item("PREPARING")));
            assertFalse(o.isCancellable(), "Đã tốn nguyên liệu cho một món thì thôi");
        }

        @Test
        @DisplayName("Đơn đã sẵn sàng thì khách không tự huỷ được")
        void readyOrderIsNotCustomerCancellable() {
            assertFalse(order(OrderStatus.READY).isCancellable());
        }

        @Test
        @DisplayName("Thu ngân đóng được mọi đơn chưa kết thúc, rộng hơn quyền của khách")
        void staffCanCloseAnyLiveOrder() {
            assertTrue(order(OrderStatus.READY).isStaffCancellable());
            assertTrue(order(OrderStatus.PREPARING).isStaffCancellable());
            assertFalse(order(OrderStatus.COMPLETED).isStaffCancellable());
            assertFalse(order(OrderStatus.CANCELLED).isStaffCancellable());
        }
    }

    @Nested
    @DisplayName("Tình trạng thanh toán")
    class PaymentState {

        @Test
        @DisplayName("Có khoản đã thu thì tính là đã trả tiền")
        void paidWhenLatestPaymentIsPaid() {
            Order o = order(OrderStatus.READY);
            o.setLatestPayment(payment(PaymentStatus.PAID));
            assertTrue(o.isPaid());
        }

        @Test
        @DisplayName("Đã hoàn tiền thì không còn tính là đã trả tiền")
        void refundedIsNotPaid() {
            Order o = order(OrderStatus.READY);
            o.setLatestPayment(payment(PaymentStatus.REFUNDED));
            assertFalse(o.isPaid(), "Giao món cho đơn đã hoàn tiền là mất trắng phần ăn đó");
        }

        @Test
        @DisplayName("Chưa có lần thanh toán nào thì chưa trả tiền")
        void noPaymentMeansNotPaid() {
            assertFalse(order(OrderStatus.PENDING_PAYMENT).isPaid());
        }

        @Test
        @DisplayName("Đơn đã đóng mà tiền còn nằm lại thì phải hiện lối hoàn tiền sót")
        void cancelledOrderWithPaidMoneyNeedsRefund() {
            Order o = order(OrderStatus.CANCELLED);
            o.setLatestPayment(payment(PaymentStatus.PAID));
            assertTrue(o.isRefundPending());
        }

        @Test
        @DisplayName("Đơn đã giao xong thì không phải hoàn gì")
        void completedOrderNeedsNoRefund() {
            Order o = order(OrderStatus.COMPLETED);
            o.setLatestPayment(payment(PaymentStatus.PAID));
            assertFalse(o.isRefundPending());
        }
    }

    @Nested
    @DisplayName("Chuyển trạng thái của đơn")
    class StatusMachine {

        // Mốc "chỉ đơn sẵn sàng mới giao được" không kiểm ở đây nữa: nó không chỉ là một
        // trạng thái mà còn đòi quầy đã nhận đủ món và tiền đã thu, nên chỗ kiểm đúng là
        // StaffOrderService.handoff — xem OnlinePreorderFlowIT và CounterQueueIT.

        @Test
        @DisplayName("Ba trạng thái kết thúc thì không đi tiếp được")
        void terminalStatesAreFinal() {
            assertTrue(OrderStatus.COMPLETED.isFinal());
            assertTrue(OrderStatus.CANCELLED.isFinal());
            assertTrue(OrderStatus.EXPIRED.isFinal());
            assertFalse(OrderStatus.READY.isFinal());
        }
    }

    // ------------------------------------------------------------------ dựng đối tượng

    private static Order order(OrderStatus status) {
        Order o = new Order();
        o.setOrderId(1);
        o.setOrderSource("ONLINE_PREORDER");
        o.setOrderStatus(status.name());
        o.setTotalAmount(new BigDecimal("50000"));
        return o;
    }

    private static Order readyOnlineOrder(LocalDateTime pickupTime) {
        Order o = order(OrderStatus.READY);
        o.setPickupTime(pickupTime);
        o.setReadyAt(pickupTime.minusMinutes(5));
        return o;
    }

    private static OrderItem item(String status) {
        OrderItem i = new OrderItem();
        i.setItemStatus(status);
        i.setQuantity(1);
        i.setUnitPrice(new BigDecimal("50000"));
        return i;
    }

    private static Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setPaymentStatus(status.name());
        p.setAmount(new BigDecimal("50000"));
        return p;
    }
}
