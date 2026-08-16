package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.CartView;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.customer.CustomerOrderService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Giỏ hàng và đặt trước: xem, thêm, đổi số lượng, bỏ món, rồi chọn giờ đến lấy ngay tại chỗ.
 * <p>
 * Chọn giờ trước đây là một màn hình riêng. Tách ra thì khách phải nhớ giỏ hàng có gì trong
 * lúc chọn giờ, nên màn hình đó lại phải liệt kê lại toàn bộ giỏ — cùng một danh sách hiện
 * hai lần ở hai trang, và sửa số lượng thì phải quay ngược về trang trước.
 * <p>
 * Không có ô địa chỉ giao hàng và không có lựa chọn trả tiền tại quầy: đơn đặt trước bắt buộc
 * thanh toán online. Khách muốn trả tiền mặt thì mua trực tiếp tại cửa hàng.
 * <p>
 * Mọi thao tác ghi đều kết thúc bằng chuyển hướng chứ không hiển thị thẳng, để khách
 * bấm tải lại trang không vô tình thêm món lần nữa.
 */
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

        // Phần chọn giờ chỉ dựng lên khi giỏ thật sự đặt được. Giỏ trống hoặc còn món ngừng
        // bán thì không sinh khoá chống trùng, vì chẳng có đơn nào sắp được tạo.
        if (cart.isCheckoutable()) {
            LocalDateTime earliest = orderService.earliestPickupTime();
            req.setAttribute("minPickupTime", DateTimeUtil.toHtmlInput(earliest));
            req.setAttribute("suggestedPickupTime", DateTimeUtil.toHtmlInput(earliest.plusMinutes(15)));
            req.setAttribute("minLeadMinutes", AppConfig.pickupMinLeadMinutes());
            req.setAttribute("paymentExpiryMinutes", AppConfig.paymentExpiryMinutes());
            // Khoá chống trùng: khách bấm đúp hoặc tải lại trang cũng chỉ tạo một đơn
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
                // Không dùng handle(): đặt hàng thành công thì đi tiếp sang cổng thanh toán
                // chứ không quay lại giỏ, nên đường đi khi thành công và khi hỏng khác nhau.
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
