package com.fastfood.controller.admin;

import com.fastfood.common.util.PasswordUtil;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.admin.AdminService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/admin/users")
public class UserManageServlet extends BaseServlet {

    private final AdminService adminService = new AdminService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String roleName = WebUtil.getString(req, "role");
        String keyword = WebUtil.getString(req, "keyword");
        String status = WebUtil.getString(req, "status");
        int editId = WebUtil.getInt(req, "edit", 0);

        req.setAttribute("pageData", adminService.listUsers(roleName, keyword, status,
                WebUtil.getInt(req, "page", 1)));
        req.setAttribute("staffRoles", adminService.listStaffRoles());
        req.setAttribute("role", roleName);
        req.setAttribute("keyword", keyword);
        req.setAttribute("status", status);
        req.setAttribute("filterQuery", WebUtil.queryStringWithout(req, "page", "edit"));
        if (editId > 0) {
            req.setAttribute("editing", adminService.findUser(editId));
        }
        forward(req, resp, "admin/user.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User admin = requireUser(req);
        String action = WebUtil.getString(req, "action");
        int userId = WebUtil.getInt(req, "userId", 0);
        String back = WebUtil.pathWithFilters("/admin/users", WebUtil.getString(req, "back"));

        switch (action == null ? "" : action) {
            case "create":
                handle(req, resp, () -> adminService.createStaff(admin.getUserId(),
                                WebUtil.getString(req, "fullName"),
                                WebUtil.getString(req, "email"),
                                WebUtil.getString(req, "phone"),
                                req.getParameter("password"),
                                WebUtil.getInt(req, "roleId", 0)),
                        "Đã tạo tài khoản nhân viên.", back);
                return;
            case "updateInfo":
                handle(req, resp, () -> adminService.updateUserInfo(admin.getUserId(), userId,
                                WebUtil.getString(req, "fullName"),
                                WebUtil.getString(req, "phone")),
                        "Đã cập nhật thông tin tài khoản.", back);
                return;
            case "lock":
                handle(req, resp, () -> adminService.setUserStatus(admin.getUserId(), userId, "LOCKED"),
                        "Đã khoá tài khoản.", back);
                return;
            case "unlock":
                handle(req, resp, () -> adminService.setUserStatus(admin.getUserId(), userId, "ACTIVE"),
                        "Đã mở khoá tài khoản.", back);
                return;
            case "resetPassword":
                String temporary = PasswordUtil.randomTemporary();
                handle(req, resp,
                        () -> adminService.resetPassword(admin.getUserId(), userId, temporary),
                        "Đã đặt lại mật khẩu. Mật khẩu tạm: " + temporary
                                + " — đọc cho người dùng, họ sẽ phải tự đổi ở lần đăng nhập sau.",
                        back);
                return;
            default:
                redirect(req, resp, back);
        }
    }
}
