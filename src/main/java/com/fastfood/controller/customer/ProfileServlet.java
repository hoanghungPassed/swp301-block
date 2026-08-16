package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Thông tin tài khoản và đổi mật khẩu. */
@WebServlet("/profile")
public class ProfileServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        req.setAttribute("profile", authService.findById(user.getUserId()));
        forward(req, resp, "customer/profile.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String action = WebUtil.getString(req, "action");

        if ("changePassword".equals(action)) {
            handle(req, resp, () -> {
                authService.changePassword(user.getUserId(),
                        req.getParameter("currentPassword"),
                        req.getParameter("newPassword"),
                        req.getParameter("confirmPassword"));
                // Gỡ cờ ngay trên đối tượng trong phiên. Chỉ ghi xuống cơ sở dữ liệu thôi thì
                // AuthenticationFilter vẫn đọc bản cũ trong phiên và giữ người dùng ở lại đây
                // mãi, dù họ vừa làm đúng việc được yêu cầu.
                user.setMustChangePassword(false);
            }, "Đã đổi mật khẩu.", "/profile");
            return;
        }

        String fullName = WebUtil.getString(req, "fullName");
        String phone = WebUtil.getString(req, "phone");
        handle(req, resp, () -> {
            authService.updateProfile(user.getUserId(), fullName, phone);
            // Cập nhật lại tên hiển thị trên thanh điều hướng ngay, không đợi đăng nhập lại
            user.setFullName(fullName);
            user.setPhone(phone);
        }, "Đã cập nhật thông tin tài khoản.", "/profile");
    }
}
