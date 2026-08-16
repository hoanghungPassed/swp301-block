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

/** Đăng ký tài khoản khách hàng. Tài khoản nhân viên do quản trị viên tạo. */
@WebServlet("/register")
public class RegisterServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        forward(req, resp, "auth/register.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            User user = authService.register(
                    WebUtil.getString(req, "fullName"),
                    WebUtil.getString(req, "email"),
                    WebUtil.getString(req, "phone"),
                    req.getParameter("password"),
                    req.getParameter("confirmPassword"),
                    WebUtil.baseUrl(req),
                    WebUtil.clientIp(req));

            // Đăng ký xong là đã đăng nhập, nên phải cấp phiên mới y hệt lúc đăng nhập. Dùng lại
            // phiên cũ ở đây thì việc chiếm phiên đã biết trước chỉ cần chuyển sang cửa này là
            // qua được — hàng rào dựng ở /login mà bỏ trống ở /register thì không phải là hàng rào.
            String target = WebUtil.startAuthenticatedSession(req, user, "/menu");
            // Nói ngay rằng có một lá thư đang chờ, và nói cả việc nó dùng để làm gì. Chỉ chúc
            // mừng rồi thôi thì dải nhắc "chưa xác thực email" ở trang sau hiện ra như một lỗi
            // vừa xảy ra, trong khi nó là bước tiếp theo hoàn toàn bình thường.
            WebUtil.flashSuccess(req, "Đăng ký thành công. Chào mừng " + user.getFullName()
                    + "! Chúng tôi vừa gửi một thư xác thực tới " + user.getEmail()
                    + " — mở thư và bấm liên kết trong đó để đặt được đơn online.");
            redirect(req, resp, target);
        } catch (AppException e) {
            req.setAttribute("errorMessage", e.getMessage());
            req.setAttribute("fullName", WebUtil.getString(req, "fullName"));
            req.setAttribute("email", WebUtil.getString(req, "email"));
            req.setAttribute("phone", WebUtil.getString(req, "phone"));
            forward(req, resp, "auth/register.jsp");
        }
    }
}
