package com.fastfood.integration.payment;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.config.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cổng thanh toán VNPAY (chuẩn tích hợp 2.1.0).
 *
 * <p>Khác SePay ở chỗ đây là cổng CHUYỂN HƯỚNG: mình dựng một địa chỉ có chữ ký rồi đẩy khách
 * sang trang của VNPAY, khách chọn ngân hàng và trả tiền ở bên đó, xong VNPAY đưa khách quay
 * lại {@code vnp_ReturnUrl} kèm kết quả. Chỗ dựa để tin kết quả đó là chữ ký HMAC-SHA512 tính
 * từ chuỗi bí mật hai bên dùng chung — không có chữ ký hợp lệ thì bất kỳ ai cũng tự gõ được
 * một địa chỉ "đã trả tiền" vào thanh địa chỉ.
 *
 * <p>Hai điểm dễ sai khi ký, cả hai đều làm chữ ký lệch mà không báo lỗi rõ ràng:
 * <ul>
 *   <li>Tham số phải xếp theo thứ tự bảng chữ cái, và giá trị phải được mã hoá URL TRƯỚC khi
 *       nối chuỗi — chuỗi đem ký và chuỗi truy vấn gửi đi phải giống hệt nhau.</li>
 *   <li>{@code vnp_Amount} tính bằng đồng nhân 100 và không có phần thập phân.</li>
 * </ul>
 */
public class VnPayGateway implements PaymentGateway {

    /** Máy chủ thử nghiệm. Lên thật thì đổi payment.vnpay.payUrl sang vnpayment.vn (bỏ "sandbox."). */
    public static final String SANDBOX_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    public static final String RETURN_PATH = "/payment/vnpay/return";

    /** Mã VNPAY trả về khi giao dịch thành công, dùng cho cả vnp_ResponseCode lẫn vnp_TransactionStatus. */
    public static final String CODE_SUCCESS = "00";

    private static final String VERSION = "2.1.0";
    private static final String HMAC_ALGORITHM = "HmacSHA512";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final int expiryMinutes;

    public VnPayGateway() {
        this(AppConfig.vnpayTmnCode(), AppConfig.vnpayHashSecret(), AppConfig.vnpayPayUrl(),
             AppConfig.vnpayReturnUrl(), AppConfig.paymentExpiryMinutes());
    }

    public VnPayGateway(String tmnCode, String hashSecret, String payUrl, String returnUrl,
                        int expiryMinutes) {
        this.tmnCode = trim(tmnCode);
        this.hashSecret = trim(hashSecret);
        this.payUrl = trim(payUrl).isEmpty() ? SANDBOX_PAY_URL : trim(payUrl);
        this.returnUrl = trim(returnUrl);
        this.expiryMinutes = expiryMinutes > 0 ? expiryMinutes : 15;
    }

    @Override
    public String getName() {
        return "VNPAY";
    }

    @Override
    public PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl) {
        if (!isConfigured()) {
            throw new BusinessException("Cổng thanh toán chưa được cấu hình. Vui lòng liên hệ cửa hàng.");
        }
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        String txnRef = txnRef(paymentId, now);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", vnpAmount(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl.isEmpty() ? baseUrl + RETURN_PATH : returnUrl);
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", STAMP.format(now));
        /* Hạn của VNPAY đặt bằng đúng hạn giữ đơn (BR-13). Để dài hơn thì khách vẫn trả được
           tiền sau khi đơn đã hết hiệu lực, và hệ thống phải đi hoàn lại — xem nhánh
           REFUNDED_ORDER_GONE trong PaymentService. */
        params.put("vnp_ExpireDate", STAMP.format(now.plusMinutes(expiryMinutes)));

        String query = queryString(params);
        return new PaymentInitResult(
                payUrl + "?" + query + "&vnp_SecureHash=" + sign(params), txnRef);
    }

    /**
     * Kiểm chữ ký trên toàn bộ tham số VNPAY gửi về. Dùng chung cho cả đường khách quay lại
     * lẫn lời gọi IPN — cách ký của hai đường là một.
     */
    @Override
    public boolean verifySignature(GatewayCallback callback) {
        if (hashSecret.isEmpty() || callback == null) {
            return false;
        }
        String presented = callback.getSignature();
        Map<String, String> params = callback.getParams();
        if (presented == null || presented.isBlank() || params == null || params.isEmpty()) {
            return false;
        }
        byte[] expected = sign(params).getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Mã tham chiếu gửi sang VNPAY: mã thanh toán, kèm dấu thời gian cho khỏi trùng. */
    public String txnRef(int paymentId, LocalDateTime at) {
        return paymentId + "-" + STAMP.format(at);
    }

    /** Đọc ngược mã thanh toán ra từ vnp_TxnRef. Trả về null nếu chuỗi không phải do mình sinh ra. */
    public Integer paymentIdFrom(String txnRef) {
        if (txnRef == null) {
            return null;
        }
        String head = txnRef.trim();
        int dash = head.indexOf('-');
        if (dash >= 0) {
            head = head.substring(0, dash);
        }
        try {
            int id = Integer.parseInt(head);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Đổi vnp_Amount (đồng nhân 100) về số tiền thật để đối chiếu với khoản phải thu. */
    public BigDecimal amountFrom(String vnpAmount) {
        if (vnpAmount == null || vnpAmount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(vnpAmount.trim()).movePointLeft(2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isConfigured() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
    }

    public String getTmnCode() { return tmnCode; }
    public String getPayUrl()  { return payUrl; }

    /**
     * Chữ ký HMAC-SHA512 trên các tham số đã xếp theo thứ tự bảng chữ cái. Hai trường chữ ký
     * tự nó bị loại ra — chúng không nằm trong phần được ký.
     */
    public String sign(Map<String, String> params) {
        return hmacSHA512(hashSecret, hashData(params));
    }

    private static String hashData(Map<String, String> params) {
        return join(params, false);
    }

    private static String queryString(Map<String, String> params) {
        return join(params, true);
    }

    /**
     * Chuỗi đem ký và chuỗi truy vấn gửi đi chỉ khác nhau ở chỗ tên tham số có được mã hoá hay
     * không — mọi tên tham số của VNPAY đều là chữ ASCII nên trên thực tế hai chuỗi giống hệt.
     * Giữ đúng một hàm dựng ra cả hai để chúng không thể lệch nhau về sau.
     */
    private static String join(Map<String, String> params, boolean encodeName) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            String name = e.getKey();
            String value = e.getValue();
            if (value == null || value.isEmpty()
                    || "vnp_SecureHash".equals(name) || "vnp_SecureHashType".equals(name)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encodeName ? encode(name) : name).append('=').append(encode(value));
        }
        return sb.toString();
    }

    private static String vnpAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Khong tao duoc chu ky VNPAY", e);
        }
    }

    /* VNPAY mã hoá theo US-ASCII chứ không phải UTF-8: đây là điều bản tích hợp mẫu của họ làm,
       và chữ ký chỉ khớp khi hai bên mã hoá giống hệt nhau. Vì vậy vnp_OrderInfo viết không dấu. */
    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("US-ASCII luon co mat", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
