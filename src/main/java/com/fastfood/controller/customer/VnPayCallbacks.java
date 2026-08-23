package com.fastfood.controller.customer;

import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.VnPayGateway;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng một {@link GatewayCallback} từ gói tham số VNPAY gửi về.
 *
 * <p>VNPAY báo kết quả qua hai đường và cả hai đều dùng đúng bộ tham số này: đường khách quay
 * lại trình duyệt ({@link VnPayReturnServlet}) và lời gọi máy-với-máy IPN
 * ({@link VnPayIpnServlet}). Gom vào một chỗ để hai đường không thể đọc lệch nhau — lệch một
 * trường là chữ ký không khớp, và triệu chứng ngoài mặt chỉ là "thanh toán không hợp lệ".
 */
final class VnPayCallbacks {

    /** Tra cứu ý nghĩa các mã hay gặp, để câu báo lỗi nói được điều gì đó thay vì chỉ một con số. */
    private static final Map<String, String> LY_DO = Map.of(
            "24", "bạn đã huỷ giao dịch",
            "51", "tài khoản không đủ số dư",
            "11", "quá hạn chờ thanh toán",
            "12", "thẻ hoặc tài khoản đang bị khoá",
            "13", "sai mật khẩu xác thực OTP",
            "75", "ngân hàng đang bảo trì");

    private VnPayCallbacks() {
    }

    static GatewayCallback from(HttpServletRequest req, VnPayGateway vnpay) {
        Map<String, String> params = params(req);

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        Integer paymentId = vnpay.paymentIdFrom(params.get("vnp_TxnRef"));

        GatewayCallback callback = new GatewayCallback();
        callback.setParams(params);
        callback.setSignature(params.get("vnp_SecureHash"));
        callback.setPaymentId(paymentId == null ? 0 : paymentId);
        callback.setExternalTransactionId(externalId(params));
        callback.setAmount(vnpay.amountFrom(params.get("vnp_Amount")));
        /* Thành công phải đúng cả hai mã. vnp_ResponseCode nói kết quả của lần đặt lệnh, còn
           vnp_TransactionStatus nói tiền đã thực sự về hay chưa; tin mỗi mã đầu thì có lúc ghi
           nhận đã trả cho một giao dịch còn đang treo. */
        callback.setSuccess(VnPayGateway.CODE_SUCCESS.equals(responseCode)
                && VnPayGateway.CODE_SUCCESS.equals(transactionStatus));
        callback.setRawPayload(rawPayload(params));
        return callback;
    }

    /**
     * Mã giao dịch để chống ghi nhận trùng. Ưu tiên mã của VNPAY; thiếu thì lấy mã tham chiếu
     * của mình kèm mã kết quả — vẫn đủ để hai lần báo cùng một kết quả đụng vào ràng buộc duy
     * nhất trên bảng giao dịch.
     */
    private static String externalId(Map<String, String> params) {
        String transactionNo = params.get("vnp_TransactionNo");
        if (transactionNo != null && !transactionNo.isBlank() && !"0".equals(transactionNo.trim())) {
            return "VNPAY-" + transactionNo.trim();
        }
        return "VNPAY-" + params.get("vnp_TxnRef") + "-" + params.get("vnp_ResponseCode");
    }

    static String reason(HttpServletRequest req) {
        String code = req.getParameter("vnp_ResponseCode");
        if (code == null || code.isBlank()) {
            return "không rõ lý do";
        }
        return LY_DO.getOrDefault(code.trim(), "mã lỗi " + code.trim());
    }

    private static Map<String, String> params(HttpServletRequest req) {
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            /* Chỉ lấy tham số của VNPAY. Ai đó gắn thêm tham số lạ vào địa chỉ thì nó không được
               kéo vào phần đem kiểm chữ ký — nếu kéo vào, chữ ký sẽ lệch và một lần trả tiền
               thật bị từ chối. */
            if (e.getKey().startsWith("vnp_") && e.getValue() != null && e.getValue().length > 0) {
                params.put(e.getKey(), e.getValue()[0]);
            }
        }
        return params;
    }

    private static String rawPayload(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
