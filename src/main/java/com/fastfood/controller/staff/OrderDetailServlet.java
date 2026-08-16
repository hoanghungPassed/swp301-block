package com.fastfood.controller.staff;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.service.shared.PaymentService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Chi tiết đơn và các thao tác của thu ngân: giao món, huỷ đơn, hoàn tiền.
 * <p>
 * Với đơn đặt trước, việc giao món chỉ thành công khi mã khách đưa khớp với đơn — kiểm tra
 * nằm ở tầng dịch vụ chứ không ở giao diện.
 */
@WebServlet("/staff/order/detail")
public class OrderDetailServlet extends BaseServlet {

    private final StaffOrderService orderService = new StaffOrderService();
    private final PaymentService paymentService = new PaymentService();
    private final AuditService auditService = new AuditService();
    private final KitchenService kitchenService = new KitchenService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int orderId = WebUtil.getInt(req, "orderId", 0);
        try {
            req.setAttribute("order", orderService.findById(orderId));
            req.setAttribute("payments", paymentService.findByOrder(orderId));
            req.setAttribute("auditLogs", auditService.findByEntity("ORDER", orderId));
            // Con số đếm trên từng dòng món không đủ để nói chuyện với khách — phải có
            // loại sự cố và mô tả thì nhân viên mới biết đơn đang vướng chuyện gì.
            req.setAttribute("openIssues", kitchenService.openIssuesOfOrder(orderId));
            forward(req, resp, "staff/order-detail.jsp");
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            forward(req, resp, "error/404.jsp");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User staff = requireUser(req);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        String action = WebUtil.getString(req, "action");
        String back = "/staff/order/detail?orderId=" + orderId;

        switch (action == null ? "" : action) {
            case "handoff": {
                String code = WebUtil.getString(req, "pickupCode");
                handle(req, resp, () -> orderService.handoff(orderId, staff.getUserId(), code),
                        "Đã giao món cho khách và hoàn tất đơn #" + orderId + ".", back);
                return;
            }
            case "cancel": {
                // Huỷ đơn và hoàn tiền là một thao tác, không phải hai nút bấm rời nhau:
                // tách ra thì nhân viên làm được nửa chừng và để lại đơn dở dang.
                String reason = WebUtil.getString(req, "reason");
                handle(req, resp, () -> orderService.cancelByStaff(orderId, staff.getUserId(), reason),
                        "Đã huỷ đơn #" + orderId + " và hoàn lại tiền nếu khách đã thanh toán.", back);
                return;
            }
            case "refund": {
                String refundReason = WebUtil.getString(req, "refundReason");
                handle(req, resp, () -> paymentService.refund(orderId, staff.getUserId(), refundReason),
                        "Đã hoàn tiền cho đơn #" + orderId + ".", back);
                return;
            }
            default:
                redirect(req, resp, back);
        }
    }
}
