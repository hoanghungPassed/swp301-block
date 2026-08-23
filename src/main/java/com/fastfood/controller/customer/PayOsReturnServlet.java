package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PayOsGateway;
import com.fastfood.service.shared.PaymentService;
import com.google.gson.JsonObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * PayOS đưa khách quay lại đây sau khi trả tiền xong, hoặc sau khi khách bấm huỷ.
 *
 * <p><b>Không tin một chữ nào trong địa chỉ này.</b> PayOS gắn {@code code}, {@code id},
 * {@code cancel}, {@code status}, {@code orderCode} vào đường quay về và không ký chúng — khác
 * hẳn VNPAY trước đây, nơi cả gói tham số đều nằm dưới một chữ ký HMAC. Tin thẳng {@code
 * status=PAID} đọc từ thanh địa chỉ nghĩa là bất kỳ ai gõ tay được địa chỉ ấy cũng tự cho đơn
 * của mình là đã trả tiền, không cần trả đồng nào.
 *
 * <p>Vì vậy chỗ này chỉ lấy đúng MỘT thứ từ địa chỉ — {@code orderCode}, để biết phải hỏi về
 * khoản nào — rồi tự gọi ngược sang PayOS bằng khoá API của cửa hàng. Trạng thái ghi vào cơ sở
 * dữ liệu là trạng thái PayOS trả lời trong lời gọi đó. Một orderCode bịa ra sẽ không tra được
 * gì, còn một orderCode thật của người khác thì chỉ đọc được đúng trạng thái thật của nó.
 *
 * <p>Không đòi đăng nhập: khách vừa đi vòng qua trang của PayOS, và ở vài trình duyệt hoặc ứng
 * dụng ngân hàng thì lần quay lại này không mang theo cookie phiên.
 */
@WebServlet(PayOsGateway.RETURN_PATH)
public class PayOsReturnServlet extends BaseServlet {

    private static final Logger LOG = Logger.getLogger(PayOsReturnServlet.class.getName());

    /** Câu giải thích cho khách, thay vì đưa nguyên trạng thái tiếng Anh của cổng ra màn hình. */
    private static final Map<String, String> LY_DO = Map.of(
            "CANCELLED", "bạn đã huỷ giao dịch",
            "EXPIRED", "quá hạn chờ thanh toán",
            "FAILED", "ngân hàng từ chối giao dịch");

    /* Bốn kết quả trang quầy phân biệt được. Chuỗi chứ không phải enum vì chúng đi thẳng ra
       JSP, nơi so sánh bằng chuỗi. */
    static final String KET_QUA_DA_TRA = "DA_TRA";
    static final String KET_QUA_CHO = "DANG_CHO";
    static final String KET_QUA_HONG = "HONG";
    static final String KET_QUA_DOI_SOAT = "DOI_SOAT";

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        process(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        process(req, resp);
    }

    private void process(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!(paymentService.getGateway() instanceof PayOsGateway payos)) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long orderCode = orderCode(req);
        Integer paymentId = orderCode == null ? null : payos.paymentIdFrom(orderCode);
        if (paymentId == null) {
            LOG.warning("PayOS dua khach quay lai voi orderCode khong doc duoc: "
                    + req.getParameter("orderCode"));
            WebUtil.flashError(req, "Không đọc được kết quả thanh toán. Nếu ngân hàng đã trừ "
                    + "tiền, vui lòng liên hệ cửa hàng để được đối chiếu.");
            redirect(req, resp, "/order/history");
            return;
        }

        int orderId = paymentService.orderIdOfPayment(paymentId);
        String ketQua = KET_QUA_CHO;
        String failReason = null;

        try {
            JsonObject link = payos.lookup(orderCode);
            if (link == null) {
                /* Không tra được: mã đơn không có thật, hoặc PayOS đang trục trặc. Không kết
                   luận là hỏng — nói "chưa trừ đồng nào" trong khi ngân hàng đã trừ thật là
                   đẩy khách bỏ đi mà không ai đối soát. */
                WebUtil.flashError(req, "Cổng thanh toán chưa xác nhận được giao dịch này. "
                        + "Nếu ngân hàng đã trừ tiền, đơn sẽ tự cập nhật trong ít phút.");
            } else {
                String status = PayOsCallbacks.status(link);
                GatewayCallback callback = PayOsCallbacks.fromLookup(link, payos);
                if (callback == null) {
                    /* Còn PENDING/PROCESSING: tiền chưa về, hoặc ngân hàng còn đang xử lý.
                       Không ghi gì cả — webhook sẽ báo khi xong, và nếu không ai trả thì bộ hẹn
                       giờ quá hạn đóng đơn lại. */
                    LOG.info("PayOS con dang cho cho paymentId=" + paymentId + ", trang thai " + status);
                    WebUtil.flashError(req, "Cổng thanh toán chưa báo nhận được tiền. Trang này "
                            + "sẽ tự cập nhật khi giao dịch hoàn tất.");
                } else {
                    ketQua = ghiNhan(req, callback, status);
                    failReason = KET_QUA_HONG.equals(ketQua) ? lyDo(status) : null;
                }
            }
        } catch (AppException e) {
            LOG.severe("Khong tra cuu duoc ket qua PayOS cho paymentId=" + paymentId + ": "
                    + e.getMessage());
            WebUtil.flashError(req, e.getMessage());
        }

