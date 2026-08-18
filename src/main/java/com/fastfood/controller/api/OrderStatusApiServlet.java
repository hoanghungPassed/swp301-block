package com.fastfood.controller.api;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.ViewFunctions;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.customer.CustomerOrderService;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/order/status")
public class OrderStatusApiServlet extends HttpServlet {

    private final CustomerOrderService orderService = new CustomerOrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = WebUtil.currentUser(req);
        if (user == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        int orderId = WebUtil.getInt(req, "orderId", 0);

        resp.setContentType("application/json;charset=UTF-8");
        try {
            Order order = orderService.findForCustomer(orderId, user.getUserId());
            JsonObject o = new JsonObject();
            o.addProperty("orderId", order.getOrderId());
            o.addProperty("status", order.getOrderStatus());
            o.addProperty("statusLabel", ViewFunctions.orderStatus(order.getOrderStatus()));
            o.addProperty("statusClass", ViewFunctions.orderStatusClass(order.getOrderStatus()));
            o.addProperty("releaseState", order.getReleaseState().name());
            o.addProperty("pickupCode", order.getPickupCode());
            o.addProperty("overdue", order.isOverdue());
            o.addProperty("pickupTime", DateTimeUtil.format(order.getPickupTime()));
            resp.getWriter().write(o.toString());
        } catch (RuntimeException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"not_found\"}");
        }
    }
}
