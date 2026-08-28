package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.SePayGateway;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/payment/sepay")
public class SePayCheckoutServlet extends BaseServlet {

    private final PaymentService paymentService = new PaymentService();

    /**
     * Kiểm tra Payment thuộc customer và cổng hiện tại là SePay, sau đó dựng thông tin VietQR
     * cùng thời hạn để hiển thị trang chờ chuyển khoản.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);

        if (!(paymentService.getGateway() instanceof SePayGateway sepay)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        int paymentId = WebUtil.getInt(req, "paymentId", 0);
        Payment payment;
        try {
            payment = paymentService.findForCustomer(paymentId, user.getUserId());
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
            redirect(req, resp, "/order/history");
            return;
        }

        if (!payment.isPending()) {
            redirect(req, resp, "/order/track?orderId=" + payment.getOrderId());
            return;
        }

        String content = sepay.transferContent(payment.getPaymentId());

        req.setAttribute("payment", payment);
        req.setAttribute("orderId", payment.getOrderId());
        req.setAttribute("transferContent", content);
        req.setAttribute("qrImageUrl", sepay.qrImageUrl(payment.getAmount(), content));
        req.setAttribute("bank", sepay.getBank());
        req.setAttribute("accountNumber", sepay.getAccountNumber());
        req.setAttribute("accountName", sepay.getAccountName());
        req.setAttribute("expiryMinutes", AppConfig.paymentExpiryMinutes());
        forward(req, resp, "customer/payment-sepay.jsp");
    }
}
