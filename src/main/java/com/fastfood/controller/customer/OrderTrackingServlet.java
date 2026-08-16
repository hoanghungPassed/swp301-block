package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.QrCodeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Theo dõi đơn và xem mã nhận hàng.
 * <p>
 * Mã QR được sinh tại chỗ và nhúng thẳng vào trang, không lưu file ảnh — mã chỉ dùng
 * trong vài chục phút nên không đáng để quản lý vòng đời tệp trên đĩa.
 */
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

            /* Đánh dấu đã đọc TRƯỚC khi đọc danh sách và trước khi forward, vì huy hiệu trên
               thanh điều hướng được đếm ngay trong forward. Làm ngược lại thì trang vừa hiện
               đủ tin của đơn này lại vừa mang một huy hiệu nói rằng còn tin chưa đọc, và nó
               chỉ biến mất ở lượt mở trang sau. */
            notificationService.markReadByOrder(user.getUserId(), orderId);
            req.setAttribute("notifications", notificationService.findByOrder(orderId));

            forward(req, resp, "customer/order-tracking.jsp");
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            forward(req, resp, "error/404.jsp");
        }
    }

    /** Khách tự huỷ đơn khi chưa thanh toán, hoặc khi bếp chưa bắt đầu chuẩn bị. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        // Thông báo phải nói đúng chuyện vừa xảy ra: đơn chưa trả tiền thì không có gì để hoàn,
        // hứa hoàn tiền chỉ làm khách ngồi đợi một khoản không bao giờ về. Vì vậy đặt thông báo
        // bên trong thao tác và truyền null ở tham số sau.
        handle(req, resp, () -> {
            boolean refunded = orderService.cancelByCustomer(orderId, user.getUserId());
            WebUtil.flashSuccess(req, refunded
                    ? "Đã huỷ đơn hàng. Toàn bộ số tiền sẽ được hoàn về phương thức thanh toán của bạn."
                    : "Đã huỷ đơn hàng. Đơn chưa thanh toán nên bạn không bị trừ tiền.");
        }, null, "/order/track?orderId=" + orderId);
    }
}
