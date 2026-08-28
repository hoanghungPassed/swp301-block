package com.fastfood.controller.customer;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/notifications")
public class NotificationServlet extends BaseServlet {

    private final NotificationService notificationService = new NotificationService();

    /** Hiển thị danh sách thông báo có phân trang của người đang đăng nhập. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        User user = requireUser(req);
        req.setAttribute("pageData",
                notificationService.pageOfUser(user.getUserId(), WebUtil.getInt(req, "page", 1)));
        forward(req, resp, "customer/notifications.jsp");
    }

    /** Đánh dấu toàn bộ thông báo chưa đọc của người đang đăng nhập thành đã đọc. */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/notifications");
        handle(req, resp, () -> notificationService.markAllRead(user.getUserId()),
                "Đã đánh dấu toàn bộ thông báo là đã đọc.", back);
    }
}
