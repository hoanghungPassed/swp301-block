package com.fastfood.controller.customer;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.FavouriteService;
import com.fastfood.service.customer.ReviewService;
import com.fastfood.service.shared.MenuService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Chi tiết một món, kèm <b>đánh giá của khách</b> và nút đánh dấu <b>món quen</b>.
 * <p>
 * Trang công khai: ai cũng đọc được đánh giá, kể cả khi chưa đăng nhập — đó chính là lúc người
 * ta cần đọc nhất, trước khi quyết định có đặt hay không. Nhưng chỉ khách <b>đã nhận món</b> mới
 * viết được; chốt chặn đó nằm ở {@code ReviewService}.
 * <p>
 * Vì trang nằm ngoài {@code AuthenticationFilter}, mọi yêu cầu ghi đều đi qua {@link #userOrLogin}
 * thay vì {@code requireUser} — cùng lý do đã ghi ở {@link MenuServlet}.
 */
@WebServlet("/product/detail")
public class ProductDetailServlet extends BaseServlet {

    private final MenuService menuService = new MenuService();
    private final ReviewService reviewService = new ReviewService();
    private final FavouriteService favouriteService = new FavouriteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int productId = WebUtil.getInt(req, "id", 0);
        try {
            req.setAttribute("product", menuService.detail(productId));

            User user = WebUtil.currentUser(req);
            Integer customerId = user != null && "CUSTOMER".equals(user.getRoleName())
                    ? user.getUserId() : null;

            req.setAttribute("reviews", reviewService.reviewsOf(productId));
            req.setAttribute("reviewSummary", reviewService.summaryOf(productId));
            req.setAttribute("myReview", reviewService.myReview(productId, customerId));
            req.setAttribute("canReview", reviewService.canReview(productId, customerId));
            req.setAttribute("editingReview", WebUtil.getBoolean(req, "editReview"));

            /* Món quen đánh dấu được ngay tại đây, không chỉ trên lưới thực đơn. Trang này mới
               là chỗ khách đọc mô tả và đánh giá rồi quyết định là mình thích món — bắt họ quay
               ra lưới để bấm một ngôi sao là chặn đúng lúc họ vừa muốn bấm. */
            req.setAttribute("isFavourite",
                    favouriteService.favouriteProductIds(customerId).contains(productId));

            forward(req, resp, "customer/product-detail.jsp");
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            forward(req, resp, "error/404.jsp");
        }
    }

    /** Ba thao tác trên đánh giá của chính khách. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int productId = WebUtil.getInt(req, "productId", 0);
        String back = "/product/detail?id=" + productId;

        User user = userOrLogin(req, resp, back);
        if (user == null) {
            return;
        }
        int userId = user.getUserId();
        int reviewId = WebUtil.getInt(req, "reviewId", 0);
        int rating = WebUtil.getInt(req, "rating", 0);
        String comment = WebUtil.getString(req, "comment");

        switch (WebUtil.getString(req, "action") == null ? "" : WebUtil.getString(req, "action")) {
            case "reviewUpdate":
                handle(req, resp, () -> reviewService.update(reviewId, userId, rating, comment),
                        "Đã cập nhật đánh giá.", back);
                return;
            case "reviewDelete":
                handle(req, resp, () -> reviewService.delete(reviewId, userId),
                        "Đã xoá đánh giá.", back);
                return;
            case "reviewAdd":
            default:
                handle(req, resp, () -> reviewService.add(productId, userId, rating, comment),
                        "Cảm ơn bạn đã đánh giá.", back);
        }
    }
}
