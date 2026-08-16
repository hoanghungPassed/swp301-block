package com.fastfood.flow;

import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.PosLine;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.Shift;
import com.fastfood.service.staff.ShiftService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ca làm việc của thu ngân và đối soát tiền mặt.
 * <p>
 * Bài đáng đọc trước là {@link Reconcile#refundLeavesTheDrawerOfTheShiftThatPaidItOut()}: nó
 * kiểm đúng cái bẫy mà báo cáo doanh thu đã từng mắc — một khoản đã thu rồi hoàn phải được
 * đếm bằng <b>hai mốc thời gian khác nhau</b>, chứ không lọc theo trạng thái hiện tại.
 */
@DisplayName("Ca làm việc và đối soát tiền mặt tại quầy")
class ShiftReconcileIT extends IntegrationTestBase {

    private final ShiftService shiftService = new ShiftService();
    private final StaffOrderService orderService = new StaffOrderService();

    /** Đóng mọi ca còn mở của thu ngân để mỗi bài bắt đầu từ trạng thái sạch. */
    private void closeAnyOpenShift(int cashierId) {
        Shift open = shiftService.currentShift(cashierId);
        if (open != null) {
            exec("UPDATE dbo.Shift SET status = 'CANCELLED' WHERE shift_id = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM dbo.Orders o WHERE o.shift_id = ?)",
                    open.getShiftId(), open.getShiftId());
            if (shiftService.currentShift(cashierId) != null) {
                exec("UPDATE dbo.Shift SET status='CLOSED', closed_at=SYSDATETIME(), "
                        + "counted_cash=0, expected_cash=0, variance=0 WHERE shift_id = ?",
                        open.getShiftId());
            }
        }
    }

    private List<PosLine> oneItem() {
        return List.of(new PosLine(anyOrderableProductId(), 1));
    }

    @Nested
    @DisplayName("Bốn thao tác")
    class Crud {

        @Test
        @DisplayName("Mở ca rồi đọc lại được, chưa có đơn nào")
        void openThenRead() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);

            Shift shift = shiftService.open(cashier, new BigDecimal("500000"), "ca sáng");

            assertTrue(shift.getShiftId() > 0);
            assertEquals("OPEN", shift.getStatus());
            assertEquals(0, shift.getOrderCount());
            assertNotNull(shiftService.currentShift(cashier));
        }

        @Test
        @DisplayName("Sửa được ghi chú của ca đang mở")
        void updateNote() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, BigDecimal.ZERO, null);

            shiftService.updateNote(shift.getShiftId(), cashier, "máy POS bị treo 5 phút");

            assertEquals("máy POS bị treo 5 phút",
                    shiftService.findById(shift.getShiftId()).getNote());
        }

        @Test
        @DisplayName("Thu hồi được ca mở nhầm khi chưa có đơn nào")
        void cancelEmptyShift() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, BigDecimal.ZERO, null);

            shiftService.cancel(shift.getShiftId(), cashier);

            assertEquals("CANCELLED", shiftService.findById(shift.getShiftId()).getStatus());
            assertNull(shiftService.currentShift(cashier), "Thu hồi rồi thì không còn ca đang mở");
        }

        @Test
        @DisplayName("Đóng ca lưu lại cả ba con số đối soát")
        void closeStoresAllThreeNumbers() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, new BigDecimal("200000"), null);

            Shift closed = shiftService.close(shift.getShiftId(), cashier, new BigDecimal("200000"));

            assertEquals("CLOSED", closed.getStatus());
            assertEquals(0, closed.getExpectedCash().compareTo(new BigDecimal("200000")),
                    "Chưa bán gì thì két phải đúng bằng tiền đầu ca");
            assertEquals(0, closed.getVariance().signum(), "Đếm đúng thì không lệch");
            assertNotNull(closed.getClosedAt());
        }
    }

    @Nested
    @DisplayName("Ba chốt chặn")
    class Guards {

        @Test
        @DisplayName("Một thu ngân chỉ có một ca đang mở")
        void onlyOneOpenShiftPerCashier() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            shiftService.open(cashier, BigDecimal.ZERO, null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> shiftService.open(cashier, BigDecimal.ZERO, null));

            assertTrue(e.getMessage().contains("chưa đóng"), "Nhận được: " + e.getMessage());
        }

        @Test
        @DisplayName("Ca đã có đơn thì không thu hồi được, phải đóng cho đúng thủ tục")
        void shiftWithOrdersCannotBeCancelled() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, BigDecimal.ZERO, null);
            orderService.createPosOrder(cashier, oneItem(), PaymentMethod.CASH, null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> shiftService.cancel(shift.getShiftId(), cashier));

            assertTrue(e.getMessage().contains("đóng ca"), "Nhận được: " + e.getMessage());
        }

        @Test
        @DisplayName("Ca đã đóng thì đóng lại hay sửa ghi chú đều bị từ chối")
        void closedShiftIsFrozen() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, BigDecimal.ZERO, null);
            int id = shift.getShiftId();
            shiftService.close(id, cashier, BigDecimal.ZERO);

            assertThrows(BusinessException.class, () -> shiftService.close(id, cashier, BigDecimal.ZERO));
            assertThrows(BusinessException.class, () -> shiftService.updateNote(id, cashier, "x"));
        }

        @Test
        @DisplayName("Số tiền đếm được không hợp lệ thì bị từ chối")
        void countedCashMustBeValid() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, BigDecimal.ZERO, null);

            assertThrows(ValidationException.class,
                    () -> shiftService.close(shift.getShiftId(), cashier, null));
            assertThrows(ValidationException.class,
                    () -> shiftService.close(shift.getShiftId(), cashier, new BigDecimal("-1")));
        }
    }

    @Nested
    @DisplayName("Đối soát tiền mặt")
    class Reconcile {

        @Test
        @DisplayName("Đơn tiền mặt bán trong ca cộng vào số hệ thống tính")
        void cashSaleAddsToExpected() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, new BigDecimal("100000"), null);

            Order order = orderService.createPosOrder(cashier, oneItem(), PaymentMethod.CASH, null);
            BigDecimal expected = shiftService.expectedCashNow(shift.getShiftId());

            assertEquals(0, expected.compareTo(new BigDecimal("100000").add(order.getTotalAmount())),
                    "Két phải bằng tiền đầu ca cộng tiền mặt vừa thu");
        }

        @Test
        @DisplayName("Đơn quẹt thẻ KHÔNG cộng vào tiền mặt — tiền không đi qua két")
        void cardSaleDoesNotTouchTheDrawer() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, new BigDecimal("100000"), null);

            orderService.createPosOrder(cashier, oneItem(), PaymentMethod.ONLINE_GATEWAY,
                    "BIENLAI-" + System.nanoTime());

            assertEquals(0, shiftService.expectedCashNow(shift.getShiftId())
                            .compareTo(new BigDecimal("100000")),
                    "Tiền thẻ chạy qua máy thanh toán, không qua két tiền mặt");
        }

        @Test
        @DisplayName("Hoàn tiền mặt trừ vào chính ca đã chi tiền ra khỏi két")
        void refundLeavesTheDrawerOfTheShiftThatPaidItOut() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, new BigDecimal("100000"), null);
            Order order = orderService.createPosOrder(cashier, oneItem(), PaymentMethod.CASH, null);
            BigDecimal beforeRefund = shiftService.expectedCashNow(shift.getShiftId());

            orderService.cancelByStaff(order.getOrderId(), cashier, "khách đổi ý");

            BigDecimal afterRefund = shiftService.expectedCashNow(shift.getShiftId());
            assertEquals(0, afterRefund.compareTo(new BigDecimal("100000")),
                    "Thu rồi hoàn trong cùng ca thì két trở lại đúng tiền đầu ca. Lọc vế thu "
                            + "theo trạng thái PAID sẽ trừ hai lần và cho ra số âm — đúng lỗi "
                            + "mà báo cáo doanh thu đã từng mắc");
            assertTrue(beforeRefund.compareTo(afterRefund) > 0);
        }

        @Test
        @DisplayName("Đếm thiếu thì chênh lệch âm và được lưu lại")
        void shortCountIsRecorded() {
            int cashier = userId(CASHIER_1);
            closeAnyOpenShift(cashier);
            Shift shift = shiftService.open(cashier, new BigDecimal("100000"), null);

            Shift closed = shiftService.close(shift.getShiftId(), cashier, new BigDecimal("90000"));

            assertTrue(closed.isShortOfCash(), "Thiếu 10.000đ");
            assertEquals(0, closed.getVariance().compareTo(new BigDecimal("-10000")));
            assertEquals(0, closed.getVarianceAbs().compareTo(new BigDecimal("10000")));
        }
    }

    @Test
    @DisplayName("Bán khi chưa mở ca vẫn được, đơn chỉ không thuộc ca nào")
    void sellingWithoutAnOpenShiftStillWorks() {
        int cashier = userId(CASHIER_1);
        closeAnyOpenShift(cashier);
        assertNull(shiftService.currentShift(cashier));

        Order order = orderService.createPosOrder(cashier, oneItem(), PaymentMethod.CASH, null);

        assertTrue(order.getOrderId() > 0, "Chặn bán vì chưa mở ca sẽ làm thu ngân kẹt lúc đông khách");
        assertEquals(0, count("SELECT COUNT(*) FROM dbo.Orders WHERE order_id = ? AND shift_id IS NOT NULL",
                        order.getOrderId()),
                "Đơn nằm ngoài mọi ca, và màn hình có cảnh báo về chuyện đó");
    }
}
