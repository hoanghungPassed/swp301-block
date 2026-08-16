package com.fastfood.controller.staff;

import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.PosCartLine;
import com.fastfood.model.dto.PosLine;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.MenuService;
import com.fastfood.service.staff.PosHoldService;
import com.fastfood.service.staff.ShiftService;
import com.fastfood.service.staff.StaffOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Màn hình bán hàng tại quầy.
 * <p>
 * Giỏ tạm giữ trong phiên làm việc của thu ngân chứ không ghi xuống cơ sở dữ liệu:
 * khách vãng lai không có tài khoản, và nếu ghi thì mỗi lần bấm nhầm lại để lại một dòng rác.
 * <p>
 * Khi bấm thu tiền, toàn bộ lập đơn - thu tiền - đưa xuống bếp diễn ra trong một giao dịch,
 * đúng như thao tác thật ở quầy.
 */
@WebServlet("/staff/pos")
public class PosServlet extends BaseServlet {

    private static final String CART_KEY = "posCart";

    private final MenuService menuService = new MenuService();
    private final StaffOrderService orderService = new StaffOrderService();
    private final PosHoldService holdService = new PosHoldService();
    private final ShiftService shiftService = new ShiftService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User cashier = requireUser(req);
        Integer categoryId = WebUtil.getInteger(req, "categoryId");
        String keyword = WebUtil.getString(req, "keyword");

        Map<Integer, Integer> cart = cart(req);
        List<PosCartLine> lines = orderService.describeCart(cart);
        BigDecimal total = BigDecimal.ZERO;
        boolean anyUnavailable = false;
        for (PosCartLine line : lines) {
            total = total.add(line.getLineTotal());
            anyUnavailable |= !line.isOrderable();
        }

        req.setAttribute("products", menuService.browse(categoryId, keyword));
        req.setAttribute("categories", menuService.activeCategories());
        req.setAttribute("selectedCategory", categoryId);
        req.setAttribute("keyword", keyword);
        req.setAttribute("posLines", lines);
        req.setAttribute("posTotal", total);
        // Còn một dòng không bán được thì cả phiếu không thu tiền được — createPosOrder sẽ từ
        // chối. Nói ra ở đây để thu ngân sửa trước, thay vì biết khi khách đã đưa tiền.
        req.setAttribute("posUnavailable", anyUnavailable);

        // Ca làm việc hiện ngay trên màn bán hàng, không chỉ ở trang lịch sử: đơn bán khi chưa
        // mở ca vẫn ghi nhận bình thường nhưng nằm ngoài bảng đối soát tiền cuối ca — xem
        // ShiftService. Cảnh báo phải nằm đúng chỗ người ta bán hàng thì mới có tác dụng.
        req.setAttribute("currentShift", shiftService.currentShift(cashier.getUserId()));

