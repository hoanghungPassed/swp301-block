package com.fastfood.controller.admin;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.service.shared.AuditService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Tra cứu nhật ký thao tác.
 * <p>
 * Mọi việc liên quan tới tiền và tới trạng thái đơn đều để lại dấu vết ở đây: thanh toán
 * thành công, tự động xác nhận, đưa đơn xuống bếp, xác minh mã, giao món, huỷ và hoàn tiền.
 */
@WebServlet("/admin/audit")
public class AuditServlet extends BaseServlet {

    private final AuditService auditService = new AuditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String entityType = WebUtil.getString(req, "entityType");
        String action = WebUtil.getString(req, "action");

        req.setAttribute("pageData", auditService.search(entityType, action,
                WebUtil.getDateTime(req, "from"), WebUtil.getDateTime(req, "to"),
                WebUtil.getInt(req, "page", 1)));
        // Liên kết chuyển trang phải mang theo bộ lọc đang áp dụng, nếu không thì bấm sang
        // trang 2 lại nhảy về xem toàn bộ nhật ký.
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        req.setAttribute("actions", auditService.distinctActions());
        req.setAttribute("entityType", entityType);
        req.setAttribute("action", action);
        forward(req, resp, "admin/audit.jsp");
    }
}
