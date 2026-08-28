package com.fastfood.controller.auth;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    /** Chuyển yêu cầu GET cũ sang POST logout để thao tác thay đổi session không chạy bằng link. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        redirect(req, resp, "/");
    }

    /** Ghi audit đăng xuất, hủy session hiện tại và chuyển về trang đăng nhập. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = WebUtil.currentUser(req);
        if (user != null) {
            authService.logout(user.getUserId(), WebUtil.clientIp(req));
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        redirect(req, resp, "/menu");
    }
}
