package com.fastfood.controller.kitchen;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
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

/**
 * Màn hình bếp — toàn bộ việc của một đầu bếp trên một trang.
 * <p>
 * Ba khối theo đúng thứ tự làm việc: món đang làm dở, món đã xong chờ đưa ra quầy, rồi mới
 * tới hàng chờ chung. Trước đây hai khối đầu nằm ở một trang khác, nên đầu bếp phải nhớ mình
 * đang làm gì trong lúc nhìn danh sách việc mới — và món đã xong thì không có chỗ nào nhắc,
 * nên nó nằm lại trong bếp cho tới khi khách hỏi.
 * <p>
 * Hàng chờ chỉ hiện món của những đơn đã được đưa xuống bếp. Đơn đặt trước đã thanh toán
 * nhưng chưa tới giờ không xuất hiện ở đây — đó là cách hệ thống giữ cho món không bị làm sớm.
 */
@WebServlet("/kitchen/queue")
public class KdsQueueServlet extends BaseServlet {

    private final KitchenService kitchenService = new KitchenService();
    private final PrepService prepService = new PrepService();
    private final MenuService menuService = new MenuService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        req.setAttribute("myTasks", kitchenService.myTasks(user.getUserId()));
        req.setAttribute("awaitingHandover", kitchenService.awaitingHandover(user.getUserId()));
        req.setAttribute("queue", kitchenService.waitingQueue());
        // Con số cho ô chỉ báo trên đầu trang. Sự cố không thuộc riêng ai nên đếm của cả bếp —
        // một món cháy mà người báo đã hết ca thì nó vẫn phải hiện ở đây.
        req.setAttribute("openIssueCount", kitchenService.countOpenIssues());

        // Khối kế hoạch chuẩn bị. Mặc định là hôm nay; bếp xem trước ngày mai bằng ?prepDate=
        LocalDate prepDate = parseDate(WebUtil.getString(req, "prepDate"));
        req.setAttribute("prepDate", prepDate);
        req.setAttribute("prepTasks", prepService.planOf(prepDate));
        // Chỉ món còn bán mới đưa vào ô chọn — chuẩn bị sẵn một món đã ngừng phục vụ
        // là dùng nguyên liệu cho thứ không ai đặt được.
        req.setAttribute("prepProducts", menuService.browse(null, null));

        int editId = WebUtil.getInt(req, "editPrep", 0);
        if (editId > 0) {
            req.setAttribute("editingPrep", prepService.findById(editId));
        }
        forward(req, resp, "kitchen/kds-queue.jsp");
    }

    /**
     * Ngày lập kế hoạch lấy từ ô nhập trên giao diện. Giá trị hỏng thì lùi về hôm nay thay vì
     * báo lỗi: đây là tham số chọn khoảng nhìn, không phải dữ liệu người dùng gửi lên để ghi.
     */
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

    /**
     * Ba thao tác của một món, cùng đổ về đây vì cùng kết thúc bằng việc vẽ lại trang này.
     * Trang chi tiết món cũng gửi về đây kèm {@code returnTo} để quay lại đúng chỗ nó đứng.
     * <p>
     * Bốn thao tác {@code prep*} thuộc khối kế hoạch chuẩn bị và không đụng tới món nào —
     * chúng tách hẳn ra trước, vì nhánh mặc định bên dưới hiểu mọi giá trị lạ là "nhận món".
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int itemId = WebUtil.getInt(req, "orderItemId", 0);
        String action = WebUtil.getString(req, "action");
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/kitchen/queue");

        if (action != null && action.startsWith("prep")) {
            handlePrep(req, resp, user, action, back);
            return;
        }

        switch (action == null ? "" : action) {
            case "ready":
                // Thông báo đặt bên trong thao tác và truyền null ở tham số sau, nếu không
                // thông báo chung sẽ ghi đè lên thông báo "cả đơn đã sẵn sàng".
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
            default:
                handle(req, resp, () -> kitchenService.claim(itemId, user.getUserId()),
                        "Đã nhận món. Bắt đầu chế biến.", back);
        }
    }

    /**
     * Bốn thao tác trên khối kế hoạch chuẩn bị.
     * <p>
     * Đường quay về luôn kèm ngày đang xem, nếu không thì lập kế hoạch cho ngày mai xong lại
     * bị đẩy về danh sách hôm nay và người dùng tưởng thao tác không ăn.
     */
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
