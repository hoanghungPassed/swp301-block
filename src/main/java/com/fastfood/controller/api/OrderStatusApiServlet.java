package com.fastfood.controller.api;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.ViewFunctions;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.CustomerOrderService;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Trạng thái đơn cho trang theo dõi của khách tự cập nhật.
 * Trả đúng đơn của người đang đăng nhập; đơn của người khác trả về không tìm thấy.
 */
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
            // Kèm sẵn nhãn tiếng Việt và lớp hiển thị, để trang cập nhật huy hiệu trạng thái
            // ngay tại chỗ. Nếu chỉ trả mã trạng thái thì bảng dịch nghĩa phải chép thêm một
            // bản trong JavaScript, và hai bản đó sẽ lệch nhau ngay lần sửa đầu tiên.
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
