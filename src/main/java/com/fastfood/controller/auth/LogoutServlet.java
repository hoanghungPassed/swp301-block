package com.fastfood.controller.auth;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.AuthService;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Đăng xuất và huỷ phiên.
 * <p>
 * <b>Chỉ nhận POST.</b> Đăng xuất bằng một liên kết GET nghe thì tiện, nhưng khi đó bất kỳ trang
 * nào cũng đá được người dùng ra khỏi hệ thống chỉ bằng một thẻ {@code <img src=".../logout">} —
 * trình duyệt tự tải ảnh, không cần ai bấm gì. Nó không làm lộ dữ liệu, nhưng nó là thứ dùng để
 * quấy rối, và tệ hơn: đăng xuất nạn nhân đúng lúc rồi mời họ đăng nhập lại trên một trang giả.
 * Là POST kèm mã chống giả mạo thì chỉ trang của chính hệ thống mới gọi được.
 * <p>
 * Nút thoát trên thanh điều hướng vì vậy là một biểu mẫu, không phải một liên kết — xem
 * {@code layout/header.jspf}.
 */
@WebServlet("/logout")
public class LogoutServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    /**
     * Gõ thẳng {@code /logout} vào trình duyệt thì không đăng xuất, chỉ đưa về trang chủ.
     * Không báo lỗi 405: người gõ vào đây đang muốn thoát, và một trang lỗi không giúp họ làm
     * việc đó — nút thoát nằm sẵn trên thanh điều hướng của trang họ vừa được đưa tới.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        redirect(req, resp, "/");
    }

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
