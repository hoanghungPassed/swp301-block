package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.QrCodeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/order/track")
public class OrderTrackingServlet extends BaseServlet {

    private final CustomerOrderService orderService = new CustomerOrderService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);

        try {
            Order order = orderService.findForCustomer(orderId, user.getUserId());
            req.setAttribute("order", order);
            if (order.getPickupCode() != null) {
                req.setAttribute("qrDataUri", QrCodeUtil.toDataUri(order.getPickupCode(), 220));
            }

            notificationService.markReadByOrder(user.getUserId(), orderId);
            req.setAttribute("notifications", notificationService.findByOrder(orderId));

            forward(req, resp, "customer/order-tracking.jsp");
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            forward(req, resp, "error/404.jsp");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        handle(req, resp, () -> {
            boolean refunded = orderService.cancelByCustomer(orderId, user.getUserId());
            WebUtil.flashSuccess(req, refunded
                    ? "Đã huỷ đơn hàng. Toàn bộ số tiền sẽ được hoàn về phương thức thanh toán của bạn."
                    : "Đã huỷ đơn hàng. Đơn chưa thanh toán nên bạn không bị trừ tiền.");
        }, null, "/order/track?orderId=" + orderId);
    }
}
