package com.fastfood.integration.payment;

public class PaymentInitResult {

    private final String redirectUrl;
    private final String externalTransactionId;

    /*
     * Chuỗi VietQR để khách quét thẳng bằng ứng dụng ngân hàng, khi cổng có sẵn thứ đó.
     *
     * Khác redirectUrl ở chỗ dùng được cho ai: một địa chỉ web mã hoá thành QR thì chỉ camera
     * điện thoại mở được, còn ứng dụng ngân hàng quét vào sẽ báo "mã không hợp lệ". Màn hình
     * quầy quay ra phía khách nên chỗ này đáng giá — có thì vẽ chuỗi này, không có thì lùi về
     * địa chỉ web. Cổng nào không cấp thì cứ để null.
     */
    private final String qrContent;

    public PaymentInitResult(String redirectUrl, String externalTransactionId) {
        this(redirectUrl, externalTransactionId, null);
    }

    public PaymentInitResult(String redirectUrl, String externalTransactionId, String qrContent) {
        this.redirectUrl = redirectUrl;
        this.externalTransactionId = externalTransactionId;
        this.qrContent = qrContent;
    }

    public String getRedirectUrl() { return redirectUrl; }
    public String getExternalTransactionId() { return externalTransactionId; }
    public String getQrContent() { return qrContent; }

    /** Thứ nên mã hoá thành ảnh QR: chuỗi VietQR nếu cổng có cấp, không thì địa chỉ trả tiền. */
    public String qrPayload() {
        return qrContent == null || qrContent.isBlank() ? redirectUrl : qrContent;
    }
}
