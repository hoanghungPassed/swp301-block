package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.SePayGateway;
import com.fastfood.model.entity.Payment;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Trang chuyển khoản qua mã VietQR của SePay.
 * <p>
 * Thay chỗ của trang cổng giả lập khi {@code payment.gateway.provider=SEPAY}. Khách không đi
 * đâu khỏi ứng dụng: mã QR hiện ngay tại đây, khách quét bằng ứng dụng ngân hàng, tiền vào
 * thẳng tài khoản cửa hàng, rồi SePay gọi về {@code /payment/sepay/webhook}.
 * <p>
 * Trang tự dò trạng thái đơn theo nhịp thay vì bắt khách bấm tải lại. Đây không phải tiện nghi
 * mà là điều kiện để luồng chạy được: lần báo có tiền đi thẳng từ máy chủ SePay tới máy chủ của
 * mình, <b>không đi qua trình duyệt của khách</b>, nên không có gì tự đẩy màn hình của khách
 * sang trạng thái mới. Không dò thì khách ngồi nhìn một trang đứng yên trong khi đơn đã được
 * xác nhận từ lâu.
 * <p>
 * Bên cạnh mã QR còn hiện đủ số tài khoản, tên chủ tài khoản và nội dung chuyển khoản để gõ
 * tay. Máy quét mã hỏng, ứng dụng ngân hàng không đọc được ảnh, hay khách chuyển từ máy khác
 * đều là chuyện thường; thiếu phần này thì những lần ấy là ngõ cụt.
 */
@WebServlet("/payment/sepay")
public class SePayCheckoutServlet extends BaseServlet {

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);

        if (!(paymentService.getGateway() instanceof SePayGateway sepay)) {
            // Đang chạy cổng khác. Dựng mã QR lúc này sẽ dẫn tiền tới một tài khoản chưa cấu
            // hình, mà tiền chuyển nhầm thì không có đường lấy lại tự động.
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
            // Lần thanh toán này đã xong — hoặc tiền đã về, hoặc nó đã hỏng và khách phải lập
            // lần mới. Dựng lại mã QR ở đây là mời khách chuyển tiền lần thứ hai cho một khoản
            // đã đóng, mà tiền chuyển thừa thì không có đường tự lấy lại.
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
