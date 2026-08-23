package com.fastfood.controller.staff;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.service.staff.StaffOrderService;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Trang quầy hỏi lại vài giây một lần: cổng đã báo tiền về chưa?
 *
 * <p>Không dùng chung {@code /api/order/status} được vì đường đó soi theo chủ đơn, mà đơn tại
 * quầy thì không có chủ. Nằm dưới {@code /staff/} nên bộ lọc phân quyền đã chặn sẵn người
 * ngoài — xem web.xml.
 */
@WebServlet("/staff/pos/qr/status")
public class PosQrStatusServlet extends HttpServlet {

    private final StaffOrderService orderService = new StaffOrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int orderId = WebUtil.getInt(req, "orderId", 0);
        resp.setContentType("application/json;charset=UTF-8");
        try {
            Order order = orderService.findById(orderId);
            Payment payment = order.getLatestPayment();

            JsonObject o = new JsonObject();
            o.addProperty("orderId", order.getOrderId());
            o.addProperty("status", order.getOrderStatus());
            o.addProperty("paid", payment != null && payment.isPaid());
            o.addProperty("paymentStatus", payment == null ? "" : payment.getPaymentStatus());
            resp.getWriter().write(o.toString());
        } catch (RuntimeException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"not_found\"}");
        }
    }
}
