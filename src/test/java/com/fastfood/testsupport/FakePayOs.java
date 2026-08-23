package com.fastfood.testsupport;

import com.fastfood.integration.payment.PayOsApi;
import com.fastfood.integration.payment.PayOsGateway;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PayOS giả: giữ lại các liên kết đã tạo trong bộ nhớ và trả lời đúng khuôn JSON thật.
 *
 * <p>Có mặt để phần lớn đường đi của tiền được kiểm mà không cần mạng và không cần khoá thật.
 * Thứ bị thay ở đây chỉ là ĐƯỜNG TRUYỀN ({@link PayOsApi}); phần ký, phần dựng mã đơn, phần
 * đọc kết quả về vẫn là {@link PayOsGateway} thật, nên một lỗi ở đó vẫn làm bài kiểm tra đỏ.
 *
 * <p>Ba hành vi của PayOS thật được giữ lại vì mã sản phẩm dựa vào chúng:
 * <ul>
 *   <li>tạo hai liên kết cùng một {@code orderCode} thì lần thứ hai bị từ chối;</li>
 *   <li>tra cứu trả về {@code amountPaid} và danh sách giao dịch, không trả về checkoutUrl;</li>
 *   <li>webhook được ký trên toàn bộ khối {@code data}.</li>
 * </ul>
 */
public class FakePayOs implements PayOsApi {

    public static final String CLIENT_ID = "test-client-id";
    public static final String API_KEY = "test-api-key";
    public static final String CHECKSUM_KEY = "test-checksum-key-0123456789";

    /** Mã lỗi PayOS trả khi mã đơn đã dùng rồi. Con số chỉ để giống thật — mã sản phẩm cố ý không bám vào nó. */
    private static final String CODE_ORDER_EXISTS = "231";

    private final Map<Long, JsonObject> links = new LinkedHashMap<>();

    private int soLanTao;

    /** Cổng thật, chỉ khác ở chỗ đường truyền đi qua bản giả này. */
    public PayOsGateway gateway() {
        return gateway(0L);
    }

    public PayOsGateway gateway(long orderCodeOffset) {
        return new PayOsGateway(CLIENT_ID, API_KEY, CHECKSUM_KEY, "", 15, orderCodeOffset, this);
    }

    /** Đếm số lần thật sự tạo liên kết mới — để kiểm rằng mở lại trang quầy không đẻ thêm liên kết. */
    public int soLanTaoLienKet() {
        return soLanTao;
    }

    @Override
    public JsonObject send(String method, String path, String jsonBody) {
        if ("POST".equalsIgnoreCase(method) && path.equals("/v2/payment-requests")) {
            return taoLienKet(JsonParser.parseString(jsonBody).getAsJsonObject());
        }
        if ("GET".equalsIgnoreCase(method) && path.startsWith("/v2/payment-requests/")) {
            return traCuu(Long.parseLong(path.substring(path.lastIndexOf('/') + 1)));
        }
        return loi("101", "Duong dan khong ho tro trong ban gia: " + method + " " + path);
    }

    private JsonObject taoLienKet(JsonObject body) {
        long orderCode = body.get("orderCode").getAsLong();
        if (links.containsKey(orderCode)) {
            return loi(CODE_ORDER_EXISTS, "Đơn thanh toán đã tồn tại");
        }
        soLanTao++;
        long amount = body.get("amount").getAsLong();
        String id = "link-" + orderCode;

        JsonObject link = new JsonObject();
        link.addProperty("id", id);
        link.addProperty("orderCode", orderCode);
        link.addProperty("amount", amount);
        link.addProperty("amountPaid", 0);
        link.addProperty("amountRemaining", amount);
        link.addProperty("status", "PENDING");
        link.addProperty("createdAt", "2026-01-01T00:00:00+07:00");
        link.add("transactions", new JsonArray());
        link.add("cancellationReason", com.google.gson.JsonNull.INSTANCE);
        link.add("canceledAt", com.google.gson.JsonNull.INSTANCE);
        links.put(orderCode, link);

        JsonObject data = new JsonObject();
        data.addProperty("bin", "970422");
        data.addProperty("accountNumber", "113366668888");
        data.addProperty("accountName", "CUA HANG FASTFOOD");
        data.addProperty("amount", amount);
        data.addProperty("description", body.get("description").getAsString());
        data.addProperty("orderCode", orderCode);
        data.addProperty("currency", "VND");
        data.addProperty("paymentLinkId", id);
        data.addProperty("status", "PENDING");
        data.addProperty("checkoutUrl", "https://pay.payos.vn/web/" + id);
        data.addProperty("qrCode", "00020101021238570010A00000072701270006970422011311336666888802"
                + orderCode);
        return ok(data);
    }

