package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bếp và cách trạng thái đơn được <b>suy ra</b> từ trạng thái các món (BR-11).
 * <p>
 * Không ai bấm nút "đơn đang chế biến" hay "đơn sẵn sàng". Bếp chỉ đụng vào từng món, còn
 * trạng thái của cả đơn do hệ thống tính lại. Nhờ vậy chỉ có một nguồn sự thật, và không có
 * cảnh đơn ghi là sẵn sàng trong khi còn món chưa xong.
 */
@DisplayName("Bếp làm món và trạng thái đơn tự suy ra")
class KitchenFlowIT extends IntegrationTestBase {

    private final KitchenService kitchenService = new KitchenService();

    // ------------------------------------------------------------------ tổng hợp trạng thái

    @Test
    @DisplayName("Đơn hai món: xong một món thì đơn vẫn đang chế biến")
    void orderStaysPreparingUntilEveryItemIsDone() {
        Fixture f = orderWithItems(2);

        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));
        assertEquals("PREPARING", statusOf(f.orderId));

        boolean whole = kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1));

        assertFalse(whole, "Mới xong một trong hai món");
        assertEquals("PREPARING", statusOf(f.orderId),
                "Báo sẵn sàng khi còn món chưa xong là gọi khách tới rồi bắt đứng đợi tiếp");
    }

    @Test
    @DisplayName("Món cuối xong thì cả đơn sẵn sàng và ghi mốc thời gian")
    void lastItemMakesTheWholeOrderReady() {
        Fixture f = orderWithItems(2);

        for (int itemId : f.itemIds) {
            kitchenService.claim(itemId, userId(KITCHEN_1));
        }
        kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1));
        boolean whole = kitchenService.markReady(f.itemIds.get(1), userId(KITCHEN_1));

        assertTrue(whole);
        assertEquals("READY", statusOf(f.orderId));
        assertTrue(scalar(LocalDateTime.class,
                "SELECT ready_at FROM dbo.Orders WHERE order_id = ?", f.orderId) != null,
                "ready_at là mẫu số của chỉ số đúng hẹn");
    }

    @Test
    @DisplayName("Hai đầu bếp cùng nhận một món thì người sau bị từ chối")
    void secondCookCannotStealAClaimedItem() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_2)));

        assertTrue(e.getMessage().contains("vừa được người khác nhận"), e.getMessage());
        assertEquals(userId(KITCHEN_1), (int) scalar(Integer.class,
                "SELECT assigned_to_user_id FROM dbo.OrderItem WHERE order_item_id = ?", f.itemIds.get(0)));
    }

    @Test
    @DisplayName("Người không nhận món thì không đánh dấu xong được")
    void onlyTheAssignedCookCanFinishAnItem() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));

        assertThrows(BusinessException.class,
                () -> kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_2)));
    }

    @Test
    @DisplayName("Món chưa ai nhận thì chưa đánh dấu xong được")
    void cannotFinishAnItemNobodyStarted() {
        Fixture f = orderWithItems(1);

        assertThrows(BusinessException.class,
                () -> kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1)));
    }

    // ------------------------------------------------------------------ bàn giao ra quầy (BR-25)

    @Test
    @DisplayName("Món chưa làm xong thì chưa bàn giao ra quầy được")
    void cannotHandOverAnItemStillCooking() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1)));

        assertTrue(e.getMessage().contains("chưa làm xong"), e.getMessage());
    }

    @Test
    @DisplayName("Chỉ người đã làm món mới bàn giao được món đó")
    void onlyTheCookWhoMadeItCanHandItOver() {
        Fixture f = readyItem();

        BusinessException e = assertThrows(BusinessException.class,
                () -> kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_2)));

        assertTrue(e.getMessage().contains("Chỉ người đã làm món này"), e.getMessage());
        assertTrue(scalar(LocalDateTime.class,
                "SELECT handed_over_at FROM dbo.OrderItem WHERE order_item_id = ?",
                f.itemIds.get(0)) == null,
                "Ai cũng bàn giao được thì mất luôn dấu vết trách nhiệm khi món ra quầy bị thiếu");
    }

    @Test
    @DisplayName("Bàn giao ghi lại ai đưa và lúc nào, không đụng trạng thái món")
    void handoverRecordsWhoAndWhenWithoutTouchingItemStatus() {
        Fixture f = readyItem();

        kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1));

        assertTrue(scalar(LocalDateTime.class,
                "SELECT handed_over_at FROM dbo.OrderItem WHERE order_item_id = ?",
                f.itemIds.get(0)) != null);
        assertEquals(userId(KITCHEN_1), (int) scalar(Integer.class,
                "SELECT handed_over_by FROM dbo.OrderItem WHERE order_item_id = ?", f.itemIds.get(0)));
        assertEquals("READY", text("SELECT item_status FROM dbo.OrderItem WHERE order_item_id = ?",
                f.itemIds.get(0)),
                "Bàn giao là trục song song với trạng thái món: thêm bậc vào WAITING→PREPARING→READY "
                + "sẽ buộc mọi chỗ đang đếm \"món chưa xong\" phải sửa lại, trong khi nghĩa không đổi");
        assertEquals("READY", statusOf(f.orderId),
                "Đơn đã sẵn sàng từ lúc món cuối xong; bàn giao không làm nó sẵn sàng thêm lần nữa");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'ORDER_ITEM' " +
                "AND entity_id = ? AND action = 'ITEM_HANDED_OVER'", f.itemIds.get(0)));
    }

    @Test
    @DisplayName("Bàn giao lần hai bị từ chối, không ghi đè mốc lần đầu")
    void handingOverTwiceIsRejected() {
        Fixture f = readyItem();
        kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1));
        LocalDateTime first = scalar(LocalDateTime.class,
                "SELECT handed_over_at FROM dbo.OrderItem WHERE order_item_id = ?", f.itemIds.get(0));

        BusinessException e = assertThrows(BusinessException.class,
                () -> kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1)));

        assertTrue(e.getMessage().contains("đã được bàn giao"), e.getMessage());
        assertEquals(first, scalar(LocalDateTime.class,
                "SELECT handed_over_at FROM dbo.OrderItem WHERE order_item_id = ?", f.itemIds.get(0)),
                "Đè lại mốc cũ sẽ làm sai chính con số mà màn hình quầy dùng để sắp xếp món chờ lâu nhất");
    }

    @Test
    @DisplayName("Món đã bàn giao rời khỏi danh sách chờ đưa ra của bếp")
    void handedOverItemLeavesTheKitchenHandoverList() {
        Fixture f = readyItem();
        assertTrue(awaitsHandover(f.itemIds.get(0)), "Món vừa xong phải nằm trong danh sách chờ đưa ra");

        kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1));

        assertFalse(awaitsHandover(f.itemIds.get(0)),
                "Còn nằm lại thì đầu bếp bấm đi bấm lại một nút không bao giờ ăn");
    }

    private boolean awaitsHandover(int itemId) {
        return kitchenService.awaitingHandover(userId(KITCHEN_1)).stream()
                .anyMatch(v -> v.getItem().getOrderItemId() == itemId);
    }

    /** Một đơn một món, đã nấu xong, còn nằm trong bếp. */
    private Fixture readyItem() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));
        kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1));
        return f;
    }

    // ------------------------------------------------------------------ sự cố bếp

    @Test
    @DisplayName("Sự cố đang mở KHÔNG chặn bếp hoàn thành món (BR-19)")
    void openIssueDoesNotBlockCompletion() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));
        kitchenService.openIssue(f.itemIds.get(0), userId(KITCHEN_1), "REMAKE", "banh bi chay, lam lai");

        kitchenService.markReady(f.itemIds.get(0), userId(KITCHEN_1));

        assertEquals("READY", statusOf(f.orderId),
                "Đây là quyết định có chủ đích: chặn thì sự cố hết nguyên liệu sẽ khoá đơn "
                + "vĩnh viễn vì bếp không bao giờ hoàn tất được, buộc thu ngân phải huỷ đơn");
        assertEquals(1, count("SELECT COUNT(*) FROM dbo.KitchenIssue " +
                "WHERE order_item_id = ? AND status = 'OPEN'", f.itemIds.get(0)));
    }

    @Test
    @DisplayName("Báo hết nguyên liệu thì tắt luôn món đó trên thực đơn")
    void outOfStockIssueTakesTheProductOffTheMenu() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));

        kitchenService.openIssue(f.itemIds.get(0), userId(KITCHEN_1), "OUT_OF_STOCK", "het nguyen lieu");

        Boolean available = scalar(Boolean.class,
                "SELECT is_available FROM dbo.Product WHERE product_id = ?", f.productId);
        assertFalse(Boolean.TRUE.equals(available),
                "Không tắt thì khách tiếp theo vẫn đặt đúng món vừa hết, và cửa hàng nợ thêm "
                + "một đơn không làm được");

        // Trả lại tình trạng ban đầu để các bài sau vẫn có món đặt được
        exec("UPDATE dbo.Product SET is_available = 1 WHERE product_id = ?", f.productId);
    }

    @Test
    @DisplayName("Loại sự cố không có trong danh sách thì bị từ chối")
    void unknownIssueTypeIsRejected() {
        Fixture f = orderWithItems(1);

        assertThrows(com.fastfood.common.exception.ValidationException.class,
                () -> kitchenService.openIssue(f.itemIds.get(0), userId(KITCHEN_1), "LINH_TINH", "abc"));
    }

    @Test
    @DisplayName("Xử lý xong sự cố hai lần thì lần sau bị từ chối")
    void resolvingTwiceIsRejected() {
        Fixture f = orderWithItems(1);
        kitchenService.openIssue(f.itemIds.get(0), userId(KITCHEN_1), "QUALITY", "mon khong dat");
        int issueId = scalar(Integer.class,
                "SELECT MAX(issue_id) FROM dbo.KitchenIssue WHERE order_item_id = ?", f.itemIds.get(0));

        kitchenService.resolveIssue(issueId, userId(KITCHEN_1));

        assertThrows(BusinessException.class, () -> kitchenService.resolveIssue(issueId, userId(KITCHEN_1)));
    }

    @Test
    @DisplayName("Sự cố của bếp đi được sang màn hình của thu ngân")
    void issuesReachTheCounter() {
        Fixture f = orderWithItems(1);
        kitchenService.openIssue(f.itemIds.get(0), userId(KITCHEN_1), "REMAKE", "lam lai");

        assertEquals(1, kitchenService.openIssuesOfOrder(f.orderId).size(),
                "Bếp phát hiện sự cố nhưng người phải trả lời khách lại đứng ở quầy");
    }

    // ------------------------------------------------------------------ tầm nhìn của bếp

    @Test
    @DisplayName("Bếp chỉ thấy món của đơn đã được đưa xuống")
    void kitchenOnlySeesReleasedOrders() {
        Fixture waiting = orderWithItems(1, false);   // chưa đưa xuống bếp

        boolean visible = kitchenService.waitingQueue().stream()
                .anyMatch(v -> v.getItem().getOrderItemId() == waiting.itemIds.get(0));

        assertFalse(visible,
                "Đây chính là cơ chế giữ cho món đặt trước không bị làm sớm rồi nguội");
    }

    @Test
    @DisplayName("Không nhìn thấy là chưa đủ: gửi thẳng mã món của đơn chưa tới lượt cũng bị chặn")
    void cannotClaimAnItemFromAnOrderThatWasNeverReleased() {
        Fixture waiting = orderWithItems(1, false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> kitchenService.claim(waiting.itemIds.get(0), userId(KITCHEN_1)));

        assertTrue(e.getMessage().contains("chưa tới lượt vào bếp"), e.getMessage());
        assertEquals("WAITING", text("SELECT item_status FROM dbo.OrderItem WHERE order_item_id = ?",
                waiting.itemIds.get(0)),
                "Chỉ lọc ở truy vấn hàng chờ thì việc giữ đơn tới sát giờ chỉ là ẩn thẻ trên màn "
                + "hình — điều kiện phải nằm trong chính câu lệnh ghi");
    }

    @Test
    @DisplayName("Chặn ở đó cũng là giữ quyền tự huỷ đơn cho khách")
    void blockingEarlyClaimKeepsTheCustomerAbleToCancel() {
        Fixture waiting = orderWithItems(1, false);

        assertThrows(BusinessException.class,
                () -> kitchenService.claim(waiting.itemIds.get(0), userId(KITCHEN_1)));

        assertEquals("CONFIRMED", statusOf(waiting.orderId),
                "Nhận sớm lọt được thì đơn rơi vào trạng thái mâu thuẫn — đang chế biến nhưng "
                + "released_to_kds_at vẫn trống");
        assertTrue(scalar(LocalDateTime.class,
                "SELECT released_to_kds_at FROM dbo.Orders WHERE order_id = ?", waiting.orderId) == null);
        assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderItem " +
                "WHERE order_id = ? AND item_status <> 'WAITING'", waiting.orderId),
                "Mốc chặn huỷ của BR-12 là \"chưa món nào rời WAITING\"; một lần nhận sớm lọt qua "
                + "là khách mất quyền huỷ đơn mà bếp còn chưa được phép nhìn thấy");
    }

    @Test
    @DisplayName("Việc đang làm dở hiện đúng theo người nhận")
    void myTasksFollowsTheActualAssignment() {
        Fixture f = orderWithItems(1);
        kitchenService.claim(f.itemIds.get(0), userId(KITCHEN_1));

        assertTrue(kitchenService.myTasks(userId(KITCHEN_1)).stream()
                        .anyMatch(v -> v.getItem().getOrderItemId() == f.itemIds.get(0)),
                "Không lưu người nhận thì cái tên \"việc của tôi\" không có nghĩa gì");
        assertFalse(kitchenService.myTasks(userId(KITCHEN_2)).stream()
                        .anyMatch(v -> v.getItem().getOrderItemId() == f.itemIds.get(0)),
                "Việc của người này không được hiện trong danh sách của người kia");
    }

    // ------------------------------------------------------------------ ô chọn món khi báo sự cố

    @Test
    @DisplayName("Ô chọn khi báo sự cố gồm mọi món còn trong bếp, kể cả món người khác đang làm")
    void issuePickerCoversEveryStageStillInsideTheKitchen() {
        Fixture waiting = orderWithItems(1);
        Fixture preparing = orderWithItems(1);
        kitchenService.claim(preparing.itemIds.get(0), userId(KITCHEN_2));
        Fixture ready = readyItem();
        Fixture unreleased = orderWithItems(1, false);

        List<Integer> ids = kitchenService.itemsInKitchen().stream()
                .map(v -> v.getItem().getOrderItemId()).toList();

        assertTrue(ids.contains(waiting.itemIds.get(0)));
        assertTrue(ids.contains(preparing.itemIds.get(0)),
                "Người phát hiện món cháy thường không phải người đang đứng nấu nó");
        assertTrue(ids.contains(ready.itemIds.get(0)),
                "Món xong mà chưa ra quầy vẫn nằm trong bếp, vẫn hỏng được");
        assertFalse(ids.contains(unreleased.itemIds.get(0)),
                "Đơn chưa tới lượt vào bếp thì bếp còn chưa được nhìn thấy, nói gì tới báo sự cố");
    }

    @Test
    @DisplayName("Món đã bàn giao ra quầy rời khỏi ô chọn báo sự cố")
    void handedOverItemLeavesTheIssuePicker() {
        Fixture f = readyItem();
        kitchenService.handOverToCounter(f.itemIds.get(0), userId(KITCHEN_1));

        assertFalse(kitchenService.itemsInKitchen().stream()
                        .anyMatch(v -> v.getItem().getOrderItemId() == f.itemIds.get(0)),
                "Món đã rời tay đầu bếp; sự cố của nó là chuyện của quầy");
    }

    // ------------------------------------------------------------------ lịch sử món đã xong

    @Test
    @DisplayName("Lịch sử lọc theo người làm chỉ trả về món của người đó")
    void readyHistoryCanBeNarrowedToOneCook() {
        Fixture mine = readyItem();
        Fixture theirs = orderWithItems(1);
        kitchenService.claim(theirs.itemIds.get(0), userId(KITCHEN_2));
        kitchenService.markReady(theirs.itemIds.get(0), userId(KITCHEN_2));

        Page<OrderItem> onlyMine = kitchenService.recentReady(1, userId(KITCHEN_1));

        assertTrue(listed(onlyMine, mine.itemIds.get(0)));
        assertFalse(listed(onlyMine, theirs.itemIds.get(0)));
        assertTrue(listed(kitchenService.recentReady(1, 0), theirs.itemIds.get(0)),
                "Bỏ lọc thì phải thấy lại món của cả bếp");
    }

    @Test
    @DisplayName("Thanh chuyển trang đếm đúng cái mà bảng đang liệt kê")
    void readyHistoryCountMatchesTheFilteredList() {
        readyItem();

        Page<OrderItem> onlyMine = kitchenService.recentReady(1, userId(KITCHEN_1));

        assertEquals(count("SELECT COUNT(*) FROM dbo.OrderItem " +
                        "WHERE item_status = 'READY' AND assigned_to_user_id = ?", userId(KITCHEN_1)),
                onlyMine.getTotalItems(),
                "Câu đếm lọc khác câu lấy trang thì thanh chuyển trang báo tổng của cả bếp "
                + "trong khi bảng chỉ liệt kê món của một người");
    }

    private static boolean listed(Page<OrderItem> page, int itemId) {
        return page.getItems().stream().anyMatch(i -> i.getOrderItemId() == itemId);
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    private record Fixture(int orderId, List<Integer> itemIds, int productId) {
    }

    private Fixture orderWithItems(int itemCount) {
        return orderWithItems(itemCount, true);
    }

    /** Đơn đặt trước đã xác nhận với n món đang chờ bếp. */
    private Fixture orderWithItems(int itemCount, boolean releasedToKitchen) {
        LocalDateTime pickup = LocalDateTime.now().plusHours(3);
        exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
             "pickup_time, kitchen_release_at, released_to_kds_at, pickup_code, created_at) " +
             "VALUES (?, 'ONLINE_PREORDER', 50000, 'CONFIRMED', ?, ?, ?, ?, ?)",
             userId(CUSTOMER_1), pickup, pickup.minusMinutes(20),
             releasedToKitchen ? LocalDateTime.now() : null,
             "TK" + (System.nanoTime() % 100000000L), LocalDateTime.now());
        int orderId = scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");

        int productId = anyOrderableProductId();
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            exec("INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, " +
                 "quantity, item_status) VALUES (?, ?, N'Mon test', 25000, 1, 'WAITING')",
                 orderId, productId);
            ids.add(scalar(Integer.class, "SELECT MAX(order_item_id) FROM dbo.OrderItem"));
        }
        return new Fixture(orderId, ids, productId);
    }

    private static String statusOf(int orderId) {
        return text("SELECT order_status FROM dbo.Orders WHERE order_id = ?", orderId);
    }
}
