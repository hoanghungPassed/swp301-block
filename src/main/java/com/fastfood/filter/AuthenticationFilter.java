package com.fastfood.filter;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.SessionGuard;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

public class AuthenticationFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.jsp", "/menu", "/product/detail", "/login", "/logout", "/register",
            "/forgot-password", "/reset-password",
            "/verify-email",
            "/payment/payos/return",
            "/payment/payos/webhook",
            "/payment/sepay/webhook"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of("/assets/");

    private static final Set<String> PASSWORD_CHANGE_PATHS = Set.of("/profile", "/logout");

    private final SessionGuard sessionGuard = new SessionGuard();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = RequestPath.of(req);

        if (isPublicPath(path)) {
            refreshIfLoggedIn(req);
            chain.doFilter(request, response);
            return;
        }

        User user = WebUtil.currentUser(req);
        if (user == null) {
            String target = req.getQueryString() == null ? path : path + "?" + req.getQueryString();
            req.getSession().setAttribute(WebUtil.REDIRECT_AFTER_LOGIN, target);
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        user = sessionGuard.refresh(req);
        if (user == null) {
            WebUtil.flashError(req, "Tài khoản của bạn vừa bị khoá hoặc không còn hiệu lực. "
                    + "Vui lòng đăng nhập lại.");
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        if (user.isMustChangePassword() && !PASSWORD_CHANGE_PATHS.contains(path)) {
            WebUtil.flashError(req, "Mật khẩu của bạn vừa được quản trị viên đặt lại. "
                    + "Vui lòng đặt mật khẩu mới trước khi tiếp tục.");
            resp.sendRedirect(req.getContextPath() + "/profile");
            return;
        }
        chain.doFilter(request, response);
    }

    private void refreshIfLoggedIn(HttpServletRequest req) {
        if (WebUtil.currentUser(req) != null) {
            sessionGuard.refresh(req);
        }
    }

    public static boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
