package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class MockPaymentGateway implements PaymentGateway {

    private static final String SECRET = AppConfig.get("payment.gateway.secretKey", "SANDBOX_SECRET");

    @Override
    public String getName() {
        return "MOCK";
    }

    @Override
    public PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl) {
        String externalId = "MOCK-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        String amountText = amount.toPlainString();
        String signature = sign(paymentId, externalId, true, amountText);
        String url = baseUrl + "/payment/gateway"
                + "?paymentId=" + paymentId
                + "&txnId=" + externalId
                + "&amount=" + amountText
                + "&sig=" + signature;
        return new PaymentInitResult(url, externalId);
    }

    @Override
    public boolean verifySignature(GatewayCallback callback) {
        String expected = sign(callback.getPaymentId(), callback.getExternalTransactionId(),
                callback.isSuccess(), amountText(callback.getAmount()));
        return expected.equals(callback.getSignature());
    }

    public String signFailure(int paymentId, String externalId, String amountText) {
        return sign(paymentId, externalId, false, amountText);
    }

    private static String amountText(BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }

    private String sign(int paymentId, String externalId, boolean success, String amountText) {
        try {
            String raw = paymentId + "|" + externalId + "|" + success + "|"
                    + (amountText == null ? "" : amountText) + "|" + SECRET;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("Khong tao duoc chu ky", e);
        }
    }
}
