package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Đăng nhập cho cả bốn vai trò. */
@WebServlet("/login")
public class LoginServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (WebUtil.currentUser(req) != null) {
            redirect(req, resp, "/");
            return;
        }
        forward(req, resp, "auth/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String email = WebUtil.getString(req, "email");
        try {
            User user = authService.login(email, req.getParameter("password"), WebUtil.clientIp(req));

            // Cấp phiên mới sau khi xác thực để tránh bị chiếm phiên đã biết trước
            redirect(req, resp, WebUtil.startAuthenticatedSession(req, user, "/"));
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.setAttribute("email", email);
            forward(req, resp, "auth/login.jsp");
        }
    }
}
