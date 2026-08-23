package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.VnPayGateway;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * VNPAY đưa khách quay lại đây sau khi trả tiền xong.
 *
 * <p>Không đòi đăng nhập: khách vừa đi vòng qua trang của VNPAY, và ở vài trình duyệt/ứng dụng
 * ngân hàng thì lần quay lại này không mang theo cookie phiên. Chỗ dựa để tin kết quả không
 * phải là phiên đăng nhập mà là chữ ký trên gói tham số — {@link PaymentService#handleCallback}
 * kiểm lại chữ ký trước khi ghi bất cứ thứ gì.
 */
@WebServlet("/payment/vnpay/return")
public class VnPayReturnServlet extends BaseServlet {

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
        if (!(paymentService.getGateway() instanceof VnPayGateway vnpay)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        GatewayCallback callback = VnPayCallbacks.from(req, vnpay);
        int orderId = 0;

        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            orderId = paymentService.orderIdOfPayment(callback.getPaymentId());
            switch (result) {
                case PAID:
                    WebUtil.flashSuccess(req, "Thanh toán thành công. Đơn hàng đã được xác nhận.");
                    break;
                case FAILED:
                    WebUtil.flashError(req, "Thanh toán không thành công ("
                            + VnPayCallbacks.reason(req) + "). Bạn có thể thử lại.");
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
                    /* IPN thường về trước lúc khách bấm quay lại, nên lần này bị nhận ra là trùng
                       và bỏ qua. Không báo gì thêm — trang theo dõi đơn đã hiện đúng trạng thái. */
                    break;
            }
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
        }

        if (orderId <= 0) {
            orderId = paymentService.orderIdOfPayment(callback.getPaymentId());
        }
        redirect(req, resp, orderId > 0 ? "/order/track?orderId=" + orderId : "/order/history");
    }
}