        // Phiếu treo của chính người đang đăng nhập. Không ai xem phiếu của người khác, nên
        // đây vừa là điều kiện lọc vừa là chốt chặn quyền.
        req.setAttribute("holds", holdService.myHolds(cashier.getUserId()));
        int editId = WebUtil.getInt(req, "editHold", 0);
        if (editId > 0) {
            req.setAttribute("editingHold", holdService.findOwn(editId, cashier.getUserId()));
        }
        forward(req, resp, "staff/pos.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        String action = WebUtil.getString(req, "action");
        Map<Integer, Integer> cart = cart(req);

        switch (action == null ? "" : action) {
            /* Hai nhánh dưới đây chặn số lượng ngay tại chỗ thay vì để dồn tới lúc thu tiền.
               Ngưỡng là cùng con số mà createPosOrder dùng — quá nó thì cả phiếu bị từ chối,
               và biết điều đó khi khách đã đứng chờ ở quầy là muộn. */
            case "add": {
                int productId = WebUtil.getInt(req, "productId", 0);
                int current = cart.getOrDefault(productId, 0);
                if (current >= BusinessRule.MAX_QUANTITY_PER_LINE) {
                    WebUtil.flashError(req, tooManyMessage());
                } else {
                    cart.put(productId, current + 1);
                }
                redirect(req, resp, "/staff/pos");
                return;
            }
            case "setQty": {
                int productId = WebUtil.getInt(req, "productId", 0);
                int quantity = WebUtil.getInt(req, "quantity", 0);
                if (quantity <= 0) {
                    cart.remove(productId);
                } else if (quantity > BusinessRule.MAX_QUANTITY_PER_LINE) {
                    WebUtil.flashError(req, tooManyMessage());
                } else {
                    cart.put(productId, quantity);
                }
                redirect(req, resp, "/staff/pos");
                return;
            }
            case "remove": {
                cart.remove(WebUtil.getInt(req, "productId", 0));
                redirect(req, resp, "/staff/pos");
                return;
            }
            case "clear": {
                cart.clear();
                redirect(req, resp, "/staff/pos");
                return;
            }
            case "pay": {
                if (cart.isEmpty()) {
                    WebUtil.flashError(req, "Chưa chọn món nào.");
                    redirect(req, resp, "/staff/pos");
                    return;
                }
                // Không suy diễn hình thức thanh toán từ "khác CASH thì là quẹt thẻ": tham số
                // gõ sai sẽ lặng lẽ ghi nhận sai loại tiền và làm sai luôn báo cáo đối soát.
                String raw = WebUtil.getString(req, "method");
                PaymentMethod method;
                try {
                    method = PaymentMethod.valueOf(raw == null ? "" : raw.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    WebUtil.flashError(req, "Hình thức thanh toán không hợp lệ.");
                    redirect(req, resp, "/staff/pos");
                    return;
                }
                String reference = WebUtil.getString(req, "reference");

                List<PosLine> lines = new ArrayList<>();
                cart.forEach((productId, quantity) -> lines.add(new PosLine(productId, quantity)));

                try {
                    Order order = orderService.createPosOrder(cashier.getUserId(), lines, method, reference);
                    cart.clear();
                    WebUtil.flashSuccess(req, "Đã lập đơn #" + order.getOrderId()
                            + " và chuyển xuống bếp.");
                    redirect(req, resp, "/staff/order/detail?orderId=" + order.getOrderId());
                } catch (AppException e) {
                    WebUtil.flashError(req, e.getMessage());
                    redirect(req, resp, "/staff/pos");
                }
                return;
            }
            case "hold": {
                // Chụp lại giỏ trước khi gọi dịch vụ: treo xong mới xoá giỏ, và chỉ xoá khi
                // thật sự đã ghi được xuống. Xoá trước rồi mới ghi thì một lỗi nhập liệu —
                // ví dụ để trống tên phiếu — sẽ làm mất trắng giỏ đang dở.
                Map<Integer, Integer> chup = new LinkedHashMap<>(cart);
                handle(req, resp, () -> {
                    holdService.hold(cashier.getUserId(), WebUtil.getString(req, "label"),
                            WebUtil.getString(req, "note"), chup);
                    cart.clear();
                }, "Đã treo phiếu. Giỏ đã trống, mời khách tiếp theo.", "/staff/pos");
                return;
            }
            case "resume": {
                /* Không nạp chồng lên giỏ đang có món. Trộn hai giỏ của hai khách khác nhau là
                   lỗi có hậu quả bằng tiền, và nó xảy ra lặng lẽ: thu ngân bấm lấy phiếu ra rồi
                   thu tiền, không ai đọc lại từng dòng. */
                if (!cart.isEmpty()) {
                    WebUtil.flashError(req, "Giỏ đang có món của khách khác. "
                            + "Hãy tính tiền, treo lại, hoặc xoá giỏ trước khi lấy phiếu ra.");
                    redirect(req, resp, "/staff/pos");
                    return;
                }
                int holdId = WebUtil.getInt(req, "holdId", 0);
                handle(req, resp, () -> cart.putAll(holdService.resume(holdId, cashier.getUserId())),
                        "Đã lấy phiếu ra. Kiểm lại món rồi thu tiền.", "/staff/pos");
                return;
            }
            case "holdRename": {
                int holdId = WebUtil.getInt(req, "holdId", 0);
                handle(req, resp, () -> holdService.rename(holdId, cashier.getUserId(),
                                WebUtil.getString(req, "label"), WebUtil.getString(req, "note")),
                        "Đã cập nhật phiếu treo.", "/staff/pos");
                return;
            }
            case "holdSetQty": {
                int holdId = WebUtil.getInt(req, "holdId", 0);
                int productId = WebUtil.getInt(req, "productId", 0);
                int quantity = WebUtil.getInt(req, "quantity", 0);
                handle(req, resp, () -> holdService.setQuantity(holdId, cashier.getUserId(),
                                productId, quantity),
                        null, "/staff/pos");
                return;
            }
            case "holdDiscard": {
                int holdId = WebUtil.getInt(req, "holdId", 0);
                handle(req, resp, () -> holdService.discard(holdId, cashier.getUserId()),
                        "Đã bỏ phiếu treo.", "/staff/pos");
                return;
            }
            default:
                redirect(req, resp, "/staff/pos");
        }
    }

    private String tooManyMessage() {
        return "Mỗi món chỉ bán được tối đa " + BusinessRule.MAX_QUANTITY_PER_LINE
                + " phần trên một đơn. Khách mua nhiều hơn thì tách thành đơn thứ hai.";
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Integer> cart(HttpServletRequest req) {
        HttpSession session = req.getSession();
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute(CART_KEY);
        if (cart == null) {
            cart = new LinkedHashMap<>();
            session.setAttribute(CART_KEY, cart);
        }
        return cart;
    }
}
