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

@WebServlet("/payment/sepay/webhook")
public class SePayWebhookServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(SePayWebhookServlet.class.getName());

    private static final String TRANSFER_IN = "in";

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
            reply(resp, HttpServletResponse.SC_OK, true);
            return;
        }

        GatewayCallback callback = new GatewayCallback();
        callback.setSignature(apiKeyFrom(req));
        callback.setRawPayload(body);
        callback.setSuccess(true);

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
            LOG.severe("Tien vao mang ma thanh toan " + paymentId + " nhung khong xu ly duoc: "
                    + e.getMessage() + ". Noi dung: " + body);
        }
        reply(resp, HttpServletResponse.SC_OK, true);
    }

    static Integer firstPaymentId(SePayGateway sepay, JsonObject payload) {
        for (String field : new String[]{"content", "code", "description"}) {
            Integer id = sepay.paymentIdFrom(text(payload, field));
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static String apiKeyFrom(HttpServletRequest req) {
        return apiKeyFromHeader(req.getHeader("Authorization"));
    }

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
