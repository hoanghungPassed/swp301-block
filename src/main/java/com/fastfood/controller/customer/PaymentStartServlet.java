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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        try {
            String redirectUrl = paymentService.startOnlinePayment(
                    orderId, user.getUserId(), WebUtil.baseUrl(req));
            resp.sendRedirect(redirectUrl + "&orderId=" + orderId);
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
            redirect(req, resp, "/order/track?orderId=" + orderId);
        }
    }
}
