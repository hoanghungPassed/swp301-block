package com.fastfood.controller.staff;

import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderNote;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.staff.OrderNoteService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@WebServlet("/staff/orders")
public class OrderDashboardServlet extends BaseServlet {

    private static final List<String> TABS = List.of("POS", "SCHEDULED", "READY", "OVERDUE");

    private final StaffOrderService orderService = new StaffOrderService();
    private final OrderNoteService noteService = new OrderNoteService();
    private final KitchenService kitchenService = new KitchenService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String tab = WebUtil.getString(req, "tab");
        if (tab == null || tab.isBlank()) {
            tab = "POS";
        }

        Map<String, List<Order>> byTab = new LinkedHashMap<>();
        for (String name : TABS) {
            byTab.put(name, orderService.dashboard(name));
        }
        List<Order> current = byTab.getOrDefault(tab, byTab.get("POS"));

        req.setAttribute("tab", tab);
        req.setAttribute("orders", current);
        req.setAttribute("countPos", byTab.get("POS").size());
        req.setAttribute("countScheduled", byTab.get("SCHEDULED").size());
        req.setAttribute("countReady", byTab.get("READY").size());
        req.setAttribute("countOverdue", byTab.get("OVERDUE").size());
        req.setAttribute("openIssueCount", kitchenService.countOpenIssues());
        req.setAttribute("awaitingCounterCount", orderService.countAwaitingCounter());

        Map<Integer, List<OrderNote>> notesByOrder = noteService.notesOfOrders(
                current.stream().map(Order::getOrderId).collect(Collectors.toList()));
        req.setAttribute("notesByOrder", notesByOrder);

        int editId = WebUtil.getInt(req, "editNote", 0);
        if (editId > 0) {
            req.setAttribute("editingNote", notesByOrder.values().stream()
                    .flatMap(List::stream)
                    .filter(n -> n.getOrderNoteId() == editId)
                    .findFirst().orElse(null));
        }

        lookupPickupCode(req);
        forward(req, resp, "staff/order-dashboard.jsp");
    }

    private void lookupPickupCode(HttpServletRequest req) {
        String code = WebUtil.getString(req, "code");
        req.setAttribute("code", code);
        if (code == null || code.isBlank()) {
            return;
        }
        try {
            Order found = orderService.findByPickupCode(code);
            req.setAttribute("found", found);

            if (!OrderStatus.READY.name().equals(found.getOrderStatus())) {
                req.setAttribute("lookupWarning", "Đơn chưa sẵn sàng để giao. Trạng thái hiện tại: "
                        + found.getOrderStatus());
            } else if (!found.isPaid()) {
                req.setAttribute("lookupWarning", "Đơn này chưa có khoản thanh toán thành công nên "
                        + "không giao được. Kiểm tra lại tình trạng thanh toán trước khi đưa món.");
            }
        } catch (AppException e) {
            req.setAttribute("lookupError", e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int noteId = WebUtil.getInt(req, "noteId", 0);
        String content = WebUtil.getString(req, "content");
        String tab = WebUtil.getString(req, "tab");
        String back = "/staff/orders" + (tab == null || tab.isBlank() ? "" : "?tab=" + tab);

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "noteUpdate":
                handle(req, resp, () -> noteService.update(noteId, user.getUserId(), content),
                        "Đã sửa ghi chú.", back);
                return;
            case "noteDelete":
                handle(req, resp, () -> noteService.delete(noteId, user.getUserId()),
                        "Đã xoá ghi chú.", back);
                return;
            case "noteAdd":
            default:
                handle(req, resp, () -> noteService.add(WebUtil.getInt(req, "orderId", 0),
                                user.getUserId(), content),
                        "Đã thêm ghi chú.", back);
        }
    }
}
