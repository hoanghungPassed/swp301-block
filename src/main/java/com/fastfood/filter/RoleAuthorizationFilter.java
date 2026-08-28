package com.fastfood.filter;

import com.fastfood.common.constant.Constants.RoleName;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.UserEntities.User;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RoleAuthorizationFilter implements Filter {

    @Override
    /** Xác định role theo URL, buộc đăng nhập và chặn Customer truy cập màn staff/admin. */
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = RequestPath.of(req);
        boolean isApi = path.startsWith("/api/");

        User user = WebUtil.currentUser(req);
        if (user == null) {
            deny(req, resp, isApi, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        RoleName required = requiredRole(path);
        RoleName actual = RoleName.parse(user.getRoleName());

        boolean allowed = actual != null && (actual == required || actual == RoleName.ADMIN);
        if (!allowed) {
            deny(req, resp, isApi, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Trả JSON cho API hoặc chuyển sang trang lỗi phù hợp cho request giao diện. */
    private void deny(HttpServletRequest req, HttpServletResponse resp, boolean isApi, int status)
            throws IOException, ServletException {
        if (isApi) {
            resp.setStatus(status);
            return;
        }
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        req.setAttribute("errorMessage",
                "Tài khoản của bạn không có quyền truy cập khu vực này.");
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.getRequestDispatcher("/WEB-INF/views/error/403.jsp").forward(req, resp);
    }

    /** Ánh xạ tiền tố URL /admin, /staff, /kitchen sang role bắt buộc; URL Customer trả null. */
    public static RoleName requiredRole(String path) {
        if (path != null && path.startsWith("/staff/")) {
            return RoleName.CASHIER;
        }
        if (path != null && (path.startsWith("/kitchen/") || path.startsWith("/api/kds/"))) {
            return RoleName.KITCHEN;
        }
        return RoleName.ADMIN;
    }
}
