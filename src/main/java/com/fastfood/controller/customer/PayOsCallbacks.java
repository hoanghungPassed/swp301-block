package com.fastfood.controller.customer;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;

/**
 * Dựng một {@link GatewayCallback} từ những gì PayOS nói về một khoản thu.
 *
 * <p>PayOS nói qua hai đường, và hai đường mang hai hình dạng dữ liệu KHÁC HẲN nhau — đây là
 * chỗ khác lớn nhất so với VNPAY trước đây, nơi cả hai đường dùng chung một bộ tham số:
 *
 * <ul>
 *   <li><b>Webhook</b> — PayOS gọi thẳng vào máy chủ, gửi khối {@code data} phẳng kèm chữ ký
 *       trên chính khối ấy. Tin được nhờ chữ ký. Xem {@link #fromWebhook}.</li>
 *   <li><b>Khách quay lại</b> — PayOS chỉ gắn vài tham số vào địa chỉ và KHÔNG ký gì cả, nên
 *       không tin được. Đường ấy phải tự đi hỏi PayOS, và câu trả lời có hình dạng của một
 *       lần tra cứu liên kết. Xem {@link #fromLookup}.</li>
 * </ul>
 *
 * <p>Điểm phải khớp giữa hai đường: <b>mã giao dịch</b>. Cả hai cùng dựng nó từ mã tham chiếu
 * ngân hàng ({@code reference}), nên khi cả webhook lẫn lượt khách quay lại cùng báo về một
 * lần trả tiền, lần thứ hai đụng ràng buộc duy nhất trên bảng giao dịch và bị bỏ qua. Dựng
 * lệch nhau ở đây là ghi nhận cùng một khoản tiền hai lần.
 */
final class PayOsCallbacks {

    private static final String EXTERNAL_ID_PREFIX = "PAYOS-";

    /** Trạng thái liên kết nghĩa là "tiền đã về đủ". */
    static final String STATUS_PAID = "PAID";

    /** Những trạng thái nghĩa là "khoản này chấm dứt, không còn chờ tiền nữa". */
    private static final java.util.Set<String> STATUS_DEAD =
            java.util.Set.of("CANCELLED", "EXPIRED", "FAILED");

    /** Những trạng thái nghĩa là "còn đang chờ, chưa kết luận được gì". */
    private static final java.util.Set<String> STATUS_OPEN =
            java.util.Set.of("PENDING", "PROCESSING", "UNDERPAID");

    private PayOsCallbacks() {
    }

    /**
     * Từ khối {@code data} của webhook. Chữ ký giữ nguyên để {@link PayOsGateway#verifySignature}
     * kiểm lại — {@link GatewayCallback#getParams()} phải là đúng khối đã được ký, không thêm
     * bớt trường nào.
     */
    static GatewayCallback fromWebhook(JsonObject payload, PayOsGateway payos) {
        JsonObject data = PayOsGateway.object(payload, "data");

        GatewayCallback callback = new GatewayCallback();
        callback.setParams(PayOsGateway.flatten(data));
        callback.setSignature(PayOsGateway.text(payload, "signature"));
        callback.setRawPayload(payload.toString());

        Integer paymentId = payos.paymentIdFrom(PayOsGateway.number(data, "orderCode"));
        callback.setPaymentId(paymentId == null ? 0 : paymentId);
        callback.setAmount(tien(data, "amount"));
        callback.setExternalTransactionId(EXTERNAL_ID_PREFIX + maGiaoDich(data));
        /* Thành công phải đúng cả hai mức. Mức ngoài nói lời gọi webhook có hợp lệ không, mức
           trong (data.code) mới nói giao dịch có thành công không; tin mỗi mức ngoài thì một
           lần trả tiền hỏng vẫn được ghi là đã trả. */
        callback.setSuccess(thanhCong(payload, "success")
                && PayOsGateway.CODE_SUCCESS.equals(PayOsGateway.text(data, "code")));
        return callback;
    }

