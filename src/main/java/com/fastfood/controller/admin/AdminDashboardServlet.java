package com.fastfood.controller.admin;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.admin.ReportService;
import com.fastfood.service.admin.RevenueTargetService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@WebServlet("/admin/dashboard")
public class AdminDashboardServlet extends BaseServlet {

    private final ReportService reportService = new ReportService();
    private final RevenueTargetService targetService = new RevenueTargetService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String range = WebUtil.getString(req, "range");
        LocalDateTime from = WebUtil.getDateTime(req, "from");
        LocalDateTime to = WebUtil.getDateTime(req, "to");

        if (range != null && !range.isBlank()) {
            from = DateTimeUtil.now().minusDays(daysBack(range)).toLocalDate().atStartOfDay();
            to = DateTimeUtil.now();
        }
        if (from == null) {
            from = DateTimeUtil.now().minusDays(30).toLocalDate().atStartOfDay();
        }
        if (to == null) {
            to = DateTimeUtil.now();
        }
        if (from.isAfter(to)) {
            LocalDateTime swap = from;
            from = to;
            to = swap;
            req.setAttribute("rangeSwapped", Boolean.TRUE);
        }

        req.setAttribute("kpi", reportService.loadKpi(from, to));
        req.setAttribute("bestSellers", reportService.bestSellers(from, to, 10));
        req.setAttribute("paymentSummary", reportService.paymentSummary(from, to));
        req.setAttribute("revenueByDay", reportService.revenueByDay(from, to));
        req.setAttribute("from", DateTimeUtil.toHtmlInput(from));
        req.setAttribute("to", DateTimeUtil.toHtmlInput(to));
        req.setAttribute("range", range);

        req.setAttribute("monthTarget", targetService.currentMonth());
        req.setAttribute("dayTarget", targetService.today());
        req.setAttribute("targetPage", Page.of(targetService.recent(),
                WebUtil.getInt(req, "targetPage", 1), Page.SMALL_SIZE));
        req.setAttribute("today", DateTimeUtil.now().toLocalDate().toString());

        int editId = WebUtil.getInt(req, "editTarget", 0);
        if (editId > 0) {
            req.setAttribute("editingTarget", targetService.findById(editId));
        }
        forward(req, resp, "admin/dashboard.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User admin = requireUser(req);
        int targetId = WebUtil.getInt(req, "targetId", 0);
        String note = WebUtil.getString(req, "note");
        /* Quay lại đúng khoảng thời gian và đúng trang chỉ tiêu đang xem. */
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/admin/dashboard");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "targetUpdate":
                handle(req, resp, () -> targetService.update(admin.getUserId(), targetId,
                                amount(req), note),
                        "Đã cập nhật chỉ tiêu.", back);
                return;
            case "targetDelete":
                handle(req, resp, () -> targetService.delete(admin.getUserId(), targetId),
                        "Đã xoá chỉ tiêu.", back);
                return;
            case "targetCreate":
            default:
                handle(req, resp, () -> targetService.create(admin.getUserId(),
                                WebUtil.getString(req, "periodType"),
                                WebUtil.getDate(req, "periodStart"), amount(req), note),
                        "Đã đặt chỉ tiêu.", back);
        }
    }

    private int daysBack(String range) {
        switch (range) {
            case "today": return 0;
            case "7d":    return 7;
            default:      return 30;
        }
    }

    private BigDecimal amount(HttpServletRequest req) {
        try {
            return new BigDecimal(WebUtil.getString(req, "targetAmount"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
