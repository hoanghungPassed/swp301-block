package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.customer.FavouriteService;
import com.fastfood.service.shared.MenuService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Thực đơn — trang công khai, không cần đăng nhập để xem.
 * <p>
 * Đây cũng là nơi khách quản lý <b>món quen</b> của mình. Đặt ở đây chứ không tách thành màn hình
 * riêng vì đánh dấu một món là việc làm ngay lúc đang nhìn thấy nó; bắt mở trang khác thì không
 * ai đánh dấu.
 * <p>
 * <b>Trang công khai nên mọi yêu cầu đều lọt qua {@code AuthenticationFilter}</b>, kể cả yêu cầu
 * ghi dữ liệu của người chưa đăng nhập. Vì vậy mỗi thao tác ghi ở đây đều đi qua
 * {@link #userOrLogin}, thứ đưa khách sang trang đăng nhập rồi trả họ về đúng thực đơn — chứ
 * không ném ra lỗi 401 cho một việc hoàn toàn hợp lệ, chỉ là làm sớm một bước.
 */
@WebServlet("/menu")
public class MenuServlet extends BaseServlet {

    private final MenuService menuService = new MenuService();
    private final CartService cartService = new CartService();
    private final FavouriteService favouriteService = new FavouriteService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Integer categoryId = WebUtil.getInteger(req, "categoryId");
        String keyword = WebUtil.getString(req, "keyword");
        String sort = menuService.sortOrDefault(WebUtil.getString(req, "sort"));

        req.setAttribute("products", menuService.browse(categoryId, keyword, sort));
        req.setAttribute("categories", menuService.activeCategories());
        req.setAttribute("selectedCategory", categoryId);
        req.setAttribute("keyword", keyword);
        /* Mã đã qua kiểm chứ không phải chuỗi thô: trang dùng nó để tô ô đang chọn, nên nếu
           gửi lại nguyên xi thì một địa chỉ ?sort=bậy sẽ hiện ra một lựa chọn không tồn tại
           trong khi danh sách bên dưới thật ra đang xếp theo thứ tự mặc định. */
        req.setAttribute("sort", sort);

        /* "Đang lọc hay không" quyết định có hiện nút bỏ lọc và dòng đếm kết quả. Tính ở đây
           một lần thay vì lặp lại cái điều kiện ba vế ấy ở bốn chỗ trong trang. */
        req.setAttribute("hasFilter",
                categoryId != null || (keyword != null && !keyword.isBlank()) || !"DEFAULT".equals(sort));

        User user = WebUtil.currentUser(req);
        if (user == null) {
            /* Khối giới thiệu ở đầu trang nói ra luật đặt trước bằng con số. Lấy từ cấu hình
               chứ không viết cứng vào trang: đổi mốc đặt trước hay giờ mở cửa trong tệp cấu
               hình mà trang vẫn hứa con số cũ thì lời hứa đó thành sai ngay từ màn đầu tiên
               khách nhìn thấy. */
            req.setAttribute("minLeadMinutes", AppConfig.pickupMinLeadMinutes());
            req.setAttribute("openHour", AppConfig.storeOpenHour());
            req.setAttribute("closeHour", AppConfig.storeCloseHour());
        }
        if (user != null && "CUSTOMER".equals(user.getRoleName())) {
            req.setAttribute("cartCount", cartService.countItems(user.getUserId()));

            /* Danh sách đầy đủ và tập mã món phục vụ hai việc khác nhau: danh sách dựng khối
               "Món quen của tôi" ở đầu trang — kể cả món đang không bán được, thứ mà lưới thực
               đơn không hiện — còn tập mã dùng để tô dấu trên chính lưới đó. */
            req.setAttribute("favourites", favouriteService.listOf(user.getUserId()));
            req.setAttribute("favouriteIds", favouriteService.favouriteProductIds(user.getUserId()));

            int editId = WebUtil.getInt(req, "editFav", 0);
            if (editId > 0) {
                req.setAttribute("editingFavourite",
                        favouriteService.findOwn(editId, user.getUserId()));
            }
        }
        forward(req, resp, "customer/menu.jsp");
    }

    /** Ba thao tác trên món quen. Giữ lại bộ lọc đang mở để khách không bị đẩy về đầu thực đơn. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        /* Đường quay về do biểu mẫu gửi lên, đi qua safeRedirect. Không dựng lại từ
           getParameterMap: với một yêu cầu POST, bản đồ đó gồm cả các trường trong thân biểu
           mẫu, nên đường quay về sẽ mang theo cả action và productId. */
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/menu");

        User user = userOrLogin(req, resp, back);
        if (user == null) {
            return;
        }
        int userId = user.getUserId();
        String note = WebUtil.getString(req, "note");
        String action = WebUtil.getString(req, "action");

        switch (action == null ? "" : action) {
            case "favNote":
                handle(req, resp, () -> favouriteService.updateNote(
                                WebUtil.getInt(req, "favouriteId", 0), userId, note),
                        "Đã lưu ghi chú.", back);
                return;
            case "favRemove":
                handle(req, resp, () -> favouriteService.remove(
                                WebUtil.getInt(req, "favouriteId", 0), userId),
                        "Đã bỏ khỏi món quen.", back);
                return;
            case "favRemoveByProduct":
                handle(req, resp, () -> favouriteService.removeByProduct(
                                userId, WebUtil.getInt(req, "productId", 0)),
                        "Đã bỏ khỏi món quen.", back);
                return;
            case "favAdd":
            default:
                handle(req, resp, () -> favouriteService.add(
                                userId, WebUtil.getInt(req, "productId", 0), note),
                        "Đã thêm vào món quen.", back);
        }
    }
}
