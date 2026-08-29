package com.fastfood.controller.staff;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.QrCodeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.integration.payment.PaymentInitResult;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.PaymentService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Màn hình quầy quay ra phía khách: mã QR để khách trả tiền, và nút Xong cho thu ngân.
 *
 * <p>Đơn đã tồn tại từ lúc thu ngân bấm "Khách quét mã QR" nhưng chưa xuống bếp — xem
 * {@link StaffOrderService#createPosQrOrder}. Trang này hỏi cổng lấy chỗ trả tiền mỗi lần mở
 * rồi mã hoá thành ảnh QR ngay tại máy chủ, nên không phụ thuộc dịch vụ sinh ảnh nào bên
 * ngoài: mất mạng ra Internet thì khách không trả được tiền, nhưng đó là chuyện của cổng thanh
 * toán chứ không phải của cái ảnh.
 *
 * <p>Mở lại trang không sinh thêm liên kết trả tiền mới: PayOS nhận ra mã đơn đã có và trả về
 * đúng liên kết cũ — xem {@code PayOsGateway.initiate}. Nếu mỗi lần mở lại đẻ ra một liên kết
 * nữa thì cùng một đơn có mấy chỗ trả tiền còn sống cùng lúc, mà hệ thống thì không có đường
 * hoàn tiền tự động.
 */
@WebServlet("/staff/pos/qr")
public class PosQrServlet extends BaseServlet {

    /** Đủ lớn để quét được từ khoảng cách một quầy thu ngân, vẫn vừa trên màn hình đứng. */
    private static final int QR_SIZE = 320;

    private final StaffOrderService orderService = new StaffOrderService();
    private final PaymentService paymentService = new PaymentService();

    /**
     * Kiểm tra đúng đơn QR tại quầy, lấy checkout URL từ gateway và sinh ảnh QR cho khách quét.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);

        Order order;
        try {
            order = orderService.findById(orderId);
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
            redirect(req, resp, "/staff/pos");
            return;
        }

        Payment payment = order.getLatestPayment();
        if (order.isOnline() || payment == null || payment.isCash()) {
            WebUtil.flashError(req, "Đơn này không thu bằng mã QR tại quầy.");
            redirect(req, resp, "/staff/order/detail?orderId=" + orderId);
            return;
        }
        /* Đã bấm Xong rồi (đơn đã xuống bếp) hoặc đơn đã bị huỷ: không còn gì để quét. Đưa
           thẳng sang trang đơn thay vì hiện một mã QR không dẫn tới đâu. */
        if (order.getReleasedToKdsAt() != null || !"CONFIRMED".equals(order.getOrderStatus())) {
            redirect(req, resp, "/staff/order/detail?orderId=" + orderId);
            return;
        }

        if (!payment.isPaid()) {
            try {
                PaymentInitResult link = paymentService.paymentLink(payment.getPaymentId(),
                        orderId, payment.getAmount(), WebUtil.baseUrl(req));
                req.setAttribute("payUrl", link.getRedirectUrl());
                /* Vẽ chuỗi VietQR nếu cổng cấp, không thì vẽ địa chỉ trả tiền — xem
                   PaymentInitResult.qrPayload(). Khác nhau ở chỗ khách quét bằng gì được: mã
                   VietQR thì ứng dụng ngân hàng nào cũng mở ra thẳng màn hình chuyển tiền, còn
                   một địa chỉ web thì phải quét bằng camera rồi đi vòng qua trình duyệt. */
                req.setAttribute("qrDataUri", QrCodeUtil.toDataUri(link.qrPayload(), QR_SIZE));
            } catch (AppException e) {
                req.setAttribute("qrError", e.getMessage());
            }
        }

        req.setAttribute("order", order);
        req.setAttribute("payment", payment);
        req.setAttribute("gatewayName", paymentService.getGateway().getName());
        forward(req, resp, "staff/pos-qr.jsp");
    }

    /** Thu ngân bấm Xong để xác nhận khoản thu và chỉ lúc đó mới release đơn xuống bếp. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        String action = WebUtil.getString(req, "action");

        switch (action == null ? "" : action) {
            case "done":
                try {
                    orderService.confirmQrPayment(orderId, cashier.getUserId());
                    WebUtil.flashSuccess(req, "Đã ghi nhận tiền của đơn #" + orderId
                            + " và chuyển xuống bếp.");
                    redirect(req, resp, "/staff/order/detail?orderId=" + orderId);
                } catch (AppException e) {
                    WebUtil.flashError(req, e.getMessage());
                    redirect(req, resp, "/staff/pos/qr?orderId=" + orderId);
                }
                return;
            default:
                redirect(req, resp, "/staff/pos/qr?orderId=" + orderId);
        }
    }
}
