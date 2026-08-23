package com.fastfood.model;

import com.fastfood.common.constant.Constants.KdsReleaseState;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.constant.Constants.PaymentStatus;
import com.fastfood.config.AppConfig;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.Payment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        @DisplayName("Lần thu thất bại thì không tính là đã trả tiền")
        void failedPaymentIsNotPaid() {
            Order o = order(OrderStatus.READY);
            o.setLatestPayment(payment(PaymentStatus.FAILED));
            assertFalse(o.isPaid(), "Giao món cho đơn chưa có tiền là mất trắng phần ăn đó");
        }

        @Test
        @DisplayName("Chưa có lần thanh toán nào thì chưa trả tiền")
        void noPaymentMeansNotPaid() {
            assertFalse(order(OrderStatus.PENDING_PAYMENT).isPaid());
        }
    }

    @Nested
    @DisplayName("Chuyển trạng thái của đơn")
    class StatusMachine {

        @Test
        @DisplayName("Hai trạng thái kết thúc thì không đi tiếp được")
        void terminalStatesAreFinal() {
            assertTrue(OrderStatus.COMPLETED.isFinal());
            assertTrue(OrderStatus.EXPIRED.isFinal());
            assertFalse(OrderStatus.READY.isFinal());
        }
    }

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

    private static Payment payment(PaymentStatus status) {
        Payment p = new Payment();
        p.setPaymentStatus(status.name());
        p.setAmount(new BigDecimal("50000"));
        return p;
    }
}
