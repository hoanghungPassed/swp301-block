package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.CartView;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.customer.CustomerOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@WebServlet("/cart")
public class CartServlet extends BaseServlet {

    private final CartService cartService = new CartService();
    private final CustomerOrderService orderService = new CustomerOrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        CartView cart = cartService.getCart(user.getUserId());
        req.setAttribute("cart", cart);

        if (cart.isCheckoutable()) {
            LocalDateTime earliest = orderService.earliestPickupTime();
            req.setAttribute("minPickupTime", DateTimeUtil.toHtmlInput(earliest));
            req.setAttribute("suggestedPickupTime", DateTimeUtil.toHtmlInput(earliest.plusMinutes(15)));
            req.setAttribute("minLeadMinutes", AppConfig.pickupMinLeadMinutes());
            req.setAttribute("paymentExpiryMinutes", AppConfig.paymentExpiryMinutes());
            req.setAttribute("idempotencyKey", UUID.randomUUID().toString());
        }
        forward(req, resp, "customer/cart.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String action = WebUtil.getString(req, "action");
        int userId = user.getUserId();

        switch (action == null ? "" : action) {
            case "add": {
                int productId = WebUtil.getInt(req, "productId", 0);
                int quantity = WebUtil.getInt(req, "quantity", 1);
                String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/menu");
                handle(req, resp, () -> cartService.addProduct(userId, productId, quantity),
                        "Đã thêm món vào giỏ hàng.", back);
                return;
            }
            case "update": {
                int cartItemId = WebUtil.getInt(req, "cartItemId", 0);
                int quantity = WebUtil.getInt(req, "quantity", 1);
                handle(req, resp, () -> cartService.updateQuantity(userId, cartItemId, quantity),
                        null, "/cart");
                return;
            }
            case "remove": {
                int cartItemId = WebUtil.getInt(req, "cartItemId", 0);
                handle(req, resp, () -> cartService.removeItem(userId, cartItemId),
                        "Đã bỏ món khỏi giỏ hàng.", "/cart");
                return;
            }
            case "removeUnavailable": {
                handle(req, resp, () -> cartService.removeUnavailable(userId),
                        "Đã bỏ các món không còn phục vụ.", "/cart");
                return;
            }
            case "placeOrder": {
                LocalDateTime pickupTime = WebUtil.getDateTime(req, "pickupTime");
                String idempotencyKey = WebUtil.getString(req, "idempotencyKey");
                try {
                    Order order = orderService.createOnlineOrder(userId, pickupTime, idempotencyKey);
                    redirect(req, resp, "/payment/start?orderId=" + order.getOrderId());
                } catch (AppException e) {
                    WebUtil.flashError(req, e.getMessage());
                    redirect(req, resp, "/cart");
                }
                return;
            }
            default:
                redirect(req, resp, "/cart");
        }
    }
}
