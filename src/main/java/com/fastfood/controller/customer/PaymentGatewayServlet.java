package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.MockPaymentGateway;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/payment/gateway")
public class PaymentGatewayServlet extends BaseServlet {

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        requireUser(req);
        if (!(paymentService.getGateway() instanceof MockPaymentGateway)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        int paymentId = WebUtil.getInt(req, "paymentId", 0);
        String txnId = WebUtil.getString(req, "txnId");
        String amount = WebUtil.getString(req, "amount");

        req.setAttribute("paymentId", paymentId);
        req.setAttribute("txnId", txnId);
        req.setAttribute("orderId", WebUtil.getInt(req, "orderId", 0));
        req.setAttribute("amount", amount);
        req.setAttribute("successSig", WebUtil.getString(req, "sig"));
        req.setAttribute("failureSig", paymentService.signFailure(paymentId, txnId, amount));
        forward(req, resp, "customer/payment-gateway.jsp");
    }
}
