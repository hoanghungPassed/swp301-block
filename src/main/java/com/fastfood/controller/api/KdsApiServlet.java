package com.fastfood.controller.api;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.dto.Dtos.KdsItemView;
import com.fastfood.model.dto.Dtos.KdsOrderView;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Hàng chờ bếp dưới dạng JSON để màn bếp tự làm mới. Trả về nguyên cả hàng chờ; trình duyệt
 * tự cắt theo trang đang xem, nên đổi trang không cần hỏi lại máy chủ.
 */
@WebServlet("/api/kds/queue")
public class KdsApiServlet extends HttpServlet {

    private final KitchenService kitchenService = new KitchenService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = WebUtil.currentUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        JsonObject root = new JsonObject();
        JsonArray queue = new JsonArray();
        for (KdsOrderView order : kitchenService.waitingOrders()) {
            queue.add(toJson(order));
        }
        root.add("queue", queue);
        root.addProperty("queueCount", queue.size());
        root.addProperty("myOrderCount", kitchenService.myOrders(user.getUserId()).size());
        root.addProperty("handoverCount", kitchenService.ordersAwaitingHandover(user.getUserId()).size());
        root.addProperty("openIssueCount", kitchenService.countOpenIssues());

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(root.toString());
    }

    private JsonObject toJson(KdsOrderView order) {
        JsonObject o = new JsonObject();
        o.addProperty("orderId", order.getOrderId());
        o.addProperty("online", order.isOnline());
        o.addProperty("urgent", order.isUrgent());
        o.addProperty("late", order.isLate());
        o.addProperty("pickupLabel", order.getPickupLabel());
        o.addProperty("itemCount", order.getItemCount());
        o.addProperty("totalQuantity", order.getTotalQuantity());
        o.addProperty("openIssueCount", order.getOpenIssueCount());

        JsonArray items = new JsonArray();
        for (KdsItemView view : order.getItems()) {
            JsonObject item = new JsonObject();
            item.addProperty("orderItemId", view.getItem().getOrderItemId());
            item.addProperty("name", view.getItem().getProductNameSnapshot());
            item.addProperty("quantity", view.getItem().getQuantity());
            items.add(item);
        }
        o.add("items", items);
        return o;
    }
}
