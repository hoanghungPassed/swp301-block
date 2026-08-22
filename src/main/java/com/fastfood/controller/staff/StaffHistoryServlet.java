package com.fastfood.controller.staff;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/staff/history")
public class StaffHistoryServlet extends BaseServlet {

    private final StaffOrderService orderService = new StaffOrderService();
    private final AuditService auditService = new AuditService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        requireUser(req);
        String source = WebUtil.getString(req, "source");
        String status = WebUtil.getString(req, "status");

        req.setAttribute("pageData", orderService.search(source, status,
                WebUtil.getDateTime(req, "from"), WebUtil.getDateTime(req, "to"),
                WebUtil.getInt(req, "page", 1)));
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        req.setAttribute("auditLogs", auditService.recent("ORDER", 50));
        req.setAttribute("source", source);
        req.setAttribute("status", status);

        forward(req, resp, "staff/history.jsp");
    }
}