        /* Đơn tại quầy không có chủ, nên không có trang theo dõi đơn nào để đưa khách về: đẩy
           sang /order/track thì khách vãng lai chỉ gặp màn hình đăng nhập. Trả thẳng một trang
           báo kết quả — việc còn lại của khách là quay ra quầy, còn đơn thì thu ngân bấm Xong
           trên màn hình của họ. */
        if (orderId > 0 && paymentService.isCounterOrder(orderId)) {
            req.setAttribute("orderId", orderId);
            req.setAttribute("ketQua", ketQua);
            req.setAttribute("failReason", failReason == null ? "không rõ lý do" : failReason);
            /* Trang này không in các câu nhắn vừa đặt ở trên: chúng viết cho khách đặt trước
               ("đơn hàng đã được xác nhận"), còn đơn tại quầy thì phải đợi thu ngân bấm Xong.
               forward() đã lấy chúng ra khỏi phiên nên chúng cũng không nhảy ra ở trang sau. */
            forward(req, resp, "customer/counter-paid.jsp");
            return;
        }
        redirect(req, resp, orderId > 0 ? "/order/track?orderId=" + orderId : "/order/history");
    }

    /**
     * Ghi nhận kết quả và trả về trạng thái để trang quầy nói đúng chuyện đã xảy ra.
     *
     * <p>Bốn kết quả chứ không phải hai, vì "chưa trả xong" gộp làm một là nói dối khách: một
     * giao dịch còn đang xử lý và một giao dịch mà tiền đã về nhưng đơn hết hiệu lực đều không
     * phải "đã trả", nhưng chỉ trường hợp khách chủ động huỷ mới đúng là "chưa trừ đồng nào".
     */
    private String ghiNhan(HttpServletRequest req, GatewayCallback callback, String status) {
        try {
            PaymentService.CallbackResult result = paymentService.handleCallback(callback);
            switch (result) {
                case PAID:
                    WebUtil.flashSuccess(req, "Thanh toán thành công. Đơn hàng đã được xác nhận.");
                    return KET_QUA_DA_TRA;
                case DUPLICATE:
                    /* Webhook thường về trước lúc khách bấm quay lại, nên lần này bị nhận ra là
                       trùng và bỏ qua. Không báo gì thêm — trang theo dõi đơn đã hiện đúng
                       trạng thái. */
                    return PayOsCallbacks.STATUS_PAID.equals(status) ? KET_QUA_DA_TRA : KET_QUA_HONG;
                case FAILED:
                    WebUtil.flashError(req, "Thanh toán không thành công (" + lyDo(status)
                            + "). Bạn có thể thử lại.");
                    return KET_QUA_HONG;
                case ORDER_GONE:
                    WebUtil.flashError(req, "Đơn hàng đã hết hiệu lực trước khi thanh toán hoàn tất "
                            + "nên đơn không được giữ. Vui lòng liên hệ cửa hàng kèm mã đơn #"
                            + paymentService.orderIdOfPayment(callback.getPaymentId())
                            + " để được đối chiếu khoản tiền vừa thu.");
                    return KET_QUA_DOI_SOAT;
                case AMOUNT_MISMATCH:
                default:
                    WebUtil.flashError(req, "Số tiền nhận được không khớp với giá trị đơn hàng nên "
                            + "đơn chưa được xác nhận. Nhân viên cửa hàng sẽ liên hệ với bạn để "
                            + "đối chiếu.");
                    return KET_QUA_DOI_SOAT;
            }
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
            return KET_QUA_CHO;
        }
    }

    private static String lyDo(String status) {
        return LY_DO.getOrDefault(status == null ? "" : status,
                                  "cổng báo trạng thái " + status);
    }

    private static Long orderCode(HttpServletRequest req) {
        String raw = req.getParameter("orderCode");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
