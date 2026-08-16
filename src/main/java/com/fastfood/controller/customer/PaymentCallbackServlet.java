package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Nhận kết quả từ cổng thanh toán.
 * <p>
 * Địa chỉ này không yêu cầu đăng nhập vì cổng thanh toán gọi vào từ máy chủ của họ,
 * không mang theo phiên của khách. Bù lại, mọi dữ liệu đều phải qua kiểm tra chữ ký,
 * và mã giao dịch được chống trùng ở tầng cơ sở dữ liệu — chi tiết trong
 * {@link PaymentService#handleCallback}.
 */
@WebServlet("/payment/callback")
public class PaymentCallbackServlet extends BaseServlet {

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        process(req, resp);
    }

    private void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        GatewayCallback callback = new GatewayCallback();
        callback.setPaymentId(WebUtil.getInt(req, "paymentId", 0));
        callback.setExternalTransactionId(WebUtil.getString(req, "txnId"));
        callback.setSuccess(WebUtil.getBoolean(req, "success"));
        callback.setAmount(parseAmount(WebUtil.getString(req, "amount")));
        callback.setSignature(WebUtil.getString(req, "sig"));
        callback.setRawPayload(req.getQueryString());

        int orderId = WebUtil.getInt(req, "orderId", 0);

        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            if (orderId <= 0) {
                orderId = paymentService.orderIdOfPayment(callback.getPaymentId());
            }
            switch (result) {
                case PAID:
                    WebUtil.flashSuccess(req, "Thanh toán thành công. Đơn hàng đã được xác nhận.");
                    break;
                case FAILED:
                    WebUtil.flashError(req, "Thanh toán không thành công. Bạn có thể thử lại.");
                    break;
                case REFUNDED_ORDER_GONE:
                    WebUtil.flashError(req, "Đơn hàng đã hết hiệu lực trước khi thanh toán hoàn tất. "
                            + "Khoản tiền vừa thu đã được hoàn lại, bạn vui lòng đặt đơn mới.");
                    break;
                case AMOUNT_MISMATCH:
                    WebUtil.flashError(req, "Số tiền nhận được không khớp với giá trị đơn hàng nên "
                            + "đơn chưa được xác nhận. Nhân viên cửa hàng sẽ liên hệ với bạn để "
                            + "đối chiếu.");
                    break;
                case DUPLICATE:
                default:
                    // Cổng gửi lại kết quả đã xử lý — trang theo dõi đơn đã hiện đúng trạng thái
                    break;
            }
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
        }
        redirect(req, resp, "/order/track?orderId=" + orderId);
    }

    /** Số tiền cổng báo đã thu; {@code null} khi thiếu hoặc không đọc được. */
    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
