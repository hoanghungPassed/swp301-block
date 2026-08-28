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

    /**
     * Tải đầy đủ một đơn thuộc khách hiện tại, tạo QR từ mã nhận hàng và đánh dấu thông báo của
     * đơn là đã đọc trước khi hiển thị trang theo dõi.
     */
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

    /**
     * Khách bỏ đơn chưa thanh toán của mình.
     *
     * <p>Ở lại trang này chứ không đẩy sang giỏ hàng: bỏ xong khách phải nhìn thấy đơn đã
     * chuyển sang hết hiệu lực bằng chính mắt mình, rồi mới tự quyết đi đặt lại hay thôi.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);

        handle(req, resp, () -> orderService.cancelPendingOrder(orderId, user.getUserId()),
                "Đã bỏ đơn #" + orderId + ". Giỏ hàng của bạn vẫn còn nguyên món cũ, "
                        + "vào giỏ là đặt lại được ngay.",
                "/order/track?orderId=" + orderId);
    }
}