    /**
     * Từ kết quả tra cứu liên kết ({@link PayOsGateway#lookup}).
     *
     * <p>Đánh dấu {@code trusted}: dữ liệu này là câu trả lời của một lời gọi HTTPS do chính
     * hệ thống phát ra kèm khoá API tới máy chủ PayOS, không phải thứ đọc từ thanh địa chỉ của
     * khách. Không có chữ ký nào để kiểm, và cũng không cần.
     *
     * @return null khi liên kết còn đang chờ — chưa có gì để ghi nhận
     */
    static GatewayCallback fromLookup(JsonObject link, PayOsGateway payos) {
        String status = PayOsGateway.text(link, "status");
        if (status == null || STATUS_OPEN.contains(status)) {
            return null;
        }
        boolean paid = STATUS_PAID.equals(status);
        if (!paid && !STATUS_DEAD.contains(status)) {
            /* Trạng thái lạ: PayOS thêm cái gì đó mà bản tích hợp này chưa biết. Không đoán —
               đoán "đã trả" là mất món, đoán "hỏng" là đóng nhầm một khoản còn sống. Để lượt
               webhook hoặc bộ hẹn giờ quá hạn xử lý. */
            return null;
        }

        JsonObject giaoDich = giaoDichDauTien(link);

        GatewayCallback callback = new GatewayCallback();
        callback.setTrusted(true);
        callback.setRawPayload(link.toString());
        callback.setSuccess(paid);

        Integer paymentId = payos.paymentIdFrom(PayOsGateway.number(link, "orderCode"));
        callback.setPaymentId(paymentId == null ? 0 : paymentId);
        /* Số tiền lấy ở amountPaid chứ không phải amount: amount là số ghi trên liên kết, còn
           amountPaid mới là số ngân hàng thực sự chuyển. Trả thiếu thì hai số này lệch nhau, và
           PaymentService phải nhìn thấy số thật để đưa vào nhánh đối soát tay. */
        callback.setAmount(tien(link, "amountPaid"));

        String reference = giaoDich == null ? null : PayOsGateway.text(giaoDich, "reference");
        callback.setExternalTransactionId(EXTERNAL_ID_PREFIX + (
                reference != null && !reference.isBlank()
                        ? reference
                        /* Không có giao dịch ngân hàng nào để bám vào (khách huỷ, hoặc liên kết
                           hết hạn): lấy mã liên kết kèm trạng thái. Vẫn đủ duy nhất để lượt báo
                           thứ hai bị nhận ra là trùng. */
                        : PayOsGateway.text(link, "id") + "-" + status));
        return callback;
    }

    /** Trạng thái liên kết đọc được từ một lần tra cứu, hoặc null. */
    static String status(JsonObject link) {
        return PayOsGateway.text(link, "status");
    }

    /** Kiểm tra trạng thái PayOS còn đang xử lý nên chưa được kết luận thành công/thất bại. */
    static boolean conDangCho(String status) {
        return status != null && STATUS_OPEN.contains(status);
    }

    /**
     * Mã tham chiếu ngân hàng của một lần webhook. Thiếu thì lấy mã liên kết — vẫn đủ để chống
     * ghi nhận trùng, chỉ kém ở chỗ không tra ngược ra sao kê được.
     */
    private static String maGiaoDich(JsonObject data) {
        String reference = PayOsGateway.text(data, "reference");
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return PayOsGateway.text(data, "paymentLinkId") + "-" + PayOsGateway.text(data, "code");
    }

    /** Lấy giao dịch đầu tiên trong dữ liệu tra cứu link thanh toán, nếu có. */
    private static JsonObject giaoDichDauTien(JsonObject link) {
        for (JsonElement e : PayOsGateway.array(link, "transactions")) {
            if (e != null && e.isJsonObject()) {
                return e.getAsJsonObject();
            }
        }
        return null;
    }

    /** Đọc số tiền JSON an toàn thành BigDecimal, trả null khi không hợp lệ. */
    private static BigDecimal tien(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        if (v == null || v.isJsonNull()) {
            return null;
        }
        try {
            return v.getAsBigDecimal();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            return null;
        }
    }

    /** Đọc mã kết quả PayOS và xác định giao dịch có thành công hay không. */
    private static boolean thanhCong(JsonObject o, String field) {
        JsonElement v = o == null ? null : o.get(field);
        if (v == null || v.isJsonNull() || !v.isJsonPrimitive()) {
            return false;
        }
        /* PayOS gửi success dưới dạng boolean JSON, nhưng đọc cả chuỗi "true" cho chắc: một
           thư viện HTTP nào đó ở giữa đổi kiểu là toàn bộ luồng tiền im lặng ngừng chạy. */
        return v.getAsJsonPrimitive().isBoolean()
                ? v.getAsBoolean()
                : "true".equalsIgnoreCase(v.getAsString());
    }
}
