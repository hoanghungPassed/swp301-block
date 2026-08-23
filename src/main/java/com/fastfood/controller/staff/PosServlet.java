package com.fastfood.controller.staff;

import com.fastfood.common.constant.Constants.BusinessRule;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.dto.Dtos.PosCartLine;
import com.fastfood.model.dto.Dtos.PosLine;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.MenuService;
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

@WebServlet("/staff/pos")
public class PosServlet extends BaseServlet {

    private static final String CART_KEY = "posCart";

    private final MenuService menuService = new MenuService();
    private final StaffOrderService orderService = new StaffOrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        requireUser(req);
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

        req.setAttribute("pageData", menuService.browsePage(categoryId, keyword, null,
                WebUtil.getInt(req, "page", 1), Page.CARD_SIZE));
        req.setAttribute("categories", menuService.activeCategories());
        req.setAttribute("selectedCategory", categoryId);
        req.setAttribute("keyword", keyword);
        req.setAttribute("posLines", lines);
        req.setAttribute("posTotal", total);
        req.setAttribute("posUnavailable", anyUnavailable);
        forward(req, resp, "staff/pos.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User cashier = requireUser(req);
        String action = WebUtil.getString(req, "action");
        Map<Integer, Integer> cart = cart(req);
        /* Quay lại đúng trang thực đơn mà thu ngân đang mở, không nhảy về đầu. */
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/staff/pos");

        switch (action == null ? "" : action) {
            case "add": {
                int productId = WebUtil.getInt(req, "productId", 0);
                int current = cart.getOrDefault(productId, 0);
                if (current >= BusinessRule.MAX_QUANTITY_PER_LINE) {
                    WebUtil.flashError(req, tooManyMessage());
                } else {
                    cart.put(productId, current + 1);
                }
                redirect(req, resp, back);
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
                redirect(req, resp, back);
                return;
            }
            case "remove": {
                cart.remove(WebUtil.getInt(req, "productId", 0));
                redirect(req, resp, back);
                return;
            }
            case "clear": {
                cart.clear();
                redirect(req, resp, back);
                return;
            }
            case "pay": {
                if (cart.isEmpty()) {
                    WebUtil.flashError(req, "Chưa chọn món nào.");
                    redirect(req, resp, back);
                    return;
                }
                try {
                    Order order = orderService.createPosOrder(cashier.getUserId(), toLines(cart));
                    cart.clear();
                    WebUtil.flashSuccess(req, "Đã lập đơn #" + order.getOrderId()
                            + " và chuyển xuống bếp.");
                    redirect(req, resp, "/staff/order/detail?orderId=" + order.getOrderId());
                } catch (AppException e) {
                    WebUtil.flashError(req, e.getMessage());
                    redirect(req, resp, back);
                }
                return;
            }
            case "qr": {
                if (cart.isEmpty()) {
                    WebUtil.flashError(req, "Chưa chọn món nào.");
                    redirect(req, resp, back);
                    return;
                }
                try {
                    Order order = orderService.createPosQrOrder(cashier.getUserId(), toLines(cart));
                    /* Giỏ dọn ngay dù tiền chưa về: đơn đã có mã, mọi thao tác tiếp theo diễn ra
                       trên trang mã QR, còn quầy thì rảnh tay phục vụ khách sau. Khách bỏ đi
                       không trả thì thu ngân huỷ đơn ở chính trang đó. */
                    cart.clear();
                    redirect(req, resp, "/staff/pos/qr?orderId=" + order.getOrderId());
                } catch (AppException e) {
                    WebUtil.flashError(req, e.getMessage());
                    redirect(req, resp, back);
                }
                return;
            }
            default:
                redirect(req, resp, back);
        }
    }

    private List<PosLine> toLines(Map<Integer, Integer> cart) {
        List<PosLine> lines = new ArrayList<>();
        cart.forEach((productId, quantity) -> lines.add(new PosLine(productId, quantity)));
        return lines;
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
