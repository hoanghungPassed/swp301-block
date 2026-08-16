package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Cổng thanh toán giả lập dùng khi phát triển và trình bày.
 * <p>
 * Thay vì gọi ra ngoài mạng, nó chuyển khách sang một trang trong chính ứng dụng để
 * bấm chọn thành công hoặc thất bại. Nhờ vậy vẫn diễn được đủ các tình huống thật:
 * thanh toán hỏng rồi thử lại, đơn hết hạn, và cổng gửi kết quả về nhiều lần.
 * <p>
 * Chữ ký vẫn được ký và kiểm tra thật để tầng Service không phải sửa gì khi thay bằng cổng thật.
 * <b>Số tiền nằm trong chữ ký</b>, cùng lý do với ba trường kia: kết quả đi về qua địa chỉ trên
 * thanh trình duyệt, nên sửa {@code &amount=} trong đó là việc ai cũng làm được. Ký cả số tiền
 * thì lần sửa ấy làm chữ ký sai và bị từ chối — đúng như cổng thật xử lý.
 */
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

    /** Chữ ký cho trường hợp khách chọn thanh toán thất bại. */
    public String signFailure(int paymentId, String externalId, String amountText) {
        return sign(paymentId, externalId, false, amountText);
    }

    /**
     * Dạng chuỗi của số tiền dùng khi ký.
     * <p>
     * Phải đi qua đúng một dạng duy nhất ở cả hai đầu, vì {@code 150000} và {@code 150000.00} là
     * cùng một số tiền nhưng là hai chuỗi khác nhau và cho ra hai chữ ký khác nhau.
     */
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
