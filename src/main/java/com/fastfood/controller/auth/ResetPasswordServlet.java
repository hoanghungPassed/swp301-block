package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.PasswordResetService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Bước hai của luồng quên mật khẩu: đặt mật khẩu mới bằng mã trong liên kết.
 * <p>
 * Mã đi trong địa chỉ ở lần mở đầu tiên, rồi được chuyển sang ô ẩn của biểu mẫu. Không giữ nó
 * trong địa chỉ khi gửi biểu mẫu: địa chỉ nằm trong lịch sử trình duyệt và trong nhật ký của
 * mọi máy chủ trung gian, còn thân của một yêu cầu POST thì không.
 * <p>
 * Đặt mật khẩu xong <b>không</b> tự đăng nhập hộ. Bấm vào liên kết trong hộp thư mới chỉ chứng
 * minh người đó mở được hộp thư; bắt đăng nhập lại bằng mật khẩu vừa đặt là bước xác nhận rằng
 * họ thật sự biết mật khẩu đó, chứ không phải vừa vô tình mở một liên kết ai đó gửi tới.
 */
@WebServlet("/reset-password")
public class ResetPasswordServlet extends BaseServlet {

    private final PasswordResetService resetService = new PasswordResetService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String token = WebUtil.getString(req, "token");
        User owner = resetService.findAccountFor(token);
        if (owner == null) {
            WebUtil.flashError(req, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. "
                    + "Vui lòng yêu cầu một liên kết mới.");
            redirect(req, resp, "/forgot-password");
            return;
        }
        req.setAttribute("token", token);
        req.setAttribute("email", owner.getEmail());
        forward(req, resp, "auth/reset-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String token = WebUtil.getString(req, "token");
        try {
            resetService.complete(token,
                    req.getParameter("newPassword"),
                    req.getParameter("confirmPassword"),
                    WebUtil.clientIp(req));
        } catch (AppException e) {
            // Mật khẩu chưa đạt thì mã vẫn còn nguyên hiệu lực — dựng lại đúng biểu mẫu đó với
            // cùng mã, để người dùng sửa và gửi lại chứ không phải đi xin liên kết mới.
            User owner = resetService.findAccountFor(token);
            if (owner == null) {
                WebUtil.flashError(req, e.getMessage());
                redirect(req, resp, "/forgot-password");
                return;
            }
            req.setAttribute("errorMessage", e.getMessage());
            req.setAttribute("token", token);
            req.setAttribute("email", owner.getEmail());
            forward(req, resp, "auth/reset-password.jsp");
            return;
        }
        WebUtil.flashSuccess(req, "Đã đặt mật khẩu mới. Vui lòng đăng nhập lại.");
        redirect(req, resp, "/login");
    }
}
