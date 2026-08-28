package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/payment/start")
public class PaymentStartServlet extends BaseServlet {

    private final PaymentService paymentService = new PaymentService();

    /**
     * Kiểm tra đơn chờ thanh toán thuộc khách hiện tại, tạo lần thanh toán và chuyển trình duyệt
     * tới checkout URL do cổng thanh toán cung cấp.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        try {
            /* Đi thẳng tới địa chỉ cổng trả về, không gắn thêm tham số nào: địa chỉ ấy là
               của payOS, mọi thứ cần mang theo đã nằm trong liên kết họ vừa cấp. */
            resp.sendRedirect(paymentService.startOnlinePayment(
                    orderId, user.getUserId(), WebUtil.baseUrl(req)));
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
            redirect(req, resp, "/order/track?orderId=" + orderId);
        }
    }
}
