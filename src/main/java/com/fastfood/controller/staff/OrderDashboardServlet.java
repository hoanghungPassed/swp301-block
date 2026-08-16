package com.fastfood.controller.staff;

import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderNote;
import com.fastfood.model.entity.User;
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

/**
 * Màn hình điều phối của thu ngân, chia bốn tab: đơn tại quầy đang xử lý, đơn đặt trước
 * (đang chờ tới giờ hoặc đang làm), đơn chờ khách tới lấy, và đơn khách đã quá hẹn.
 * <p>
 * Bốn tab cộng lại phủ kín mọi đơn chưa kết thúc — xem
 * {@link com.fastfood.dao.shared.OrderDAO#findForDashboard}.
 * <p>
 * Màn hình này cũng là chỗ tra mã nhận hàng khi khách tới quầy. Tra xong vẫn phải mở đơn ra
 * mới giao được món, để nhân viên có cơ hội đối chiếu món trước khi đưa cho khách.
 */
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

        // Bốn tab đều cần số lượng để nhân viên thấy ngay chỗ nào cần xử lý; tab đang mở
        // thì dùng lại chính danh sách đó thay vì hỏi cơ sở dữ liệu thêm một lần nữa.
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
        // Sự cố bếp không nằm trong tab nào vì nó cắt ngang cả bốn: đơn nào cũng có thể
        // vướng. Để nhân viên tự đi tìm thì không ai tìm, nên đẩy thành cảnh báo trên đầu.
        req.setAttribute("openIssueCount", kitchenService.countOpenIssues());
        req.setAttribute("awaitingCounterCount", orderService.countAwaitingCounter());

        // Ghi chú của mọi đơn đang hiện, lấy trong MỘT lượt truy vấn. Hỏi từng đơn một sẽ
        // thành hàng chục lượt cho một lần mở trang.
        Map<Integer, List<OrderNote>> notesByOrder = noteService.notesOfOrders(
                current.stream().map(Order::getOrderId).collect(Collectors.toList()));
        req.setAttribute("notesByOrder", notesByOrder);

        // Ghi chú đang sửa tìm ngay trong tập vừa nạp, không hỏi lại cơ sở dữ liệu: nó chắc
        // chắn thuộc một trong những đơn đang hiện, vì nút Sửa chỉ có ở đó.
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

    /**
     * Tra mã nhận hàng ngay trên màn hình điều phối.
     * <p>
     * Trước đây là một màn hình riêng, nhưng thu ngân luôn phải đi qua đây rồi mới mở được
     * đơn để giao món — hai lần chuyển trang cho một việc. Gộp vào đầu màn hình này thì lúc
     * khách đứng ở quầy chỉ còn một chỗ để nhìn.
     */
    private void lookupPickupCode(HttpServletRequest req) {
        String code = WebUtil.getString(req, "code");
        req.setAttribute("code", code);
        if (code == null || code.isBlank()) {
            return;
        }
        try {
            // Chỉ đơn đặt trước mới có mã nhận hàng — ràng buộc CK_Orders_pickupCodeOnline
            // đảm bảo điều đó — nên tra theo mã thì không bao giờ ra đơn tại quầy.
            Order found = orderService.findByPickupCode(code);
            req.setAttribute("found", found);

            // Giao món cần đủ ba điều kiện: mã khớp, món đã xong, tiền đã thu. Mã khớp thì vừa
            // tra xong; hai điều kiện còn lại phải nói thành lời ở đây. Chỉ hiện huy hiệu trạng
            // thái là chưa đủ — nhân viên đang vội sẽ đọc lướt qua, rồi bấm giao món và nhận một
            // thông báo lỗi mà lúc đó khách đã đứng chờ sẵn ở quầy.
            //
            // Đây chỉ là cảnh báo sớm cho người dùng. Chốt chặn thật nằm ở StaffOrderService.handoff,
            // nơi cả ba điều kiện được kiểm lại một lần nữa.
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

    /**
     * Ba thao tác trên ghi chú điều phối.
     * <p>
     * Quay về đúng tab đang mở, nếu không thì ghi chú trên tab "Sẵn sàng giao" xong lại bị đẩy
     * về tab mặc định và người dùng tưởng thao tác không ăn.
     */
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
