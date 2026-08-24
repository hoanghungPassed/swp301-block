package com.fastfood.controller.kitchen;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.kitchen.KitchenService;
import com.fastfood.service.kitchen.PrepService;
import com.fastfood.service.shared.MenuService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@WebServlet("/kitchen/queue")
public class KdsQueueServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final PrepService prepService = new PrepService();
    private final MenuService menuService = new MenuService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        /* Ba khối đều đếm theo ĐƠN: bếp nhận cả đơn, làm xong cả đơn và bàn giao cả đơn. */
        req.setAttribute("taskPage", Page.of(kitchenService.myOrders(user.getUserId()),
                WebUtil.getInt(req, "taskPage", 1), Page.CARD_SIZE));
        req.setAttribute("handoverPage", Page.of(kitchenService.ordersAwaitingHandover(user.getUserId()),
                WebUtil.getInt(req, "handoverPage", 1), Page.CARD_SIZE));
        /* Hàng chờ tự làm mới bằng JavaScript nên trang đầu do máy chủ dựng, các lần
           cập nhật sau do trình duyệt cắt lại theo đúng số trang này. */
        req.setAttribute("queuePage", Page.of(kitchenService.waitingOrders(),
                WebUtil.getInt(req, "queuePage", 1), Page.CARD_SIZE));
        req.setAttribute("openIssueCount", kitchenService.countOpenIssues());

        LocalDate prepDate = parseDate(WebUtil.getString(req, "prepDate"));
        req.setAttribute("prepDate", prepDate);
        req.setAttribute("prepPage", Page.of(prepService.planOf(prepDate),
                WebUtil.getInt(req, "prepPage", 1), Page.SMALL_SIZE));
        req.setAttribute("prepProducts", menuService.browse(null, null));

        int editId = WebUtil.getInt(req, "editPrep", 0);
        if (editId > 0) {
            req.setAttribute("editingPrep", prepService.findById(editId));
        }
        forward(req, resp, "kitchen/kds-queue.jsp");
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return DateTimeUtil.now().toLocalDate();
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return DateTimeUtil.now().toLocalDate();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        int orderId = WebUtil.getInt(req, "orderId", 0);
        String action = WebUtil.getString(req, "action");
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/kitchen/queue");

        if (action != null && action.startsWith("prep")) {
            handlePrep(req, resp, user, action, back);
            return;
        }

        /* Nút trên màn bếp làm việc theo đơn; ba nhánh lẻ bên dưới chỉ còn trang chi tiết
           món dùng tới, để bếp xử lý riêng một món hỏng giữa đơn. */
        switch (action == null ? "" : action) {
            case "claimOrder":
                handle(req, resp, () -> kitchenService.claimOrder(orderId, user.getUserId()),
                        "Đã nhận đơn #" + orderId + ". Bắt đầu chế biến.", back);
                return;
            case "readyOrder":
                handle(req, resp, () -> {
                    boolean orderReady = kitchenService.markOrderReady(orderId, user.getUserId());
                    WebUtil.flashSuccess(req, orderReady
                            ? "Đơn #" + orderId + " đã xong, khách đã được báo. "
                              + "Nhớ bàn giao cả đơn ra quầy."
                            : "Đã đánh dấu xong phần của bạn trong đơn #" + orderId + ".");
                }, null, back);
                return;
            case "handoverOrder":
                handle(req, resp, () -> kitchenService.handOverOrder(orderId, user.getUserId()),
                        "Đã bàn giao đơn #" + orderId + " ra quầy.", back);
                return;
            case "ready":
                handle(req, resp, () -> {
                    boolean orderReady = kitchenService.markReady(itemId, user.getUserId());
                    WebUtil.flashSuccess(req, orderReady
                            ? "Món đã xong. Cả đơn đã sẵn sàng, khách đã được báo. "
                              + "Nhớ bàn giao món ra quầy."
                            : "Đã đánh dấu món hoàn thành. Nhớ bàn giao món ra quầy.");
                }, null, back);
                return;
            case "handover":
                handle(req, resp, () -> kitchenService.handOverToCounter(itemId, user.getUserId()),
                        "Đã bàn giao món ra quầy.", back);
                return;
            case "claim":
                handle(req, resp, () -> kitchenService.claim(itemId, user.getUserId()),
                        "Đã nhận món. Bắt đầu chế biến.", back);
                return;
            default:
                /* Không để nhánh cuối rơi vào việc nhận món: một tham số gõ sai sẽ lặng lẽ
                   ghi tên người bấm vào một món nào đó. */
                WebUtil.flashError(req, "Thao tác không hợp lệ.");
                redirect(req, resp, back);
        }
    }

    private void handlePrep(HttpServletRequest req, HttpServletResponse resp, User user,
                            String action, String back) throws IOException {
        int prepTaskId = WebUtil.getInt(req, "prepTaskId", 0);
        LocalDate date = parseDate(WebUtil.getString(req, "prepDate"));
        String returnTo = back + (back.contains("?") ? "&" : "?") + "prepDate=" + date;

        switch (action) {
            case "prepUpdate":
                handle(req, resp, () -> prepService.update(prepTaskId,
                                WebUtil.getInt(req, "plannedQty", 0),
                                WebUtil.getInt(req, "doneQty", 0),
                                WebUtil.getString(req, "note"), user.getUserId()),
                        "Đã cập nhật dòng kế hoạch.", returnTo);
                return;
            case "prepDone":
                handle(req, resp, () -> prepService.markDone(prepTaskId, user.getUserId()),
                        "Đã chốt phần chuẩn bị này.", returnTo);
                return;
            case "prepCancel":
                handle(req, resp, () -> prepService.cancel(prepTaskId, user.getUserId()),
                        "Đã thu hồi dòng kế hoạch.", returnTo);
                return;
            case "prepCreate":
            default:
                handle(req, resp, () -> prepService.plan(
                                WebUtil.getInt(req, "productId", 0), date,
                                WebUtil.getInt(req, "plannedQty", 0),
                                WebUtil.getString(req, "note"), user.getUserId()),
                        "Đã thêm vào kế hoạch chuẩn bị.", returnTo);
        }
    }
}
