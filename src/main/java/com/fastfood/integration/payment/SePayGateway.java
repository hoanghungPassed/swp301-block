package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thu tiền qua SePay (https://sepay.vn) bằng chuyển khoản ngân hàng có mã VietQR.
 *
 * <h2>Vì sao luồng này khác hẳn cổng chuyển hướng</h2>
 * SePay không giữ tiền và cũng không có trang thanh toán của riêng nó. Tiền đi thẳng từ tài
 * khoản của khách vào tài khoản ngân hàng của cửa hàng; việc duy nhất SePay làm là đọc biến
 * động số dư của tài khoản đó rồi gọi về báo. Hệ quả:
 * <ul>
 *   <li>Không chuyển khách đi đâu cả. {@link #initiate} trả về địa chỉ một trang <b>trong chính
 *       ứng dụng</b>, nơi hiện mã QR để khách quét bằng ứng dụng ngân hàng.</li>
 *   <li>Không có "thanh toán thất bại". Khách hoặc chuyển tiền, hoặc không chuyển — và không
 *       chuyển thì không có lần gọi về nào hết, đơn tự hết hiệu lực theo bộ hẹn giờ như thường.
 *       Vì vậy lớp này chỉ sinh ra kết quả thành công.</li>
 *   <li>Lần gọi về <b>không mang theo mã đơn</b>, vì lệnh chuyển khoản của khách không có chỗ
 *       nào để mang. Cách duy nhất nối nó lại với một lần thanh toán là nội dung chuyển khoản —
 *       xem {@link #transferContent} và {@link #paymentIdFrom}.</li>
 * </ul>
 *
 * <h2>Chỗ dựa để tin một lần báo có tiền</h2>
 * Địa chỉ nhận báo là địa chỉ công khai, ai gọi vào cũng được, nên nó phải tự chứng minh. SePay
 * gửi kèm khoá API do chính mình đặt trong bảng điều khiển của họ, ở header
 * {@code Authorization: Apikey <khoá>}. {@link #verifySignature} so khoá đó với cấu hình.
 * <p>
 * <b>Chưa cấu hình khoá thì từ chối tất.</b> Nhánh mặc định phải là từ chối chứ không phải cho
 * qua: một webhook không xác thực là chỗ bất kỳ ai cũng tự báo mình đã trả tiền được, chỉ cần
 * đoán ra mã thanh toán — mà mã thanh toán thì chạy tuần tự.
 * <p>
 * Việc chống ghi nhận trùng và đối chiếu số tiền không nằm ở đây mà ở
 * {@code PaymentService.handleCallback}, chung cho mọi cổng.
 */
public class SePayGateway implements PaymentGateway {

    /** Ảnh mã VietQR do SePay dựng sẵn; không phải gọi API nào, cứ nhúng thẳng vào thẻ img. */
    private static final String QR_ENDPOINT = "https://qr.sepay.vn/img";

    /** Header SePay gửi khoá API, dạng {@code Apikey <khoá>}. */
    public static final String API_KEY_SCHEME = "Apikey";

    /**
     * Số chữ số tối đa của mã thanh toán khi đọc từ nội dung chuyển khoản.
     * Chặn trên để một dãy số dài bất thường không bị hiểu thành mã hợp lệ rồi tràn kiểu int.
     */
    private static final int MAX_PAYMENT_ID_DIGITS = 9;

    private final String accountNumber;
    private final String bank;
    private final String accountName;
    private final String apiKey;
    private final String contentPrefix;

    /** Đọc thẳng nội dung chuyển khoản, dựng sẵn một lần vì mỗi lần gọi về đều dùng tới. */
    private final Pattern referencePattern;

    public SePayGateway() {
        this(AppConfig.sepayAccountNumber(), AppConfig.sepayBank(), AppConfig.sepayAccountName(),
             AppConfig.sepayApiKey(), AppConfig.sepayContentPrefix());
    }

    public SePayGateway(String accountNumber, String bank, String accountName,
                        String apiKey, String contentPrefix) {
        this.accountNumber = trim(accountNumber);
        this.bank = trim(bank);
        this.accountName = trim(accountName);
        this.apiKey = trim(apiKey);
        this.contentPrefix = trim(contentPrefix).isEmpty() ? "FF" : trim(contentPrefix).toUpperCase();
        // Tiền tố phải đứng đầu một "từ": SePay tự chèn mã của họ vào nội dung chuyển khoản
        // (kiểu "SEVN63DC8E5C chuyen tien"), mà mã đó là chữ số mười sáu nên hoàn toàn có thể
        // chứa đúng hai chữ "FF" ở giữa. Không chốt hai đầu thì "SEVNFF12ABCD" sẽ bị đọc thành
        // mã thanh toán 12 và ghi nhận tiền cho một đơn không liên quan.
        this.referencePattern = Pattern.compile(
                "(?<![A-Za-z0-9])" + Pattern.quote(this.contentPrefix)
                        + "([0-9]{1," + MAX_PAYMENT_ID_DIGITS + "})(?![0-9])",
                Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String getName() {
        return "SEPAY";
    }

    /**
     * Mở trang mã QR trong ứng dụng.
     * <p>
     * Mã giao dịch phía cổng chưa tồn tại ở bước này — nó là mã biến động số dư, chỉ có sau khi
     * tiền thật sự vào tài khoản. Nên chỗ đó trả về nội dung chuyển khoản: đây mới là thứ nối
     * lần thanh toán này với lệnh chuyển tiền sắp tới, và cũng là thứ cần đọc lại khi đối soát.
     */
    @Override
    public PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl) {
        return new PaymentInitResult(baseUrl + "/payment/sepay?paymentId=" + paymentId,
                                     transferContent(paymentId));
    }

    /**
     * So khoá API SePay gửi kèm với khoá trong cấu hình.
     * <p>
     * So từng byte cho đủ độ dài thay vì dùng {@code equals}: {@code equals} dừng ngay ở byte
     * đầu tiên lệch, nên thời gian trả lời rò rỉ số ký tự đầu đã đoán đúng và khoá có thể bị
     * dò dần từng ký tự. Đây là địa chỉ công khai nên hoàn toàn có thể bị gọi hàng nghìn lần.
     */
    @Override
    public boolean verifySignature(GatewayCallback callback) {
        if (apiKey.isEmpty()) {
            return false;
        }
        String presented = callback == null ? null : callback.getSignature();
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.trim().getBytes(StandardCharsets.UTF_8),
                                     apiKey.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ nội dung chuyển khoản

    /**
     * Nội dung khách phải ghi khi chuyển khoản, ví dụ {@code FF57}.
     * <p>
     * Ngắn và chỉ gồm chữ với số là có chủ ý: nhiều ngân hàng cắt bớt nội dung, bỏ dấu và viết
     * hoa toàn bộ trước khi gửi đi. Mọi thứ dài hơn hoặc có dấu đều có nguy cơ về tới nơi trong
     * hình dạng khác.
     */
    public String transferContent(int paymentId) {
        return contentPrefix + paymentId;
    }

    /**
     * Đọc ngược mã thanh toán ra từ nội dung chuyển khoản.
     *
     * @return mã thanh toán, hoặc {@code null} nếu nội dung không chứa mã nào của hệ thống —
     *         chuyện hoàn toàn bình thường, vì tài khoản ngân hàng của cửa hàng còn nhận cả
     *         những khoản không liên quan tới đơn hàng nào.
     */
    public Integer paymentIdFrom(String content) {
        if (content == null) {
            return null;
        }
        Matcher m = referencePattern.matcher(content);
        if (!m.find()) {
            return null;
        }
        try {
            int id = Integer.parseInt(m.group(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ dữ liệu cho trang QR

    /**
     * Địa chỉ ảnh mã VietQR đã điền sẵn số tài khoản, số tiền và nội dung.
     * Khách quét bằng ứng dụng ngân hàng là mọi ô đã có đủ, không phải gõ tay ô nào.
     */
    public String qrImageUrl(BigDecimal amount, String transferContent) {
        return QR_ENDPOINT
                + "?acc=" + encode(accountNumber)
                + "&bank=" + encode(bank)
                + "&amount=" + amount.toBigInteger()
                + "&des=" + encode(transferContent);
    }

    /**
     * Đã đủ tham số để thu tiền thật chưa.
     * Thiếu bất kỳ thứ nào trong bốn thứ này thì mã QR dựng ra sẽ dẫn tiền đi sai chỗ, hoặc
     * tiền về mà không ai xác thực được — cả hai đều tệ hơn là không bật SePay.
     */
    public boolean isConfigured() {
        return !accountNumber.isEmpty() && !bank.isEmpty() && !apiKey.isEmpty();
    }

    public String getAccountNumber() { return accountNumber; }
    public String getBank()          { return bank; }
    public String getAccountName()   { return accountName; }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 luon co mat", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
