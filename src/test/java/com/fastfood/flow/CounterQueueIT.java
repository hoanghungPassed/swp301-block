package com.fastfood.flow;

import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hàng chờ của quầy giao nhận (BR-25).
 * <p>
 * Đây là danh sách trả lời câu hỏi mà người đứng quầy hỏi cả ngày: món của đơn này đang ở đâu.
 * Trạng thái đơn không trả lời được — đơn ghi là sẵn sàng chỉ nghĩa là bếp đã nấu xong, món
 * vẫn có thể còn nằm trong bếp.
 * <p>
 * Bài đáng chú ý nhất ở đây là {@link #cancelledOrderItemsStayOnTheCounterQueue()}: nó giữ một
 * quyết định trông như thiếu sót nếu chỉ đọc câu truy vấn.
 */
@DisplayName("Hàng chờ của quầy giao nhận")
class CounterQueueIT extends IntegrationTestBase {

    private final KitchenService kitchenService = new KitchenService();
    private final StaffOrderService staffOrders = new StaffOrderService();

    @Test
    @DisplayName("Bếp bàn giao thì món hiện ra ở quầy, quầy nhận thì món rời danh sách")
    void itemAppearsAfterHandoverAndLeavesAfterReceipt() {
        int itemId = readyItemInKitchen();
        assertFalse(onCounterQueue(itemId), "Món còn trong bếp thì chưa phải việc của quầy");

        kitchenService.handOverToCounter(itemId, userId(KITCHEN_1));
        assertTrue(onCounterQueue(itemId),
                "Khoảng giữa hai mốc chính là lúc món nằm chờ trên quầy — và đó là toàn bộ "
                + "nội dung màn hình này");

        staffOrders.receiveAtCounter(itemId, userId(CASHIER_1));
        assertFalse(onCounterQueue(itemId),
                "Còn nằm lại thì thu ngân bấm đi bấm lại một nút không bao giờ ăn");
    }

    @Test
    @DisplayName("Đơn bị huỷ sau khi nấu xong thì món VẪN nằm lại hàng chờ của quầy")
    void cancelledOrderItemsStayOnTheCounterQueue() {
        int itemId = readyItemInKitchen();
        kitchenService.handOverToCounter(itemId, userId(KITCHEN_1));
        int orderId = scalar(Integer.class,
                "SELECT order_id FROM dbo.OrderItem WHERE order_item_id = ?", itemId);

        exec("UPDATE dbo.Orders SET order_status = 'CANCELLED', cancelled_at = ? WHERE order_id = ?",
             LocalDateTime.now(), orderId);

        OrderItem onQueue = staffOrders.awaitingCounter().stream()
                .filter(i -> i.getOrderItemId() == itemId)
                .findFirst().orElse(null);

        assertTrue(onQueue != null,
                "Lọc theo trạng thái đơn ở đây là sai: món đã nấu xong là món có thật, đang nằm "
                + "trên quầy. Giấu nó đi thì không ai còn nhìn thấy để mang đi bỏ, và nó nằm lại "
                + "đó tới cuối ca");
        assertTrue(onQueue.isOrderClosed(),
                "Phải nói được đơn đã đóng để quầy mang món đi bỏ chứ không đưa cho khách");
        assertEquals("CANCELLED", onQueue.getOrderStatus());
    }

    @Test
    @DisplayName("Đơn sẵn sàng nói được còn thiếu mấy món chưa về tới quầy")
    void readyOrderKnowsHowManyItemsAreStillMissing() {
        Fixture f = twoItemOrder();
        for (int itemId : f.itemIds) {
            kitchenService.claim(itemId, userId(KITCHEN_1));
            kitchenService.markReady(itemId, userId(KITCHEN_1));
        }
        kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1));
        staffOrders.receiveAtCounter(f.itemIds.get(0), userId(CASHIER_1));

        Order shown = staffOrders.readyOrdersForCounter().stream()
                .filter(o -> o.getOrderId() == f.orderId)
                .findFirst().orElse(null);

        assertTrue(shown != null, "Đơn đã sẵn sàng phải hiện ở khối chờ khách tới lấy");
        assertEquals(2, shown.getItems().size(),
                "Màn hình này nạp kèm danh sách món; bản rút gọn của trang điều phối không nạp "
                + "nên không trả lời được câu \"còn thiếu món nào\"");
        assertEquals(1, shown.getItems().stream().filter(i -> !i.isReceived()).count());
    }

    @Test
    @DisplayName("Món của hai đơn khác nhau không lẫn vào nhau")
    void queueKeepsItemsOfDifferentOrdersApart() {
        int a = readyItemInKitchen();
        int b = readyItemInKitchen();
        kitchenService.handOverToCounter(a, userId(KITCHEN_1));

        assertTrue(onCounterQueue(a));
        assertFalse(onCounterQueue(b), "Bàn giao món này không được kéo theo món của đơn khác");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private boolean onCounterQueue(int itemId) {
        return staffOrders.awaitingCounter().stream()
                .anyMatch(i -> i.getOrderItemId() == itemId);
    }

    private record Fixture(int orderId, List<Integer> itemIds) {
    }

    /** Một món đã nấu xong, do KITCHEN_1 làm, còn nằm trong bếp. */
    private int readyItemInKitchen() {
        Fixture f = twoItemOrder(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));
        kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1));
        return f.itemIds.get(0);
    }

    private Fixture twoItemOrder() {
        return twoItemOrder(2);
    }

    /** Đơn tại quầy đã xác nhận, món đã xuống bếp — không cần giờ hẹn nên dựng gọn. */
    private Fixture twoItemOrder(int itemCount) {
        LocalDateTime now = LocalDateTime.now();
        exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
             "released_to_kds_at, created_at) VALUES (NULL, 'POS', ?, 'CONFIRMED', ?, ?)",
             25000 * itemCount, now, now);
        int orderId = scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");

        int productId = anyOrderableProductId();
        List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            exec("INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, " +
                 "unit_price, quantity, item_status) VALUES (?, ?, N'Mon test quay', 25000, 1, 'WAITING')",
                 orderId, productId);
            ids.add(scalar(Integer.class, "SELECT MAX(order_item_id) FROM dbo.OrderItem"));
        }
        return new Fixture(orderId, ids);
    }
}
