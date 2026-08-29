package com.fastfood.controller.staff;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.Page;
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

    /** Nạp các hàng chờ nhận, đơn sẵn sàng và sự cố để hiển thị màn hình giao nhận tại quầy. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        /* Năm bảng trên cùng một trang nên mỗi bảng có tham số trang riêng, đổi trang
           bảng này không kéo bảng kia về đầu. */
        /* Bếp bàn giao cả đơn nên quầy cũng nhận cả đơn: bảng đầu tiên gom món theo đơn. */
        req.setAttribute("handoverPage", Page.of(orderService.awaitingCounterOrders(),
                WebUtil.getInt(req, "handoverPage", 1), Page.SMALL_SIZE));
        req.setAttribute("readyPage", Page.of(orderService.readyOrdersForCounter(),
                WebUtil.getInt(req, "readyPage", 1), Page.SMALL_SIZE));
        req.setAttribute("issuePage", Page.of(kitchenService.openIssues(),
                WebUtil.getInt(req, "issuePage", 1), Page.SMALL_SIZE));
        req.setAttribute("closedPage", Page.of(kitchenService.recentClosedIssues(30),
                WebUtil.getInt(req, "closedPage", 1), Page.SMALL_SIZE));
        req.setAttribute("rejectPage", Page.of(rejectService.openRejects(),
                WebUtil.getInt(req, "rejectPage", 1), Page.SMALL_SIZE));
        forward(req, resp, "staff/counter.jsp");
    }

    /**
     * Điều phối nút nhận đơn/nhận món hoặc trả món về bếp; Service chịu trách nhiệm kiểm tra
     * trạng thái và quyền sở hữu phiếu từ chối.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        int issueId = WebUtil.getInt(req, "issueId", 0);
        String reason = WebUtil.getString(req, "reason");
        String action = WebUtil.getString(req, "action");
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/staff/counter");

        switch (action == null ? "" : action) {
            case "receiveOrder":
                handle(req, resp, () -> orderService.receiveOrder(orderId, cashier.getUserId()),
                        "Đã nhận cả đơn #" + orderId + " từ bếp.", back);
                return;
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
