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

    /* Toàn bộ khối dữ liệu cổng gửi về, giữ nguyên tên trường gốc. Cổng ký trên CẢ khối chứ
       không phải trên vài trường mình chọn ra, nên muốn kiểm lại chữ ký thì phải còn đủ khối —
       thêm hay bớt một trường là chữ ký lệch. Cổng nào không cần thì cứ để trống. */
    private Map<String, String> params = Collections.emptyMap();

    /*
     * Dữ liệu này lấy về bằng một lời gọi máy-với-máy do chính hệ thống phát ra, nên không cần
     * kiểm chữ ký nữa.
     *
     * Đặt ra vì PayOS: khi khách quay lại từ trang trả tiền, PayOS KHÔNG ký các tham số trên
     * địa chỉ quay về, nên đường quay lại phải hỏi ngược PayOS xem khoản này thực sự đã trả
     * chưa. Câu trả lời đi qua HTTPS tới đúng máy chủ của PayOS, kèm khoá API của cửa hàng —
     * nguồn gốc đã chắc chắn hơn bất kỳ chữ ký nào đọc từ thanh địa chỉ của khách.
     *
     * Mặc định là false, và chỉ đúng một chỗ trong toàn hệ thống được bật lên: nơi vừa đọc
     * xong câu trả lời của cổng. Bật ở chỗ nào khác là mở đúng cái cửa mà chữ ký sinh ra để
     * đóng.
     */
    private boolean trusted;

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

    public boolean isTrusted() { return trusted; }
    public void setTrusted(boolean trusted) { this.trusted = trusted; }

    public Map<String, String> getParams() { return params; }
    public void setParams(Map<String, String> params) {
        this.params = params == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}
