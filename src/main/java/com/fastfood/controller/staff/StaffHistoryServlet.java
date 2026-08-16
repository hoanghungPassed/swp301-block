package com.fastfood.controller.staff;

import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.Shift;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.staff.ShiftService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Tra cứu đơn cũ, nhật ký thao tác tại quầy, và <b>ca làm việc</b> của chính thu ngân.
 * <p>
 * Ba thứ nằm chung một trang vì chúng trả lời cùng một câu hỏi cuối ca: <i>hôm nay tôi đã bán
 * những gì, và tiền có khớp không</i>. Danh sách đơn trả lời vế đầu, bảng ca trả lời vế sau,
 * và nhật ký là chỗ tra khi hai vế không khớp.
 */
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
        // Số tiền lẽ ra phải có trong két ngay lúc này — hiện sẵn để thu ngân biết mình
        // có đếm nhầm không, thay vì phải đóng ca rồi mới thấy chênh lệch.
        if (current != null) {
            req.setAttribute("expectedCashNow", shiftService.expectedCashNow(current.getShiftId()));
        }
        forward(req, resp, "staff/history.jsp");
    }

    /** Bốn thao tác trên ca làm việc. */
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
                    // Thông báo phải nói ra con số: "đã đóng ca" rồi thôi thì thu ngân
                    // vẫn phải đi tìm xem có lệch hay không.
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

    /**
     * Số tiền do người dùng nhập.
     * <p>
     * Bỏ trống trả về {@code null} để tầng nghiệp vụ quyết định ý nghĩa của "không nhập gì".
     * Nhập bậy thì <b>báo lỗi ngay</b>, không quy về null: {@code ShiftService.open} hiểu null là
     * "không có tiền lẻ đầu ca" và mở ca với số 0, nên gõ nhầm một ký tự vào ô tiền đầu ca sẽ
     * lặng lẽ mở ca sai — và sai số đó chỉ lộ ra ở chênh lệch cuối ca, lúc không còn ai nhớ
     * trong két ban đầu có bao nhiêu.
     */
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
