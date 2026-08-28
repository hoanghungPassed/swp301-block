package com.fastfood.controller.auth;

import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.service.auth.PasswordResetService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends BaseServlet {

    private static final Logger LOG = Logger.getLogger(ForgotPasswordServlet.class.getName());

    private static final String ALWAYS = "Nếu địa chỉ này có tài khoản, chúng tôi đã gửi một liên kết "
            + "đặt lại mật khẩu tới hộp thư của bạn. Vui lòng kiểm tra trong ít phút tới.";

    private final PasswordResetService resetService = new PasswordResetService();

    /** Hiển thị form nhập email yêu cầu đặt lại mật khẩu. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (WebUtil.currentUser(req) != null) {
            redirect(req, resp, "/profile");
            return;
        }
        forward(req, resp, "auth/forgot-password.jsp");
    }

    /**
     * Tạo và gửi liên kết đặt lại mật khẩu nếu email tồn tại nhưng luôn trả cùng một thông báo
     * để không làm lộ email nào đã đăng ký.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            resetService.request(WebUtil.getString(req, "email"),
                    WebUtil.baseUrl(req), WebUtil.clientIp(req));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Loi khi xu ly yeu cau dat lai mat khau", e);
        }
        WebUtil.flashSuccess(req, ALWAYS);
        redirect(req, resp, "/forgot-password");
    }
}
