package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.SePayGateway;
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
import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Nhận báo có tiền về từ SePay.
 *
 * <h2>Địa chỉ này khác mọi địa chỉ khác trong hệ thống ở ba điểm</h2>
 * <ol>
 *   <li><b>Không có phiên đăng nhập.</b> SePay gọi vào từ máy chủ của họ. Vì vậy nó nằm trong
 *       danh sách trang công khai của {@code AuthenticationFilter} và được miễn mã chống giả
 *       mạo ở {@code CsrfFilter} — cả hai đều là quyết định có chủ ý, và cả hai đều được
 *       {@code RoutePolicyTest} canh.</li>
 *   <li><b>Ai cũng gọi được.</b> Bù lại cho hai chỗ miễn trừ trên: khoá API ở header
 *       {@code Authorization} phải đúng, số tiền phải khớp với đơn, và mã giao dịch được chống
 *       trùng ở tầng cơ sở dữ liệu. Ba lớp đó nằm ở {@code SePayGateway.verifySignature} và
 *       {@code PaymentService.handleCallback}, không lớp nào ở đây.</li>
 *   <li><b>Người đọc câu trả lời là một cái máy.</b> SePay coi là thành công khi nhận được mã
 *       200 kèm {@code {"success": true}}; thiếu thì họ gọi lại theo dãy Fibonacci, tối đa 7
 *       lần trong 5 giờ.</li>
 * </ol>
 *
 * <h2>Vì sao phần lớn tình huống đều trả về thành công</h2>
 * "Thành công" ở đây nghĩa là <b>đã nhận và đã xử lý xong</b>, không phải "đã ghi nhận tiền cho
 * một đơn". Tài khoản ngân hàng của cửa hàng còn nhận cả những khoản chẳng liên quan tới đơn
 * nào — tiền nhà cung cấp trả lại, khách chuyển nhầm, chủ cửa hàng tự nạp vào. SePay báo hết,
 * và với những khoản đó thì không có việc gì để làm. Trả lỗi sẽ khiến họ gọi lại bảy lần cho
 * cùng một khoản tiền mà lần nào cũng không có gì để làm.
 * <p>
 * Chỉ hai tình huống thật sự trả lỗi: khoá API sai, và lỗi ngoài dự tính. Cả hai đều là thứ
 * gọi lại có thể cứu được, và đều là thứ phải lộ ra chứ không được nuốt.
 */
