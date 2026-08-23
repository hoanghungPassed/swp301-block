package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.VnPayGateway;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Đường VNPAY gọi thẳng vào máy chủ để báo kết quả (IPN), không đi qua trình duyệt khách.
 *
 * <p>Đây mới là đường đáng tin để ghi nhận tiền: khách đóng trình duyệt giữa chừng thì đường
 * quay lại không bao giờ chạy, còn IPN vẫn tới. Ngược lại, IPN đòi máy chủ có địa chỉ công khai
 * — chạy thử ở máy cá nhân thì VNPAY không gọi vào được, và lúc đó đường khách quay lại
 * ({@link VnPayReturnServlet}) gánh việc ghi nhận. Cả hai đi vào cùng một
 * {@link PaymentService#handleCallback}, lần thứ hai bị nhận ra là trùng và bỏ qua, nên bật cả
 * hai không thu tiền hai lần.
 *
 * <p>Thân trả về phải là JSON đúng khuôn {@code RspCode}/{@code Message} của VNPAY: trả sai
 * khuôn thì VNPAY coi như chưa nhận được và gọi lại nhiều lần.
 */
@WebServlet("/payment/vnpay/ipn")
public class VnPayIpnServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(VnPayIpnServlet.class.getName());

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
        resp.setContentType("application/json;charset=UTF-8");

        if (!(paymentService.getGateway() instanceof VnPayGateway vnpay)) {
            LOG.warning("Nhan IPN VNPAY nhung cong dang cau hinh khong phai VNPAY - bo qua");
            reply(resp, "99", "Unknown error");
            return;
        }

        GatewayCallback callback = VnPayCallbacks.from(req, vnpay);
        if (callback.getPaymentId() <= 0) {
            LOG.warning("IPN VNPAY khong doc duoc ma thanh toan tu vnp_TxnRef="
                    + req.getParameter("vnp_TxnRef"));
            reply(resp, "01", "Order not found");
            return;
        }
        if (!vnpay.verifySignature(callback)) {
            LOG.severe("Tu choi IPN VNPAY: chu ky khong hop le. Kiem tra payment.vnpay.hashSecret"
                    + " co khop voi chuoi bi mat trong cong quan tri VNPAY khong.");
            reply(resp, "97", "Invalid signature");
            return;
        }

        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            LOG.info("IPN VNPAY cho paymentId=" + callback.getPaymentId() + ": " + result);
            switch (result) {
                case DUPLICATE:
                    reply(resp, "02", "Order already confirmed");
                    break;
                case AMOUNT_MISMATCH:
                    reply(resp, "04", "Invalid amount");
                    break;
                default:
                    /* PAID, FAILED và REFUNDED_ORDER_GONE đều là "đã nhận và đã xử lý xong".
                       Mã 00 nói với VNPAY rằng đừng gọi lại nữa, chứ không nói tiền đã vào. */
                    reply(resp, "00", "Confirm Success");
                    break;
            }
        } catch (AppException e) {
            LOG.severe("IPN VNPAY cho paymentId=" + callback.getPaymentId()
                    + " khong xu ly duoc: " + e.getMessage());
            reply(resp, "01", "Order not found");
        }
    }

    private static void reply(HttpServletResponse resp, String code, String message)
            throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write("{\"RspCode\":\"" + code + "\",\"Message\":\"" + message + "\"}");
    }
}
