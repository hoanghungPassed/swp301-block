package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/profile")
public class ProfileServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    /** Tải lại thông tin tài khoản mới nhất từ database để hiển thị trang hồ sơ. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        req.setAttribute("profile", authService.findById(user.getUserId()));
        forward(req, resp, "customer/profile.jsp");
    }

    /**
     * Cập nhật họ tên/số điện thoại hoặc đổi mật khẩu tùy theo action của form; AuthService chịu
     * trách nhiệm validate và ghi dữ liệu.
     */
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
                user.setMustChangePassword(false);
            }, "Đã đổi mật khẩu.", "/profile");
            return;
        }

        String fullName = WebUtil.getString(req, "fullName");
        String phone = WebUtil.getString(req, "phone");
        handle(req, resp, () -> {
            authService.updateProfile(user.getUserId(), fullName, phone);
            user.setFullName(fullName);
            user.setPhone(phone);
        }, "Đã cập nhật thông tin tài khoản.", "/profile");
    }
}