@WebServlet("/payment/sepay/webhook")
public class SePayWebhookServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(SePayWebhookServlet.class.getName());

    /** Chỉ tiền vào mới đáng quan tâm; {@code out} là lệnh chuyển đi của chính cửa hàng. */
    private static final String TRANSFER_IN = "in";

    /**
     * Tiền tố gắn trước mã biến động số dư của SePay khi lưu vào nhật ký giao dịch.
     * Cột đó có ràng buộc duy nhất và dùng chung cho mọi cổng, nên mã của hai cổng khác nhau
     * phải không có đường đụng nhau — SePay đánh số tuần tự, và bản giả lập cũng vậy.
     */
    private static final String EXTERNAL_ID_PREFIX = "SEPAY-";

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        if (!(paymentService.getGateway() instanceof SePayGateway sepay)) {
            LOG.warning("Nhan webhook SePay nhung cong dang cau hinh khong phai SEPAY - bo qua");
            reply(resp, HttpServletResponse.SC_NOT_FOUND, false);
            return;
        }

        String body = readBody(req);
        JsonObject payload;
        try {
            payload = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            LOG.warning("Webhook SePay gui du lieu khong doc duoc: " + e.getMessage());
            // Gọi lại cũng sẽ gửi đúng nội dung hỏng ấy, nên nhận rồi thôi.
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        GatewayCallback callback = new GatewayCallback();
        callback.setSignature(apiKeyFrom(req));
        callback.setRawPayload(body);
        callback.setSuccess(true);   // SePay chỉ báo khi tiền đã thật sự vào tài khoản

        // Kiểm khoá API trước cả khi đọc nội dung: dữ liệu chưa chứng minh được là của SePay thì
        // chưa có lý do gì để tin bất kỳ trường nào trong đó.
        if (!sepay.verifySignature(callback)) {
            LOG.severe("Tu choi webhook SePay: khoa API khong dung. Kiem tra payment.sepay.apiKey"
                    + " co khop voi khoa dat trong bang dieu khien SePay khong.");
            reply(resp, HttpServletResponse.SC_UNAUTHORIZED, false);
            return;
        }

        String transferType = text(payload, "transferType");
        if (!TRANSFER_IN.equalsIgnoreCase(transferType)) {
            LOG.fine("Bo qua bien dong khong phai tien vao: " + transferType);
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        Integer paymentId = firstPaymentId(sepay, payload);
        if (paymentId == null) {
            // Tiền vào tài khoản nhưng không mang mã thanh toán nào. Hoàn toàn có thể là một
            // khoản không liên quan tới cửa hàng, nên chỉ ghi lại chứ không coi là lỗi.
            LOG.info("Tien vao khong kem ma thanh toan, bo qua: "
                    + text(payload, "content"));
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        callback.setPaymentId(paymentId);
        callback.setExternalTransactionId(EXTERNAL_ID_PREFIX + text(payload, "id"));
        callback.setAmount(amount(payload));

        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            LOG.info("Webhook SePay cho paymentId=" + paymentId + ": " + result);
        } catch (AppException e) {
            // Mã thanh toán đọc được nhưng không dẫn tới đơn nào — tiền đã vào tài khoản thật mà
            // hệ thống không biết ghi vào đâu. Gọi lại không sửa được, nên nhận cho xong và để
            // lại một dòng đủ to: khoản này phải có người đối soát bằng tay.
            LOG.severe("Tien vao mang ma thanh toan " + paymentId + " nhung khong xu ly duoc: "
                    + e.getMessage() + ". Noi dung: " + body);
        }
        reply(resp, HttpServletResponse.SC_OK, true);
    }

    /**
     * Mã thanh toán đọc từ nội dung chuyển khoản.
     * <p>
     * Thử lần lượt ba trường vì không trường nào chắc chắn có. {@code content} là nội dung
     * nguyên văn nên thử trước; {@code code} chỉ có giá trị khi đã khai báo quy tắc bóc mã trong
     * bảng điều khiển SePay; {@code description} là phần mô tả dài, đôi khi giữ lại nội dung mà
     * ngân hàng đã cắt bớt ở hai trường kia.
     */
    static Integer firstPaymentId(SePayGateway sepay, JsonObject payload) {
        for (String field : new String[]{"content", "code", "description"}) {
            Integer id = sepay.paymentIdFrom(text(payload, field));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    /** Khoá API SePay gửi ở dạng {@code Authorization: Apikey <khoá>}. */
    private static String apiKeyFrom(HttpServletRequest req) {
        return apiKeyFromHeader(req.getHeader("Authorization"));
    }

    /**
     * Bóc khoá ra khỏi giá trị header {@code Authorization}.
     * <p>
     * Nhận cả trường hợp không có tên lược đồ đứng trước: SePay cho phép tự đặt tên header và
     * giá trị trong bảng điều khiển, nên một cấu hình gửi thẳng khoá trần là chuyện có thật.
     * Chấp nhận cả hai dạng ở đây không nới lỏng gì cả — khoá vẫn phải khớp từng byte.
     */
    static String apiKeyFromHeader(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, SePayGateway.API_KEY_SCHEME, 0,
                                SePayGateway.API_KEY_SCHEME.length())) {
            return value.substring(SePayGateway.API_KEY_SCHEME.length()).trim();
        }
        return value;
    }

    private static BigDecimal amount(JsonObject payload) {
        try {
            return payload.has("transferAmount") && !payload.get("transferAmount").isJsonNull()
                    ? payload.get("transferAmount").getAsBigDecimal()
                    : null;
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            return null;
        }
    }

    private static String text(JsonObject payload, String field) {
        return payload.has(field) && !payload.get(field).isJsonNull()
                ? payload.get(field).getAsString()
                : null;
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
