package com.fastfood.controller.staff;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.CounterRejectService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/staff/counter")
public class CounterServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final StaffOrderService orderService = new StaffOrderService();
    private final CounterRejectService rejectService = new CounterRejectService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("awaitingCounter", orderService.awaitingCounter());
        req.setAttribute("readyOrders", orderService.readyOrdersForCounter());
        req.setAttribute("openIssues", kitchenService.openIssues());
        req.setAttribute("recentIssues", kitchenService.recentIssues(30));
        req.setAttribute("counterRejects", rejectService.openRejects());
        forward(req, resp, "staff/counter.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        int issueId = WebUtil.getInt(req, "issueId", 0);
        String reason = WebUtil.getString(req, "reason");
        String action = WebUtil.getString(req, "action");
        String back = "/staff/counter";

        switch (action == null ? "" : action) {
            case "reject":
                handle(req, resp, () -> rejectService.reject(itemId, cashier.getUserId(), reason),
                        "Đã trả món về bếp kèm lý do. Món sẽ hiện lại ở màn hình bếp để làm lại.",
                        back);
                return;
            case "rejectUpdate":
                handle(req, resp, () -> rejectService.updateReason(issueId, cashier.getUserId(), reason),
                        "Đã sửa lý do từ chối.", back);
                return;
            case "rejectCancel":
                handle(req, resp, () -> rejectService.cancel(issueId, cashier.getUserId()),
                        "Đã thu hồi phiếu từ chối.", back);
                return;
            case "receive":
            default:
                handle(req, resp, () -> orderService.receiveAtCounter(itemId, cashier.getUserId()),
                        "Đã nhận món từ bếp.", back);
        }
    }
}
