package com.fastfood.controller.admin;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.Category;
import com.fastfood.model.entity.User;
import com.fastfood.service.admin.AdminService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Quản lý nhóm món.
 * <p>
 * Xoá ở đây là <b>ẩn nhóm</b> khỏi thực đơn, kéo theo toàn bộ món trong nhóm — cách nhanh nhất
 * để ngừng bán cả một dòng sản phẩm. Xoá mềm vì các món vẫn trỏ tới nhóm bằng khoá ngoại.
 * Nút Ẩn đi theo nhánh {@code retire} riêng, tách khỏi form Sửa.
 */
@WebServlet("/admin/categories")
public class CategoryManageServlet extends BaseServlet {

    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        int editId = WebUtil.getInt(req, "edit", 0);
        req.setAttribute("categories", adminService.listCategories());
        if (editId > 0) {
            req.setAttribute("editing", adminService.findCategory(editId));
        }
        forward(req, resp, "admin/category.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User admin = requireUser(req);
        String action = WebUtil.getString(req, "action");

        if ("retire".equals(action) || "restore".equals(action)) {
            int categoryId = WebUtil.getInt(req, "categoryId", 0);
            String status = "restore".equals(action) ? "ACTIVE" : "INACTIVE";
            handle(req, resp, () -> adminService.setCategoryStatus(admin.getUserId(), categoryId, status),
                    "restore".equals(action) ? "Đã hiện lại nhóm món." : "Đã ẩn nhóm món khỏi thực đơn.",
                    "/admin/categories");
            return;
        }

        Category form = new Category();
        form.setCategoryId(WebUtil.getInt(req, "categoryId", 0));
        form.setName(WebUtil.getString(req, "name"));
        form.setDisplayOrder(WebUtil.getInt(req, "displayOrder", 0));
        // Trạng thái hiển thị cố ý không đọc từ form: nó thuộc về nhánh retire/restore ở trên.

        handle(req, resp, () -> adminService.saveCategory(admin.getUserId(), form),
                form.getCategoryId() > 0 ? "Đã cập nhật nhóm món." : "Đã thêm nhóm món.",
                "/admin/categories");
    }
}
