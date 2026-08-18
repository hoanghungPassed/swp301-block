package com.fastfood.controller;

import com.fastfood.common.constant.Constants.RoleName;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    private final NotificationService notificationService = new NotificationService();

    protected void forward(HttpServletRequest req, HttpServletResponse resp, String view)
            throws ServletException, IOException {
        WebUtil.consumeFlash(req);
        attachUnreadNotifications(req);
        attachCurrentPath(req);
        req.getRequestDispatcher("/WEB-INF/views/" + view).forward(req, resp);
    }

    private void attachCurrentPath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        String path = pathInfo == null ? req.getServletPath() : req.getServletPath() + pathInfo;
        req.setAttribute("currentPath", path == null || path.isEmpty() ? "/menu" : path);
    }

    private void attachUnreadNotifications(HttpServletRequest req) {
        User user = WebUtil.currentUser(req);
        if (user == null || !RoleName.CUSTOMER.name().equals(user.getRoleName())) {
            return;
        }
        try {
            req.setAttribute("unreadNotifications", notificationService.unreadCount(user.getUserId()));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Khong dem duoc thong bao chua doc", e);
        }
    }

    protected void redirect(HttpServletRequest req, HttpServletResponse resp, String path)
            throws IOException {
        resp.sendRedirect(req.getContextPath() + path);
    }

    protected User requireUser(HttpServletRequest req) {
        User user = WebUtil.currentUser(req);
        if (user == null) {
            throw new AppException("Vui lòng đăng nhập.", 401);
        }
        return user;
    }

    protected User userOrLogin(HttpServletRequest req, HttpServletResponse resp, String returnTo)
            throws IOException {
        User user = WebUtil.currentUser(req);
        if (user != null) {
            return user;
        }
        req.getSession().setAttribute("redirectAfterLogin", WebUtil.safeRedirect(returnTo, "/menu"));
        WebUtil.flashError(req, "Vui lòng đăng nhập để dùng chức năng này.");
        redirect(req, resp, "/login");
        return null;
    }

    protected void handle(HttpServletRequest req, HttpServletResponse resp,
                          Action action, String successMessage, String redirectPath) throws IOException {
        try {
            action.run();
            if (successMessage != null) {
                WebUtil.flashSuccess(req, successMessage);
            }
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Loi khong mong doi tai " + req.getRequestURI(), e);
            WebUtil.flashError(req, "Có lỗi xảy ra, vui lòng thử lại.");
        }
        redirect(req, resp, redirectPath);
    }

    @FunctionalInterface
    protected interface Action {
        void run();
    }
}
