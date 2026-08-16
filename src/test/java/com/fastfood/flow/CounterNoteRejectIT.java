package com.fastfood.flow;

import com.fastfood.common.constant.IssueType;
import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.PosLine;
import com.fastfood.model.entity.KitchenIssue;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderNote;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.CounterRejectService;
import com.fastfood.service.staff.OrderNoteService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hai màn còn lại của thu ngân: ghi chú điều phối và từ chối nhận món tại quầy.
 */
@DisplayName("Ghi chú điều phối và từ chối nhận món tại quầy")
class CounterNoteRejectIT extends IntegrationTestBase {

    private final OrderNoteService noteService = new OrderNoteService();
    private final CounterRejectService rejectService = new CounterRejectService();
    private final StaffOrderService orderService = new StaffOrderService();
    private final KitchenService kitchenService = new KitchenService();

    private Order posOrder() {
        return orderService.createPosOrder(userId(CASHIER_1),
                List.of(new PosLine(anyOrderableProductId(), 1)), PaymentMethod.CASH, null);
    }

    /** Đơn tại quầy có đúng một món, đã nấu xong và bếp đã bàn giao ra quầy, quầy chưa nhận. */
    private Order orderWithItemOnCounter() {
        Order order = posOrder();
        int itemId = order.getItems().get(0).getOrderItemId();
        kitchenService.claim(itemId, userId(KITCHEN_1));
        kitchenService.markReady(itemId, userId(KITCHEN_1));
        kitchenService.handOverToCounter(itemId, userId(KITCHEN_1));
        return order;
    }

    /** Một món đã được bếp bàn giao ra quầy nhưng quầy chưa nhận — đúng lúc từ chối được. */
    private int itemWaitingOnCounter() {
        return orderWithItemOnCounter().getItems().get(0).getOrderItemId();
    }

    @Nested
    @DisplayName("Ghi chú điều phối trên đơn")
    class Notes {

