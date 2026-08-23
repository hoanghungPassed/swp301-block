package com.fastfood.controller.admin;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.admin.AdminService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/admin/products")
public class ProductManageServlet extends BaseServlet {

    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Integer categoryId = WebUtil.getInteger(req, "categoryId");
        String keyword = WebUtil.getString(req, "keyword");
        String status = WebUtil.getString(req, "status");
        String stock = WebUtil.getString(req, "stock");
        int editId = WebUtil.getInt(req, "edit", 0);

        req.setAttribute("pageData", adminService.listProducts(categoryId, keyword, status,
                availableFilter(stock), WebUtil.getInt(req, "page", 1)));
        req.setAttribute("categories", adminService.listCategories());
        req.setAttribute("selectedCategory", categoryId);
        req.setAttribute("keyword", keyword);
        req.setAttribute("status", status);
        req.setAttribute("stock", stock);
        /* Giữ cả số trang: ngừng bán một món ở trang 3 xong phải quay lại đúng trang 3.
           Chỉ bỏ "edit" để lưu xong thì khung sửa đóng lại. */
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "edit"));
        if (editId > 0) {
            req.setAttribute("editing", adminService.findProduct(editId));
        }
        forward(req, resp, "admin/product.jsp");
    }

    private Boolean availableFilter(String stock) {
        if ("IN".equals(stock)) {
            return Boolean.TRUE;
        }
        if ("OUT".equals(stock)) {
            return Boolean.FALSE;
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User admin = requireUser(req);
        String action = WebUtil.getString(req, "action");
        String back = WebUtil.pathWithFilters("/admin/products", WebUtil.getString(req, "back"));

        if ("toggle".equals(action)) {
            int productId = WebUtil.getInt(req, "productId", 0);
            boolean available = WebUtil.getBoolean(req, "available");
            handle(req, resp, () -> adminService.toggleProductAvailability(admin.getUserId(), productId, available),
                    available ? "Đã đánh dấu món còn hàng." : "Đã đánh dấu món tạm hết hàng.", back);
            return;
        }

        if ("retire".equals(action) || "restore".equals(action)) {
            int productId = WebUtil.getInt(req, "productId", 0);
            String status = "restore".equals(action) ? "ACTIVE" : "INACTIVE";
            handle(req, resp, () -> adminService.setProductStatus(admin.getUserId(), productId, status),
                    "restore".equals(action) ? "Đã bán lại món này." : "Đã ngừng bán món này.", back);
            return;
        }

        Product form = new Product();
        form.setProductId(WebUtil.getInt(req, "productId", 0));
        form.setCategoryId(WebUtil.getInt(req, "categoryId", 0));
        form.setName(WebUtil.getString(req, "name"));
        form.setDescription(WebUtil.getString(req, "description"));
        form.setImageUrl(WebUtil.getString(req, "imageUrl"));
        form.setAvailable(WebUtil.getBoolean(req, "available"));
        try {
            form.setPrice(new BigDecimal(WebUtil.getString(req, "price")));
        } catch (RuntimeException e) {
            form.setPrice(BigDecimal.valueOf(-1));
        }

        handle(req, resp, () -> adminService.saveProduct(admin.getUserId(), form),
                form.getProductId() > 0 ? "Đã cập nhật món ăn." : "Đã thêm món mới.", back);
    }
}
