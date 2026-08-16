package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Bắt đầu thanh toán: tạo bản ghi giao dịch rồi chuyển khách sang trang thanh toán.
 * <p>
 * Trang thanh toán là trang nào thì do cổng đang cấu hình quyết định — trang giả lập có nút
 * chọn kết quả, hay trang mã QR chuyển khoản của SePay. Servlet này không biết và không cần
 * biết: nó đưa địa chỉ gốc của ứng dụng xuống, cổng trả lại địa chỉ đầy đủ.
 */
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