        @Test
        @DisplayName("Thêm, sửa, xoá — và xoá là xoá hẳn")
        void fullCycle() {
            Order order = posOrder();
            int cashier = userId(CASHIER_1);

            OrderNote note = noteService.add(order.getOrderId(), cashier, "  khách báo đến muộn  ");
            assertEquals("khách báo đến muộn", note.getContent(), "Phải cắt khoảng trắng thừa");
            assertFalse(note.isEdited());

            noteService.update(note.getOrderNoteId(), cashier, "khách báo đến muộn 20 phút");
            OrderNote after = noteService.notesOf(order.getOrderId()).get(0);
            assertEquals("khách báo đến muộn 20 phút", after.getContent());
            assertTrue(after.isEdited());

            noteService.delete(note.getOrderNoteId(), cashier);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderNote WHERE order_note_id = ?",
                    note.getOrderNoteId()));
        }

        @Test
        @DisplayName("Chỉ người viết mới sửa hoặc xoá được")
        void onlyAuthorMayEdit() {
            Order order = posOrder();
            OrderNote note = noteService.add(order.getOrderId(), userId(CASHIER_1), "của thu ngân 1");
            int id = note.getOrderNoteId();
            int other = userId(ADMIN);

            assertThrows(BusinessException.class, () -> noteService.update(id, other, "sửa trộm"));
            assertThrows(BusinessException.class, () -> noteService.delete(id, other));
        }

        @Test
        @DisplayName("Lấy ghi chú của nhiều đơn trong một lượt, gom đúng theo đơn")
        void batchLookupGroupsByOrder() {
            Order a = posOrder();
            Order b = posOrder();
            noteService.add(a.getOrderId(), userId(CASHIER_1), "ghi chú A");
            noteService.add(b.getOrderId(), userId(CASHIER_1), "ghi chú B1");
            noteService.add(b.getOrderId(), userId(CASHIER_1), "ghi chú B2");

            var byOrder = noteService.notesOfOrders(List.of(a.getOrderId(), b.getOrderId()));

            assertEquals(1, byOrder.get(a.getOrderId()).size());
            assertEquals(2, byOrder.get(b.getOrderId()).size());
        }

        @Test
        @DisplayName("Nội dung rỗng và đơn không tồn tại đều bị từ chối")
        void invalidInput() {
            Order order = posOrder();
            assertThrows(ValidationException.class,
                    () -> noteService.add(order.getOrderId(), userId(CASHIER_1), "   "));
            assertThrows(NotFoundException.class,
                    () -> noteService.add(999_999, userId(CASHIER_1), "đơn ma"));
        }
    }

    @Nested
    @DisplayName("Từ chối nhận món")
    class Reject {

        @Test
        @DisplayName("Từ chối được món bếp đã bàn giao mà quầy chưa nhận")
        void rejectHandedOverItem() {
            int itemId = itemWaitingOnCounter();

            KitchenIssue issue = rejectService.reject(itemId, userId(CASHIER_1), "món bị nguội");

            assertEquals(IssueType.COUNTER_REJECT.name(), issue.getIssueType());
            assertEquals("OPEN", issue.getStatus());
            assertTrue(rejectService.openRejects().stream()
                    .anyMatch(r -> r.getIssueId() == issue.getIssueId()));
        }

        @Test
        @DisplayName("Món bị từ chối rời hàng chờ của quầy và quay lại danh sách của bếp")
        void rejectedItemGoesBackToKitchen() {
            int itemId = itemWaitingOnCounter();

            rejectService.reject(itemId, userId(CASHIER_1), "món bị nguội");

            assertTrue(kitchenService.awaitingHandover(userId(KITCHEN_1)).stream()
                            .anyMatch(v -> v.getItem().getOrderItemId() == itemId),
                    "Món phải hiện lại ở 'chờ bàn giao ra quầy' của người đã nấu nó");
            assertFalse(orderService.awaitingCounter().stream()
                            .anyMatch(i -> i.getOrderItemId() == itemId),
                    "Và không còn nằm trong hàng chờ của quầy");
        }

        @Test
        @DisplayName("Làm lại xong bàn giao lại được, và đơn đi tiếp bình thường")
        void kitchenCanHandOverAgainAfterReject() {
            Order order = orderWithItemOnCounter();
            int itemId = order.getItems().get(0).getOrderItemId();
            int cashier = userId(CASHIER_1);
            rejectService.reject(itemId, cashier, "sai món");

            // Món đang nằm ở bếp thì đơn chưa giao được — đây chính là chỗ trước đây bế tắc,
            // vì không có cách nào đưa món ra quầy lần thứ hai.
            assertThrows(BusinessException.class, () -> orderService.handoff(order.getOrderId(), cashier, null),
                    "Còn món chưa ở trong tay quầy thì không giao cho khách được");

            kitchenService.handOverToCounter(itemId, userId(KITCHEN_1));
            orderService.receiveAtCounter(itemId, cashier);

            assertEquals("COMPLETED",
                    orderService.handoff(order.getOrderId(), cashier, null).getOrderStatus());
        }

        @Test
        @DisplayName("Món bếp chưa bàn giao thì chưa tới tay quầy nên không từ chối được")
        void cannotRejectBeforeHandover() {
            Order order = posOrder();
            int itemId = order.getItems().get(0).getOrderItemId();

            BusinessException e = assertThrows(BusinessException.class,
                    () -> rejectService.reject(itemId, userId(CASHIER_1), "chưa thấy món"));

            assertTrue(e.getMessage().contains("chưa bàn giao"), "Nhận được: " + e.getMessage());
        }

        @Test
        @DisplayName("Món đã nhận rồi thì không từ chối được nữa")
        void cannotRejectAfterReceiving() {
            int itemId = itemWaitingOnCounter();
            orderService.receiveAtCounter(itemId, userId(CASHIER_1));

            BusinessException e = assertThrows(BusinessException.class,
                    () -> rejectService.reject(itemId, userId(CASHIER_1), "đổi ý"));

            assertTrue(e.getMessage().contains("đã được nhận"), "Nhận được: " + e.getMessage());
        }

        @Test
        @DisplayName("Lý do bắt buộc — bếp phải biết cần sửa gì")
        void reasonIsRequired() {
            int itemId = itemWaitingOnCounter();
            assertThrows(ValidationException.class,
                    () -> rejectService.reject(itemId, userId(CASHIER_1), "  "));
        }

        @Test
        @DisplayName("Sửa lý do và thu hồi được, chỉ người lập mới làm được")
        void updateAndCancelAreOwnerOnly() {
            int itemId = itemWaitingOnCounter();
            KitchenIssue issue = rejectService.reject(itemId, userId(CASHIER_1), "sai món");
            int id = issue.getIssueId();
            int other = userId(KITCHEN_1);

            assertThrows(BusinessException.class, () -> rejectService.updateReason(id, other, "trộm"));
            assertThrows(BusinessException.class, () -> rejectService.cancel(id, other));

            rejectService.updateReason(id, userId(CASHIER_1), "sai món: khách gọi gà, bếp đưa khoai");
            rejectService.cancel(id, userId(CASHIER_1));

            assertEquals("CANCELLED", rejectService.findById(id).getStatus(),
                    "Giữ bản ghi lại vì nhật ký thao tác đã trỏ về mã này");
        }

        @Test
        @DisplayName("Không dùng đường của quầy để đụng vào sự cố do bếp báo")
        void cannotTouchKitchenIssues() {
            int itemId = itemWaitingOnCounter();
            kitchenService.openIssue(itemId, userId(KITCHEN_1), "QUALITY", "bánh cháy cạnh");
            KitchenIssue kitchenIssue = kitchenService.issuesOfItem(itemId).get(0);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> rejectService.cancel(kitchenIssue.getIssueId(), userId(CASHIER_1)));

            assertTrue(e.getMessage().contains("bếp báo"), "Nhận được: " + e.getMessage());
        }
    }
}
