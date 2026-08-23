package com.fastfood.integration.payment;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GatewayCallback {

    private int paymentId;
    private String externalTransactionId;
    private boolean success;
    private BigDecimal amount;
    private String signature;
    private String rawPayload;

    /* Toàn bộ tham số cổng gửi về, giữ nguyên tên gốc. Cổng chuyển hướng như VNPAY ký trên CẢ
       gói tham số chứ không phải trên vài trường mình chọn ra, nên muốn kiểm lại chữ ký thì
       phải còn đủ gói. Cổng nào không cần thì cứ để trống. */
    private Map<String, String> params = Collections.emptyMap();

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

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) {
        this.params = params == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
