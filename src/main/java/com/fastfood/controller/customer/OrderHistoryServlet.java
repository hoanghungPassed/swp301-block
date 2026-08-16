package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.TemplateApplyResult;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.service.customer.OrderTemplateService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Lịch sử đơn của khách. Chỉ thấy đơn của chính mình — điều kiện lọc nằm ở tầng dịch vụ.
 * <p>
 * Đây cũng là nơi khách quản lý <b>mẫu đặt nhanh</b>. Đặt ở đây vì mẫu sinh ra từ chính những
 * đơn trên trang này: nhìn thấy đơn cũ rồi bấm lưu lại là một thao tác liền mạch, còn tách ra
 * màn hình riêng thì khách phải nhớ mình đã đặt gì để gõ lại.
 */
@WebServlet("/order/history")
public class OrderHistoryServlet extends BaseServlet {

    private final CustomerOrderService orderService = new CustomerOrderService();
    private final OrderTemplateService templateService = new OrderTemplateService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        String status = WebUtil.getString(req, "status");

        req.setAttribute("pageData", orderService.historyOfCustomer(user.getUserId(), status,
                WebUtil.getDate(req, "from"), WebUtil.getDate(req, "to"),
                WebUtil.getInt(req, "page", 1)));
        // Liên kết chuyển trang phải mang theo bộ lọc đang áp dụng, nếu không thì bấm sang
        // trang 2 lại nhảy về xem toàn bộ lịch sử.
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page"));
        // Trang phân biệt "chưa từng đặt đơn nào" với "bộ lọc không khớp gì": hai tình huống
        // đó cần hai câu trả lời khác hẳn nhau, và câu thứ hai phải kèm lối bỏ lọc.
        req.setAttribute("filtering", (status != null && !status.isBlank())
                || WebUtil.getDate(req, "from") != null || WebUtil.getDate(req, "to") != null);
        // Khung "đang theo dõi" ở trên cùng luôn hiện đủ, không phân trang: đơn đang chạy
        // của một khách chỉ có vài cái, và đó chính là thứ họ mở trang này để xem.
        req.setAttribute("activeOrders", orderService.activeOrdersOfCustomer(user.getUserId()));

        req.setAttribute("templates", templateService.listOf(user.getUserId()));
        int editId = WebUtil.getInt(req, "editTemplate", 0);
        if (editId > 0) {
            req.setAttribute("editingTemplate", templateService.findOwn(editId, user.getUserId()));
        }
        forward(req, resp, "customer/order-history.jsp");
    }

    /** Năm thao tác trên mẫu đặt nhanh. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        int userId = user.getUserId();
        int templateId = WebUtil.getInt(req, "templateId", 0);
        String name = WebUtil.getString(req, "name");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "templateApply":
                applyTemplate(req, resp, templateId, userId);
                return;
            case "templateRename":
                handle(req, resp, () -> templateService.rename(templateId, userId, name),
                        "Đã đổi tên mẫu.", "/order/history");
                return;
            case "templateSetQty":
                handle(req, resp, () -> templateService.setQuantity(templateId, userId,
                                WebUtil.getInt(req, "productId", 0),
                                WebUtil.getInt(req, "quantity", 0)),
                        null, "/order/history");
                return;
            case "templateDelete":
                handle(req, resp, () -> templateService.delete(templateId, userId),
                        "Đã xoá mẫu.", "/order/history");
                return;
            case "templateSave":
            default:
                handle(req, resp, () -> templateService.saveFromOrder(userId,
                                WebUtil.getInt(req, "orderId", 0), name),
                        "Đã lưu thành mẫu đặt nhanh.", "/order/history");
        }
    }

    /**
     * Nạp mẫu vào giỏ rồi đưa khách sang thẳng giỏ hàng.
     * <p>
     * Không dùng {@code handle()} vì hai lẽ: đường đi khi thành công khác đường đi khi hỏng, và
     * thông báo phải dựng từ kết quả trả về — <b>món nào bị bỏ qua</b> mới là phần khách cần
     * biết. Báo "đã nạp xong" rồi để họ tự phát hiện giỏ thiếu món ở bước chọn giờ đến lấy là
     * đúng kiểu hỏng mà cả tính năng này sinh ra để tránh.
     */
    private void applyTemplate(HttpServletRequest req, HttpServletResponse resp,
                               int templateId, int userId) throws IOException {
        try {
            TemplateApplyResult result = templateService.applyToCart(templateId, userId);
            String message = "Đã thêm " + result.getAddedCount() + " món vào giỏ.";
            if (result.isAnythingSkipped()) {
                WebUtil.flashError(req, message + " Bỏ qua " + result.getSkippedNames().size()
                        + " món không còn phục vụ: " + result.getSkippedText() + ".");
            } else {
                WebUtil.flashSuccess(req, message);
            }
            redirect(req, resp, "/cart");
        } catch (com.fastfood.common.exception.AppException e) {
            WebUtil.flashError(req, e.getMessage());
            redirect(req, resp, "/order/history");
        }
    }
}
