package com.fastfood.flow;

import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bốn tab điều phối phủ kín mọi đơn đang chạy")
class OrderDashboardIT extends IntegrationTestBase {

    private static final List<String> TABS = List.of("POS", "SCHEDULED", "READY", "OVERDUE");

    private final StaffOrderService staffOrders = new StaffOrderService();

    @Test
    @DisplayName("Đơn đặt trước đang được bếp làm vẫn nằm trong tab đặt trước")
    void onlineOrderBeingPreparedStaysInScheduledTab() {
        int orderId = onlineOrder("PREPARING");

        assertTrue(idsIn("SCHEDULED").contains(orderId),
                "Trước đây tab này chỉ lấy trạng thái đã xác nhận, nên đơn rơi khỏi mọi tab "
                + "ngay khi đầu bếp nhận việc và kẹt ở ngoài cho tới lúc món xong");
    }

    @Test
    @DisplayName("Đơn đặt trước đã xác nhận, chưa tới giờ: nằm trong tab đặt trước")
    void confirmedOnlineOrderIsInScheduledTab() {
        int orderId = onlineOrder("CONFIRMED");
        assertTrue(idsIn("SCHEDULED").contains(orderId));
    }

    @Test
    @DisplayName("Mọi đơn chưa kết thúc đều xuất hiện ở ít nhất một tab")
    void everyLiveOrderAppearsSomewhere() {
        List<Integer> live = new ArrayList<>();
        for (String status : List.of("CONFIRMED", "PREPARING", "READY")) {
            live.add(onlineOrder(status));
            live.add(posOrder(status));
        }

        Set<Integer> covered = new HashSet<>();
        for (String tab : TABS) {
            covered.addAll(idsIn(tab));
        }

        List<Integer> missing = live.stream().filter(id -> !covered.contains(id)).toList();
        assertTrue(missing.isEmpty(),
                "Các đơn sau không hiện ở tab nào: " + missing
                + " — thu ngân sẽ không bao giờ nhìn thấy chúng");
    }

    @Test
    @DisplayName("Tab quá hạn là lát cắt con của tab chờ khách tới lấy")
    void overdueIsSubsetOfReady() {
        onlineOverdueOrder();

        Set<Integer> ready = idsIn("READY");
        Set<Integer> overdue = idsIn("OVERDUE");

        assertTrue(ready.containsAll(overdue),
                "Đơn quá hạn vẫn đang chờ khách tới lấy, nên phải nằm trong cả hai tab. "
                + "Hệ thống không tự huỷ đơn quá hạn vì khách đã trả tiền trước (BR-17)");
        assertTrue(!overdue.isEmpty(), "Dữ liệu dựng ra phải có ít nhất một đơn quá hạn");
    }

    @Test
    @DisplayName("Đơn đã kết thúc không còn nằm ở tab nào")
    void finishedOrdersLeaveAllTabs() {
        int completed = posOrder("COMPLETED");

        for (String tab : TABS) {
            assertTrue(!idsIn(tab).contains(completed),
                    "Đơn đã giao xong còn nằm ở tab " + tab + " thì màn hình chỉ toàn rác");
        }
    }

    @Test
    @DisplayName("Tab tại quầy không lẫn đơn đặt trước và ngược lại")
    void tabsDoNotMixChannels() {
        int online = onlineOrder("CONFIRMED");
        int pos = posOrder("CONFIRMED");

        assertTrue(!idsIn("POS").contains(online), "Tab tại quầy không được có đơn đặt trước");
        assertTrue(!idsIn("SCHEDULED").contains(pos), "Tab đặt trước không được có đơn tại quầy");
    }

    private Set<Integer> idsIn(String tab) {
        Set<Integer> ids = new HashSet<>();
        for (Order o : staffOrders.dashboard(tab)) {
            ids.add(o.getOrderId());
        }
        return ids;
    }

    private int onlineOrder(String status) {
        LocalDateTime pickup = LocalDateTime.now().plusHours(3);
        exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
             "pickup_time, kitchen_release_at, pickup_code, created_at) " +
             "VALUES (?, 'ONLINE_PREORDER', 50000, ?, ?, ?, ?, ?)",
             userId(CUSTOMER_1), status, pickup, pickup.minusMinutes(20),
             "TD" + (System.nanoTime() % 100000000L), LocalDateTime.now());
        return scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");
    }

    private void onlineOverdueOrder() {
        LocalDateTime pastPickup = LocalDateTime.now().minusMinutes(40);
        exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
             "pickup_time, kitchen_release_at, released_to_kds_at, ready_at, pickup_code, created_at) " +
             "VALUES (?, 'ONLINE_PREORDER', 50000, 'READY', ?, ?, ?, ?, ?, ?)",
             userId(CUSTOMER_1), pastPickup, pastPickup.minusMinutes(20),
             pastPickup.minusMinutes(20), pastPickup.minusMinutes(5),
             "TO" + (System.nanoTime() % 100000000L), LocalDateTime.now().minusHours(2));
    }

    private int posOrder(String status) {
        exec("INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, " +
             "order_status, released_to_kds_at, created_at) " +
             "VALUES (NULL, ?, 'POS', 50000, ?, ?, ?)",
             userId(CASHIER_1), status, LocalDateTime.now(), LocalDateTime.now());
        return scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");
    }
}
