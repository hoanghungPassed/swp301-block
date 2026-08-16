package com.fastfood.controller.api;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.dto.KdsItemView;
import com.fastfood.model.entity.User;
import com.fastfood.service.kitchen.KitchenService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Dữ liệu cho màn hình bếp tự cập nhật.
 * <p>
 * Trang bếp gọi địa chỉ này vài giây một lần thay vì tải lại cả trang, để món mới xuống bếp
 * xuất hiện gần như tức thì mà đầu bếp không phải bấm gì.
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
        for (KdsItemView view : kitchenService.waitingQueue()) {
            JsonObject o = new JsonObject();
            o.addProperty("orderItemId", view.getItem().getOrderItemId());
            o.addProperty("orderId", view.getItem().getOrderId());
            o.addProperty("productName", view.getItem().getProductNameSnapshot());
            o.addProperty("quantity", view.getItem().getQuantity());
            o.addProperty("online", view.isOnline());
            o.addProperty("urgent", view.isUrgent());
            o.addProperty("late", view.isLate());
            o.addProperty("pickupLabel", view.getPickupLabel());
            // Thẻ trên màn hình bếp có hiện số sự cố đang mở, nên dữ liệu cập nhật cũng
            // phải mang theo — thiếu trường này thì thẻ vẽ lại sẽ mất nhãn sự cố.
            o.addProperty("openIssueCount", view.getOpenIssueCount());
            queue.add(o);
        }
        root.add("queue", queue);
        root.addProperty("queueCount", queue.size());
        root.addProperty("myTaskCount", kitchenService.myTasks(user.getUserId()).size());
        // Món đã xong mà chưa ra quầy là thứ dễ bị bỏ quên nhất, nên số đếm của nó cũng phải
        // tự cập nhật — người khác đánh dấu xong hộ thì con số ở đây cũng phải nhúc nhích.
        root.addProperty("handoverCount", kitchenService.awaitingHandover(user.getUserId()).size());
        // Sự cố của cả bếp, không riêng người đang xem: ô chỉ báo trên đầu màn hình bếp đọc
        // con số này, và một sự cố do người khác báo cũng là việc chưa xong của cả ca.
        root.addProperty("openIssueCount", kitchenService.countOpenIssues());

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(root.toString());
    }
}
