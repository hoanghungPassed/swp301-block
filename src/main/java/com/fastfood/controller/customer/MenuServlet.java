package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.customer.CartService;
import com.fastfood.service.customer.FavouriteService;
import com.fastfood.service.shared.MenuService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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

        req.setAttribute("pageData", menuService.browsePage(categoryId, keyword, sort,
                WebUtil.getInt(req, "page", 1), Page.CARD_SIZE));
        req.setAttribute("categories", menuService.activeCategories());
        req.setAttribute("selectedCategory", categoryId);
        req.setAttribute("keyword", keyword);
        req.setAttribute("sort", sort);

        req.setAttribute("hasFilter",
                categoryId != null || (keyword != null && !keyword.isBlank()) || !"DEFAULT".equals(sort));

        User user = WebUtil.currentUser(req);
        if (user == null) {
            req.setAttribute("minLeadMinutes", AppConfig.pickupMinLeadMinutes());
            req.setAttribute("openHour", AppConfig.storeOpenHour());
            req.setAttribute("closeHour", AppConfig.storeCloseHour());
        }
        if (user != null && "CUSTOMER".equals(user.getRoleName())) {
            req.setAttribute("cartCount", cartService.countItems(user.getUserId()));

            req.setAttribute("favouritePage", Page.of(favouriteService.listOf(user.getUserId()),
                    WebUtil.getInt(req, "favPage", 1), Page.SMALL_SIZE));
            req.setAttribute("favouriteIds", favouriteService.favouriteProductIds(user.getUserId()));

            int editId = WebUtil.getInt(req, "editFav", 0);
            if (editId > 0) {
                req.setAttribute("editingFavourite",
                        favouriteService.findOwn(editId, user.getUserId()));
            }
        }
        forward(req, resp, "customer/menu.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
