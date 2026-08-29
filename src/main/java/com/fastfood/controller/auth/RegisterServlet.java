package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/register")
public class RegisterServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    /** Hiển thị form đăng ký tài khoản Customer. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        forward(req, resp, "auth/register.jsp");
    }

    /**
     * Nhận thông tin đăng ký, gọi AuthService tạo tài khoản và thư xác thực, sau đó tạo session
     * cho customer mới.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            User user = authService.register(
                    WebUtil.getString(req, "fullName"),
                    WebUtil.getString(req, "email"),
                    WebUtil.getString(req, "phone"),
                    req.getParameter("password"),
                    req.getParameter("confirmPassword"),
                    WebUtil.baseUrl(req),
                    WebUtil.clientIp(req));

            String target = WebUtil.startRegisteredSession(req, user, "/menu");
            WebUtil.flashSuccess(req, "Đăng ký thành công. Chào mừng " + user.getFullName()
                    + "! Chúng tôi vừa gửi một thư xác thực tới " + user.getEmail()
                    + " — mở thư và bấm liên kết trong đó để đặt được đơn online.");
            redirect(req, resp, target);
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.setAttribute("fullName", WebUtil.getString(req, "fullName"));
            req.setAttribute("email", WebUtil.getString(req, "email"));
            req.setAttribute("phone", WebUtil.getString(req, "phone"));
            forward(req, resp, "auth/register.jsp");
        }
    }
}
