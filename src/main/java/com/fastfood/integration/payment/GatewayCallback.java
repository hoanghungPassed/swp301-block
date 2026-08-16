package com.fastfood.integration.payment;

import java.math.BigDecimal;

/**
 * Dữ liệu cổng thanh toán gửi về sau khi khách thanh toán xong.
 * <p>
 * {@code externalTransactionId} là mã định danh giao dịch phía cổng. Cùng một giao dịch
 * có thể được gửi về nhiều lần, nên mã này được lưu với ràng buộc duy nhất để lần thứ hai
 * bị từ chối và tiền không bị ghi nhận trùng.
 * <p>
 * {@code amount} là số tiền cổng báo đã thu, và nó là một trường <b>bắt buộc</b> chứ không phải
 * thông tin kèm theo cho đẹp. Nói "đã thanh toán" mà không nói bao nhiêu thì không đủ để xác
 * nhận một đơn hàng: với SePay, số tiền do chính khách gõ vào ứng dụng ngân hàng, nên khách
 * hoàn toàn có thể sửa mã QR 200.000đ thành một lệnh chuyển 10.000đ mang đúng nội dung ấy.
 * {@code PaymentService.handleCallback} đối chiếu trường này với số tiền của đơn.
 */
public class GatewayCallback {

    private int paymentId;
    private String externalTransactionId;
    private boolean success;
    private BigDecimal amount;
    private String signature;
    private String rawPayload;

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
}