    private JsonObject traCuu(long orderCode) {
        JsonObject link = links.get(orderCode);
        return link == null ? loi("101", "Không tìm thấy đơn thanh toán") : ok(link);
    }

    /** Tiền về đủ: đổi liên kết sang PAID và ghi một giao dịch ngân hàng, đúng như PayOS thật. */
    public void traTien(long orderCode, BigDecimal soTien, String reference) {
        JsonObject link = links.get(orderCode);
        if (link == null) {
            throw new IllegalStateException("Chua co lien ket nao cho orderCode=" + orderCode);
        }
        long tien = soTien.longValueExact();
        link.addProperty("status", "PAID");
        link.addProperty("amountPaid", tien);
        link.addProperty("amountRemaining", 0);

        JsonObject txn = new JsonObject();
        txn.addProperty("reference", reference);
        txn.addProperty("amount", tien);
        txn.addProperty("accountNumber", "113366668888");
        txn.addProperty("description", "Don hang");
        txn.addProperty("transactionDateTime", "2026-01-01 12:00:00");
        JsonArray txns = new JsonArray();
        txns.add(txn);
        link.add("transactions", txns);
    }

    /** Khách bấm huỷ hoặc liên kết hết hạn. */
    public void dong(long orderCode, String status) {
        JsonObject link = links.get(orderCode);
        if (link == null) {
            throw new IllegalStateException("Chua co lien ket nao cho orderCode=" + orderCode);
        }
        link.addProperty("status", status);
    }

    /**
     * Một lần webhook PayOS báo tiền về, đã ký sẵn bằng đúng checksum key của bản giả này.
     *
     * <p>Ký bằng chính {@link PayOsGateway#signData} — cùng hàm mà mã sản phẩm dùng để kiểm.
     * Nghe thì như tự chấm bài mình, nhưng thứ đang được kiểm ở đây không phải là thuật toán
     * HMAC mà là việc hai bên có ĐỌC CÙNG MỘT khối dữ liệu hay không: thêm bớt một trường giữa
     * lúc dựng và lúc kiểm là chữ ký lệch ngay. Còn khuôn chuỗi đem ký thì
     * {@code PayOsGatewayTest} kiểm riêng bằng giá trị tính tay.
     */
    public JsonObject webhook(long orderCode, BigDecimal soTien, String reference, boolean thanhCong) {
        JsonObject data = new JsonObject();
        data.addProperty("orderCode", orderCode);
        data.addProperty("amount", soTien.longValueExact());
        data.addProperty("description", "Don hang");
        data.addProperty("accountNumber", "113366668888");
        data.addProperty("reference", reference);
        data.addProperty("transactionDateTime", "2026-01-01 12:00:00");
        data.addProperty("currency", "VND");
        data.addProperty("paymentLinkId", "link-" + orderCode);
        data.addProperty("code", thanhCong ? PayOsGateway.CODE_SUCCESS : "99");
        data.addProperty("desc", thanhCong ? "Thành công" : "Giao dịch thất bại");
        data.addProperty("counterAccountBankId", "970415");
        data.addProperty("counterAccountName", "NGUYEN VAN A");
        data.addProperty("counterAccountNumber", "0011223344");
        data.add("virtualAccountName", com.google.gson.JsonNull.INSTANCE);
        data.add("virtualAccountNumber", com.google.gson.JsonNull.INSTANCE);

        JsonObject payload = new JsonObject();
        payload.addProperty("code", thanhCong ? PayOsGateway.CODE_SUCCESS : "99");
        payload.addProperty("desc", thanhCong ? "success" : "fail");
        payload.addProperty("success", thanhCong);
        payload.add("data", data);
        payload.addProperty("signature", gateway().signData(PayOsGateway.flatten(data)));
        return payload;
    }

    private static JsonObject ok(JsonObject data) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("code", PayOsGateway.CODE_SUCCESS);
        envelope.addProperty("desc", "success");
        envelope.add("data", data);
        return envelope;
    }

    private static JsonObject loi(String code, String desc) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("code", code);
        envelope.addProperty("desc", desc);
        return envelope;
    }
}
