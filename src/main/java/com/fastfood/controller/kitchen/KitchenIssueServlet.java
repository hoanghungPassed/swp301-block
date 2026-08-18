package com.fastfood.controller.kitchen;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.KdsItemView;
import com.fastfood.model.entity.OperationEntities.KitchenIssue;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/kitchen/issue")
public class KitchenIssueServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setAttribute("openIssues", kitchenService.openIssues());
        List<KitchenIssue> closed = new ArrayList<>();
        for (KitchenIssue issue : kitchenService.recentIssues(30)) {
            if (!issue.isOpen()) {
                closed.add(issue);
            }
        }
        req.setAttribute("closedIssues", closed);

        int orderItemId = WebUtil.getInt(req, "orderItemId", 0);
        req.setAttribute("orderItemId", orderItemId);
        req.setAttribute("kitchenItems", withRequestedItem(kitchenService.itemsInKitchen(), orderItemId));

        int editId = WebUtil.getInt(req, "edit", 0);
        if (editId > 0) {
            req.setAttribute("editing", kitchenService.findIssue(editId));
        }
        forward(req, resp, "kitchen/issue.jsp");
    }

    private List<KdsItemView> withRequestedItem(List<KdsItemView> items, int orderItemId) {
        if (orderItemId <= 0) {
            return items;
        }
        for (KdsItemView view : items) {
            if (view.getItem().getOrderItemId() == orderItemId) {
                return items;
            }
        }
        List<KdsItemView> withIt = new ArrayList<>(items.size() + 1);
        try {
            withIt.add(new KdsItemView(kitchenService.findItem(orderItemId)));
        } catch (AppException e) {
            return items;
        }
        withIt.addAll(items);
        return withIt;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String action = WebUtil.getString(req, "action");

        int issueId = WebUtil.getInt(req, "issueId", 0);

        switch (action == null ? "" : action) {
            case "resolve":
                handle(req, resp, () -> kitchenService.resolveIssue(issueId, user.getUserId()),
                        "Đã đánh dấu sự cố được xử lý.", "/kitchen/issue");
                return;
            case "update":
                handle(req, resp, () -> kitchenService.updateIssue(issueId, user.getUserId(),
                                WebUtil.getString(req, "description")),
                        "Đã cập nhật mô tả sự cố.", "/kitchen/issue");
                return;
            case "cancel":
                handle(req, resp, () -> kitchenService.cancelIssue(issueId, user.getUserId()),
                        "Đã thu hồi sự cố báo nhầm.", "/kitchen/issue");
                return;
            default:
                break;
        }

        int orderItemId = WebUtil.getInt(req, "orderItemId", 0);
        String issueType = WebUtil.getString(req, "issueType");
        String description = WebUtil.getString(req, "description");
        handle(req, resp, () -> kitchenService.openIssue(orderItemId, user.getUserId(), issueType, description),
                "Đã ghi nhận sự cố.", "/kitchen/issue");
    }
}
