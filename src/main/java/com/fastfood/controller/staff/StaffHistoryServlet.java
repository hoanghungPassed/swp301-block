package com.fastfood.controller.staff;

import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.OperationEntities.Shift;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.staff.ShiftService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/staff/history")
public class StaffHistoryServlet extends BaseServlet {

    private final StaffOrderService orderService = new StaffOrderService();
    private final AuditService auditService = new AuditService();
    private final ShiftService shiftService = new ShiftService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        String source = WebUtil.getString(req, "source");
        String status = WebUtil.getString(req, "status");

        req.setAttribute("pageData", orderService.search(source, status,
                WebUtil.getDateTime(req, "from"), WebUtil.getDateTime(req, "to"),
                WebUtil.getInt(req, "page", 1)));
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        req.setAttribute("auditLogs", auditService.recent("ORDER", 50));
        req.setAttribute("source", source);
        req.setAttribute("status", status);

        Shift current = shiftService.currentShift(user.getUserId());
        req.setAttribute("currentShift", current);
        req.setAttribute("myShifts", shiftService.myShifts(user.getUserId()));
        if (current != null) {
            req.setAttribute("expectedCashNow", shiftService.expectedCashNow(current.getShiftId()));
        }
        forward(req, resp, "staff/history.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int shiftId = WebUtil.getInt(req, "shiftId", 0);
        String back = "/staff/history";

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "shiftClose":
                handle(req, resp, () -> {
                    Shift closed = shiftService.close(shiftId, user.getUserId(),
                            money(req, "countedCash"));
                    WebUtil.flashSuccess(req, closed.isVaried()
                            ? "Đã đóng ca. Lệch " + closed.getVariance().abs().toPlainString()
                              + "đ so với số hệ thống tính — kiểm tra lại két và ghi chú lý do."
                            : "Đã đóng ca. Tiền khớp với số hệ thống tính.");
                }, null, back);
                return;
            case "shiftNote":
                handle(req, resp, () -> shiftService.updateNote(shiftId, user.getUserId(),
                                WebUtil.getString(req, "note")),
                        "Đã lưu ghi chú ca.", back);
                return;
            case "shiftCancel":
                handle(req, resp, () -> shiftService.cancel(shiftId, user.getUserId()),
                        "Đã thu hồi ca mở nhầm.", back);
                return;
            case "shiftOpen":
            default:
                handle(req, resp, () -> shiftService.open(user.getUserId(),
                                money(req, "openingCash"), WebUtil.getString(req, "note")),
                        "Đã mở ca. Từ giờ mọi đơn tại quầy sẽ được gắn vào ca này.", back);
        }
    }

    private BigDecimal money(HttpServletRequest req, String name) {
        String raw = WebUtil.getString(req, name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ValidationException("Số tiền \"" + raw.trim() + "\" không hợp lệ. "
                    + "Chỉ nhập chữ số, ví dụ 500000.");
        }
    }
}
