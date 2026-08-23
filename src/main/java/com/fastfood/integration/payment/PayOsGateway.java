package com.fastfood.integration.payment;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.config.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Cổng thanh toán PayOS (https://payos.vn/docs).
 *
 * <p>Khác hẳn một cổng chuyển hướng thuần, nơi địa chỉ trả tiền là một phép tính cục bộ: ở đây
 * mình GỌI API của PayOS để xin một liên kết, rồi mới đẩy khách sang đó. Ba hệ quả kéo theo, và
 * cả ba đều nằm trong thiết kế của lớp này:
 *
 * <ul>
 *   <li><b>Mở cổng là một lời gọi mạng.</b> Mất mạng ra Internet thì không có liên kết nào để
 *       đưa khách đi — hỏng ngay tại {@link #initiate}, kèm lý do, chứ không âm thầm.</li>
 *   <li><b>Đường khách quay lại KHÔNG có chữ ký.</b> PayOS gắn {@code code}, {@code id},
 *       {@code cancel}, {@code status}, {@code orderCode} vào địa chỉ quay về và không ký gì
 *       cả, nên tin nó là để bất kỳ ai cũng tự gõ được một địa chỉ "đã trả tiền". Vì vậy đường
 *       quay lại phải hỏi ngược PayOS bằng {@link #lookup} — xem
 *       {@code PayOsReturnServlet}.</li>
 *   <li><b>{@code orderCode} là số và không dùng lại được.</b> PayOS từ chối tạo hai liên kết
 *       cùng một {@code orderCode}, kể cả khi liên kết cũ đã huỷ. Xem {@link #orderCode}.</li>
 * </ul>
 *
 * <p>Chữ ký thì có hai kiểu, và lẫn hai kiểu này là chỗ sai đầu tiên khi tích hợp:
 * <ul>
 *   <li>Lúc XIN liên kết: HMAC-SHA256 trên đúng năm trường theo đúng thứ tự cố định
 *       {@code amount, cancelUrl, description, orderCode, returnUrl} — xem
 *       {@link #signPaymentRequest}.</li>
 *   <li>Lúc PayOS báo kết quả về: HMAC-SHA256 trên TOÀN BỘ khối {@code data}, các khoá xếp theo
 *       bảng chữ cái — xem {@link #signData}.</li>
 * </ul>
 */
public class PayOsGateway implements PaymentGateway {

    private static final Logger LOG = Logger.getLogger(PayOsGateway.class.getName());

    public static final String BASE_URL = "https://api-merchant.payos.vn";

    public static final String RETURN_PATH = "/payment/payos/return";
    public static final String WEBHOOK_PATH = "/payment/payos/webhook";

    /** Mã PayOS trả về khi lời gọi thành công, dùng cho cả khối bọc ngoài lẫn từng giao dịch. */
    public static final String CODE_SUCCESS = "00";

    /**
     * Trang trả tiền của PayOS. Chỉ cần tới đây khi phải dựng lại địa chỉ cho một liên kết ĐÃ
     * tạo trước đó: lời gọi tra cứu trả về mã liên kết chứ không trả về địa chỉ đầy đủ.
     */
    private static final String CHECKOUT_URL = "https://pay.payos.vn/web/";

    /** PayOS đưa mô tả vào nội dung chuyển khoản, mà nội dung chuyển khoản thì ngân hàng cắt ngắn. */
    private static final int DESCRIPTION_MAX = 25;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Trạng thái liên kết còn đang chờ tiền, tức là còn dùng lại được. */
    private static final java.util.Set<String> STILL_OPEN =
            java.util.Set.of("PENDING", "PROCESSING");

    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String returnUrl;
    private final int expiryMinutes;
    private final long orderCodeOffset;
    private final PayOsApi api;

    public PayOsGateway() {
        this(AppConfig.payosClientId(), AppConfig.payosApiKey(), AppConfig.payosChecksumKey(),
             AppConfig.payosReturnUrl(), AppConfig.paymentExpiryMinutes(),
             AppConfig.payosOrderCodeOffset(),
             new HttpPayOsApi(AppConfig.payosClientId(), AppConfig.payosApiKey(),
                              AppConfig.payosBaseUrl()));
    }

    public PayOsGateway(String clientId, String apiKey, String checksumKey, String returnUrl,
                        int expiryMinutes, long orderCodeOffset, PayOsApi api) {
        this.clientId = trim(clientId);
        this.apiKey = trim(apiKey);
        this.checksumKey = trim(checksumKey);
        this.returnUrl = trim(returnUrl);
        this.expiryMinutes = expiryMinutes > 0 ? expiryMinutes : 15;
        this.orderCodeOffset = Math.max(0L, orderCodeOffset);
        this.api = api;
    }

    @Override
    public String getName() {
        return "PAYOS";
    }

    /**
     * Xin PayOS một liên kết trả tiền cho khoản thu {@code paymentId}.
     *
     * <p>Gọi lại với cùng một khoản thu thì KHÔNG tạo thêm liên kết mới: PayOS từ chối vì
     * {@code orderCode} đã tồn tại, và ở đây mình tra lại liên kết cũ rồi trả về đúng nó. Đây
     * không phải chuyện hiếm gặp — màn hình quầy dựng lại mã QR mỗi lần mở trang, và nếu mỗi
     * lần mở lại sinh ra một liên kết mới thì khách có thể trả tiền hai lần vào hai liên kết
     * còn sống của cùng một đơn, mà hệ thống không có đường hoàn tiền tự động.
     */
    @Override
    public PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl) {
        if (!isConfigured()) {
            throw new BusinessException("Cổng thanh toán chưa được cấu hình. Vui lòng liên hệ cửa hàng.");
        }
        long orderCode = orderCode(paymentId);
        long tien = vndAmount(amount);
        String moTa = description(orderId);
        String veLai = returnUrl.isEmpty() ? baseUrl + RETURN_PATH : returnUrl;

        JsonObject body = new JsonObject();
        body.addProperty("orderCode", orderCode);
        body.addProperty("amount", tien);
        body.addProperty("description", moTa);
        body.addProperty("returnUrl", veLai);
        /* Huỷ giữa chừng cũng quay về đúng chỗ ấy: đường quay lại hỏi ngược PayOS để biết
           trạng thái thật, nên nó phân biệt được trả xong với bỏ dở mà không cần hai địa chỉ. */
        body.addProperty("cancelUrl", veLai);
        body.addProperty("expiredAt", Instant.now().getEpochSecond() + expiryMinutes * 60L);
        body.addProperty("signature",
                signPaymentRequest(tien, veLai, moTa, orderCode, veLai));

        JsonObject envelope = api.send("POST", "/v2/payment-requests", body.toString());
        String code = text(envelope, "code");
        if (CODE_SUCCESS.equals(code)) {
            JsonObject data = object(envelope, "data");
            return new PaymentInitResult(text(data, "checkoutUrl"), text(data, "paymentLinkId"),
                                         text(data, "qrCode"));
        }

        /* Không bám vào một mã lỗi cụ thể của PayOS: danh sách mã của họ có thay đổi, và đoán
           sai một con số ở đây thì màn hình quầy hỏng hẳn. Hỏi thẳng "liên kết cho orderCode
           này có sẵn chưa" — câu trả lời đó đúng bất kể họ đánh số lỗi thế nào. */
        PaymentInitResult cuXai = reuseExisting(orderCode, tien);
        if (cuXai != null) {
            return cuXai;
        }
        throw new BusinessException("Không mở được cổng thanh toán PayOS: "
                + moTaLoi(envelope) + ".");
    }

    /**
     * Liên kết đã tạo trước đó cho {@code orderCode} này, nếu nó còn dùng lại được.
     *
     * <p>Trả về null khi không có gì dùng lại được, để bên gọi báo đúng lỗi gốc của PayOS thay
     * vì lỗi của lần tra cứu. Số tiền phải khớp: một liên kết cũ mang số tiền khác nghĩa là
     * {@code orderCode} đang đụng vào dữ liệu của lần cài đặt trước — trả về nó thì khách trả
     * nhầm số tiền của một đơn không còn tồn tại.
     */
    private PaymentInitResult reuseExisting(long orderCode, long amount) {
        JsonObject link;
        try {
            link = lookup(orderCode);
        } catch (BusinessException e) {
            return null;
        }
        if (link == null) {
            return null;
        }
        String status = text(link, "status");
        long soTien = number(link, "amount");
        if (!STILL_OPEN.contains(status)) {
            LOG.warning("PayOS da co lien ket cho orderCode=" + orderCode + " nhung o trang thai "
                    + status + " - khong dung lai duoc");
            return null;
        }
        if (soTien != amount) {
            LOG.severe("PayOS da co lien ket cho orderCode=" + orderCode + " nhung so tien lech:"
                    + " cho " + amount + ", lien ket cu " + soTien
                    + ". Rat co the ma khoan thu dang dung lai so cua lan cai dat truoc —"
                    + " tang payment.payos.orderCodeOffset len roi mo lai.");
            return null;
        }
        String id = text(link, "id");
        LOG.info("Dung lai lien ket PayOS da co cho orderCode=" + orderCode + ", id=" + id);
        return new PaymentInitResult(CHECKOUT_URL + id, id, null);
    }

    /**
     * Hỏi thẳng PayOS trạng thái thật của một liên kết. Đây là chỗ dựa duy nhất đáng tin trên
     * đường khách quay lại, vì các tham số PayOS gắn vào địa chỉ quay về không hề được ký.
     *
     * @return khối {@code data} của lời gọi tra cứu, hoặc null nếu PayOS không có liên kết nào
     */
    public JsonObject lookup(long orderCode) {
        JsonObject envelope = api.send("GET", "/v2/payment-requests/" + orderCode, null);
        if (!CODE_SUCCESS.equals(text(envelope, "code"))) {
            LOG.info("PayOS khong tra ve lien ket cho orderCode=" + orderCode + ": "
                    + moTaLoi(envelope));
            return null;
        }
        return object(envelope, "data");
    }

    /**
     * Kiểm chữ ký PayOS gửi kèm khối {@code data} của webhook.
     *
     * <p>Chỉ dùng cho webhook. Dữ liệu lấy về bằng {@link #lookup} không đi qua đây: nó là câu
     * trả lời của một lời gọi HTTPS do chính mình phát ra kèm khoá API, nên nguồn gốc đã chắc
     * chắn rồi — xem {@code GatewayCallback.isTrusted()}.
     */
    @Override
    public boolean verifySignature(GatewayCallback callback) {
        if (checksumKey.isEmpty() || callback == null) {
            return false;
        }
        String presented = callback.getSignature();
        Map<String, String> data = callback.getParams();
        if (presented == null || presented.isBlank() || data == null || data.isEmpty()) {
            return false;
        }
        byte[] expected = signData(data).getBytes(StandardCharsets.UTF_8);
        byte[] actual = presented.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Mã đơn gửi sang PayOS. PayOS đòi một SỐ, duy nhất trên toàn tài khoản merchant và không
     * bao giờ dùng lại — kể cả liên kết đã huỷ cũng vẫn giữ chỗ con số ấy.
     *
     * <p>Lấy thẳng mã khoản thu nên đọc ngược lại được ở lượt PayOS báo kết quả về, không phải
     * lưu thêm bảng tra nào. Cái giá là: nạp lại cơ sở dữ liệu thì mã khoản thu quay về đếm từ
     * 1 và đụng nguyên vào những mã đã tiêu ở lần cài trước, còn phía PayOS thì không xoá đi
     * được. Lúc ấy tăng {@code payment.payos.orderCodeOffset} lên một khoảng lớn hơn số khoản
     * thu đã tạo là xong — cộng vào đây, trừ ra ở {@link #paymentIdFrom}.
     */
    public long orderCode(int paymentId) {
        return orderCodeOffset + paymentId;
    }

    /** Đọc ngược mã khoản thu ra từ orderCode. Trả về null nếu con số không phải do mình sinh ra. */
    public Integer paymentIdFrom(long orderCode) {
        long id = orderCode - orderCodeOffset;
        return id > 0 && id <= Integer.MAX_VALUE ? (int) id : null;
    }

    public boolean isConfigured() {
        return !clientId.isEmpty() && !apiKey.isEmpty() && !checksumKey.isEmpty();
    }

    public String getClientId() { return clientId; }

    /**
     * Chữ ký của lời XIN liên kết: đúng năm trường, đúng thứ tự này, không xếp lại và không
     * thêm bớt. Khác hẳn {@link #signData} — nhầm hai hàm này thì PayOS trả về "sai chữ ký"
     * mà không nói thêm gì.
     */
    public String signPaymentRequest(long amount, String cancelUrl, String description,
                                     long orderCode, String returnUrl) {
        return hmacSHA256("amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl);
    }

    /**
     * Chữ ký của khối {@code data} PayOS gửi về: mọi khoá có mặt, xếp theo bảng chữ cái, nối
     * thành {@code khoa=gia-tri&...}. Giá trị rỗng thay cho null — đó là điều thư viện chính
     * thức của PayOS làm, và lệch chỗ này thì mọi webhook thật đều bị từ chối.
     */
    public String signData(Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(data).entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue());
        }
        return hmacSHA256(sb.toString());
    }

    /**
     * Trải khối {@code data} JSON thành bộ khoá-giá trị đem ký.
     *
     * <p>Giữ nguyên chữ số như PayOS gửi chứ không đọc thành số rồi in lại: {@code 3000} in lại
     * thành {@code 3000.0} là chữ ký lệch. Trường null thành chuỗi rỗng, mảng và đối tượng lồng
     * nhau giữ nguyên dạng JSON — khối data của webhook chỉ toàn trường phẳng, nhưng để đây cho
     * khỏi hỏng thầm lặng nếu PayOS thêm trường.
     */
    public static Map<String, String> flatten(JsonObject data) {
        Map<String, String> map = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : data.entrySet()) {
            JsonElement v = e.getValue();
            String value;
            if (v == null || v.isJsonNull()) {
                value = "";
            } else if (v.isJsonPrimitive()) {
                value = v.getAsString();
            } else {
                value = v.toString();
            }
            map.put(e.getKey(), value);
        }
        return map;
    }

    /**
     * PayOS chỉ nhận số tiền nguyên. Đơn của hệ thống này luôn là số tròn đồng nên phép làm
     * tròn dưới đây không đổi gì; nó ở đó để một ngày nào đó có phần thập phân thì lỗi rơi vào
     * chỗ thấy được chứ không phải một lời từ chối khó hiểu từ PayOS.
     */
    public static long vndAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Mô tả đi vào NỘI DUNG CHUYỂN KHOẢN, nên phải ngắn và không dấu: ngân hàng cắt phần thừa,
     * mà chuỗi bị cắt thì chữ ký đã ký trên chuỗi đầy đủ không còn khớp.
     */
    public static String description(int orderId) {
        String moTa = "Don hang " + orderId;
        return moTa.length() <= DESCRIPTION_MAX ? moTa : moTa.substring(0, DESCRIPTION_MAX);
    }

    private String hmacSHA256(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            StringBuilder sb = new StringBuilder();
            for (byte b : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Khong tao duoc chu ky PayOS", e);
        }
    }

    private static String moTaLoi(JsonObject envelope) {
        String desc = text(envelope, "desc");
        String code = text(envelope, "code");
        if (desc == null || desc.isBlank()) {
            desc = "cong tra ve ma " + code;
        }
        return desc + " (mã " + code + ")";
    }

    /* Bốn hàm đọc JSON dưới đây để public vì cả hai servlet nhận kết quả PayOS đều cần chúng,
       và chúng nằm ở package khác. Chúng cố ý KHÔNG ném lỗi khi trường thiếu hoặc sai kiểu:
       một gói dữ liệu méo mó phải đi tiếp tới bước kiểm chữ ký rồi chết ở đó, chứ không được
       làm servlet đổ vỡ trước khi kịp trả lời cổng thanh toán. */

    public static String text(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        return v == null || v.isJsonNull() ? null : v.getAsString();
    }

    public static long number(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        try {
            return v == null || v.isJsonNull() ? 0L : v.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            return 0L;
        }
    }

    public static JsonObject object(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        return v != null && v.isJsonObject() ? v.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray array(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        return v != null && v.isJsonArray() ? v.getAsJsonArray() : new JsonArray();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
