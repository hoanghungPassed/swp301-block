package com.fastfood.controller.admin;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.admin.ReportService;
import com.fastfood.service.admin.RevenueTargetService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bảng điều khiển của quản trị viên.
 * <p>
 * Mặc định xem số liệu 30 ngày gần nhất. Ngoài doanh thu và số đơn, màn hình này có hai
 * chỉ số riêng của mô hình đặt trước: tỷ lệ món sẵn sàng đúng hẹn, và số đơn khách đến muộn.
 * <p>
 * Đây cũng là nơi đặt và theo dõi <b>chỉ tiêu doanh thu</b>. Chỉ tiêu nằm ở đây chứ không ở màn
 * hình riêng vì nó chỉ có nghĩa khi đứng cạnh con số thật: đặt ra 150 triệu mà phải mở trang
 * khác để biết đã đạt bao nhiêu thì chẳng ai mở lần thứ hai.
 */
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
            // Khoảng chọn nhanh thắng hai ô nhập tay: người dùng vừa bấm vào nó.
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
            /* Gõ ngược hai đầu là chuyện thường gặp, và mọi truy vấn báo cáo đều lọc bằng
               BETWEEN — nên khoảng ngược cho ra bảng rỗng ở khắp trang, trông y hệt như
               "kỳ này không bán được gì". Đảo lại và nói ra, thay vì hiện một trang trắng
               không giải thích được. */
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

        /* Chỉ tiêu của tháng và của hôm nay đứng riêng khỏi danh sách bên dưới: hai kỳ này
           không phụ thuộc khoảng ngày người dùng đang lọc, vì "đã đạt bao nhiêu phần chỉ tiêu
           tháng" mà đổi theo bộ lọc thì con số đó không so được với gì. */
        req.setAttribute("monthTarget", targetService.currentMonth());
        req.setAttribute("dayTarget", targetService.today());
        req.setAttribute("targets", targetService.recent());
        req.setAttribute("today", DateTimeUtil.now().toLocalDate().toString());

        int editId = WebUtil.getInt(req, "editTarget", 0);
        if (editId > 0) {
            req.setAttribute("editingTarget", targetService.findById(editId));
        }
        forward(req, resp, "admin/dashboard.jsp");
    }

    /** Ba thao tác ghi trên chỉ tiêu doanh thu. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User admin = requireUser(req);
        int targetId = WebUtil.getInt(req, "targetId", 0);
        String note = WebUtil.getString(req, "note");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "targetUpdate":
                handle(req, resp, () -> targetService.update(admin.getUserId(), targetId,
                                amount(req), note),
                        "Đã cập nhật chỉ tiêu.", "/admin/dashboard");
                return;
            case "targetDelete":
                handle(req, resp, () -> targetService.delete(admin.getUserId(), targetId),
                        "Đã xoá chỉ tiêu.", "/admin/dashboard");
                return;
            case "targetCreate":
            default:
                handle(req, resp, () -> targetService.create(admin.getUserId(),
                                WebUtil.getString(req, "periodType"),
                                WebUtil.getDate(req, "periodStart"), amount(req), note),
                        "Đã đặt chỉ tiêu.", "/admin/dashboard");
        }
    }

    /**
     * Số ngày lùi lại của một khoảng chọn nhanh.
     * <p>
     * Đếm từ đầu ngày cách đây n ngày tới bây giờ, nên "hôm nay" là 0 ngày lùi lại chứ không
     * phải 1. Giá trị lạ — địa chỉ do người dùng gõ tay — rơi về đúng khoảng mặc định 30 ngày.
     */
    private int daysBack(String range) {
        switch (range) {
            case "today": return 0;
            case "7d":    return 7;
            default:      return 30;
        }
    }

    /**
     * Số tiền gõ vào ô nhập. Trả null khi không đọc được để tầng dịch vụ nói ra bằng tiếng Việt
     * là chỉ tiêu không hợp lệ — ném lỗi phân tích chuỗi ở đây thì người dùng chỉ nhận được
     * thông báo chung chung "có lỗi xảy ra".
     */
    private BigDecimal amount(HttpServletRequest req) {
        try {
            return new BigDecimal(WebUtil.getString(req, "targetAmount"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
