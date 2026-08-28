package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.PasswordResetService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/reset-password")
public class ResetPasswordServlet extends BaseServlet {

    private final PasswordResetService resetService = new PasswordResetService();

    /** Kiểm tra token trên liên kết còn dùng được rồi mới hiển thị form đặt mật khẩu mới. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String token = WebUtil.getString(req, "token");
        User owner = resetService.findAccountFor(token);
        if (owner == null) {
            WebUtil.flashError(req, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. "
                    + "Vui lòng yêu cầu một liên kết mới.");
            redirect(req, resp, "/forgot-password");
            return;
        }
        req.setAttribute("token", token);
        req.setAttribute("email", owner.getEmail());
        forward(req, resp, "auth/reset-password.jsp");
    }

    /** Validate token và mật khẩu mới, cập nhật hash rồi vô hiệu hóa các token còn lại. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String token = WebUtil.getString(req, "token");
        try {
            resetService.complete(token,
                    req.getParameter("newPassword"),
                    req.getParameter("confirmPassword"),
                    WebUtil.clientIp(req));
        } catch (AppException e) {
            User owner = resetService.findAccountFor(token);
            if (owner == null) {
                WebUtil.flashError(req, e.getMessage());
                redirect(req, resp, "/forgot-password");
                return;
            }
            req.setAttribute("errorMessage", e.getMessage());
            req.setAttribute("token", token);
            req.setAttribute("email", owner.getEmail());
            forward(req, resp, "auth/reset-password.jsp");
            return;
        }
        WebUtil.flashSuccess(req, "Đã đặt mật khẩu mới. Vui lòng đăng nhập lại.");
        redirect(req, resp, "/login");
    }
}
