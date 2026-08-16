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

/**
 * Bước một của luồng quên mật khẩu: nhận email và gửi đi liên kết đặt lại.
 * <p>
 * Trang này <b>luôn</b> trả về cùng một thông báo, dù email vừa gõ có tồn tại hay không —
 * xem {@link PasswordResetService} để biết vì sao. Vì vậy đừng thêm bất kỳ nhánh nào ở đây
 * làm cho hai trường hợp trông khác nhau, kể cả khác nhau ở tốc độ trả lời.
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends BaseServlet {

    private static final Logger LOG = Logger.getLogger(ForgotPasswordServlet.class.getName());

    /** Câu trả lời duy nhất của trang này. Một chuỗi, một nơi, để không có cách nào lệch. */
    private static final String ALWAYS = "Nếu địa chỉ này có tài khoản, chúng tôi đã gửi một liên kết "
            + "đặt lại mật khẩu tới hộp thư của bạn. Vui lòng kiểm tra trong ít phút tới.";

    private final PasswordResetService resetService = new PasswordResetService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (WebUtil.currentUser(req) != null) {
            // Đang đăng nhập thì đổi mật khẩu ở trang tài khoản, không cần đi vòng qua hộp thư.
            redirect(req, resp, "/profile");
            return;
        }
        forward(req, resp, "auth/forgot-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            resetService.request(WebUtil.getString(req, "email"),
                    WebUtil.baseUrl(req), WebUtil.clientIp(req));
        } catch (RuntimeException e) {
            // Nuốt lỗi có chủ ý. Cơ sở dữ liệu trục trặc hay kênh gửi tin hỏng đều không được
            // biến thành một thông báo khác — khác thông báo là lộ ngay email nào có thật.
            // Lỗi vẫn nằm đủ trong log máy chủ để người vận hành nhìn thấy.
            LOG.log(Level.SEVERE, "Loi khi xu ly yeu cau dat lai mat khau", e);
        }
        WebUtil.flashSuccess(req, ALWAYS);
        redirect(req, resp, "/forgot-password");
    }
}
