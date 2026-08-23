package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.fastfood.service.shared.PaymentService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Đường PayOS gọi thẳng vào máy chủ để báo tiền về, không đi qua trình duyệt khách.
 *
 * <p>Đây là đường đáng tin nhất để ghi nhận tiền: khách đóng trình duyệt giữa chừng thì lượt
 * quay lại ({@link PayOsReturnServlet}) không bao giờ chạy, còn webhook vẫn tới. Ngược lại,
 * webhook đòi máy chủ có địa chỉ công khai và phải khai báo trong bảng điều khiển
 * my.payos.vn — chạy thử ở máy cá nhân thì PayOS không gọi vào được, và lúc đó lượt khách quay
 * lại gánh việc ghi nhận. Cả hai đi vào cùng một {@link PaymentService#handleCallback} và cùng
 * dựng mã giao dịch từ mã tham chiếu ngân hàng, nên lần thứ hai bị nhận ra là trùng: bật cả
 * hai không thu tiền hai lần.
 *
 * <p><b>Luôn trả 2xx sau khi chữ ký đã qua.</b> PayOS coi mã ngoài 2xx là "chưa nhận được" và
 * gọi lại nhiều lần; trả lỗi vì một chuyện bên mình (đơn không tìm thấy, cơ sở dữ liệu trục
 * trặc) chỉ đổi một sự cố im lặng thành một trận dội webhook. Chuyện hỏng đi vào log ở mức
 * SEVERE để người trực nhìn thấy, còn PayOS thì được trả lời là đã nhận.
 */
@WebServlet(PayOsGateway.WEBHOOK_PATH)
public class PayOsWebhookServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(PayOsWebhookServlet.class.getName());

    /* Gói thử PayOS gửi vào lúc đăng ký địa chỉ webhook. Nó được ký đúng như một lần trả tiền
       thật nên chữ ký không phân biệt được, mà mã đơn trong đó lại là một con số tròn trĩnh —
       nếu đúng lúc ấy hệ thống có một khoản thu mang số đó, đang chờ, và đúng 3.000 đồng, thì
       gói thử sẽ ghi nhận nó là đã trả. Nhận diện bằng CẢ mã đơn lẫn mã tham chiếu để một giao
       dịch thật không thể lọt vào nhánh này. */
    private static final long PING_ORDER_CODE = 123L;
    private static final String PING_REFERENCE = "TF230204212323";

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        if (!(paymentService.getGateway() instanceof PayOsGateway payos)) {
            LOG.warning("Nhan webhook PayOS nhung cong dang cau hinh khong phai PAYOS - bo qua");
            reply(resp, HttpServletResponse.SC_NOT_FOUND, false);
            return;
        }

        String body = readBody(req);
        JsonObject payload;
        try {
            payload = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            LOG.warning("Webhook PayOS gui du lieu khong doc duoc: " + e.getMessage());
            reply(resp, HttpServletResponse.SC_BAD_REQUEST, false);
            return;
        }

        GatewayCallback callback = PayOsCallbacks.fromWebhook(payload, payos);

        if (!payos.verifySignature(callback)) {
            LOG.severe("Tu choi webhook PayOS: chu ky khong hop le. Kiem tra"
                    + " payment.payos.checksumKey co khop voi Checksum Key trong my.payos.vn khong.");
            reply(resp, HttpServletResponse.SC_UNAUTHORIZED, false);
            return;
        }

        if (laGoiThu(payload)) {
            LOG.info("Bo qua goi thu PayOS gui luc dang ky webhook - dia chi webhook da thong.");
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        if (callback.getPaymentId() <= 0) {
            LOG.warning("Webhook PayOS khong doc duoc ma khoan thu tu orderCode="
                    + PayOsGateway.number(PayOsGateway.object(payload, "data"), "orderCode")
                    + ". Kiem tra payment.payos.orderCodeOffset.");
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            LOG.info("Webhook PayOS cho paymentId=" + callback.getPaymentId() + ": " + result);
        } catch (AppException e) {
            LOG.severe("Webhook PayOS cho paymentId=" + callback.getPaymentId()
                    + " khong xu ly duoc: " + e.getMessage() + ". Noi dung: " + body);
        }
        reply(resp, HttpServletResponse.SC_OK, true);
    }

    private static boolean laGoiThu(JsonObject payload) {
        JsonObject data = PayOsGateway.object(payload, "data");
        return PayOsGateway.number(data, "orderCode") == PING_ORDER_CODE
                && PING_REFERENCE.equals(PayOsGateway.text(data, "reference"));
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return sb.toString();
    }

    private static void reply(HttpServletResponse resp, int status, boolean success)
            throws IOException {
        resp.setStatus(status);
        resp.getWriter().write("{\"success\":" + success + "}");
    }
}
