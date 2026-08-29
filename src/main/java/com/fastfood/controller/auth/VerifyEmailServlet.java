package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.auth.EmailVerificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet("/verify-email")
public class VerifyEmailServlet extends BaseServlet {

    private final EmailVerificationService verificationService = new EmailVerificationService();

    /** Xác nhận token trong liên kết email và làm mới trạng thái xác thực trong session hiện tại. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String token = WebUtil.getString(req, "token");
        EmailVerificationService.Result result =
                verificationService.confirm(token, WebUtil.clientIp(req));

        switch (result) {
            case VERIFIED -> {
                refreshSessionIfSamePerson(req, token);
                WebUtil.flashSuccess(req, "Đã xác thực địa chỉ email. Bạn có thể đặt đơn online ngay bây giờ.");
            }
            case ALREADY_VERIFIED -> WebUtil.flashSuccess(req,
                    "Địa chỉ email này đã được xác thực rồi. Bạn không cần làm gì thêm.");
            case INVALID -> WebUtil.flashError(req,
                    "Liên kết xác thực không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập rồi bấm "
                    + "\"Gửi lại thư xác thực\" để nhận liên kết mới.");
        }
        redirect(req, resp, WebUtil.currentUser(req) == null ? "/login" : "/menu");
    }

    /** Chỉ cập nhật user trong session nếu token vừa xác nhận thuộc đúng tài khoản đang mở. */
    private void refreshSessionIfSamePerson(HttpServletRequest req, String token) {
        User inSession = WebUtil.currentUser(req);
        if (inSession == null) {
            return;
        }
        User owner = verificationService.findAccountFor(token);
        if (owner == null || owner.getUserId() != inSession.getUserId()) {
            return;
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            WebUtil.putCurrentUser(session, owner);
        }
    }

    @Override
    /** Gửi lại thư xác thực cho chính tài khoản đang đăng nhập khi token cũ mất hoặc hết hạn. */
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = requireUser(req);
        String back = WebUtil.safeRedirect(WebUtil.getString(req, "returnTo"), "/menu");
        try {
            verificationService.resend(user, WebUtil.baseUrl(req), WebUtil.clientIp(req));
            WebUtil.flashSuccess(req, "Đã gửi lại thư xác thực tới " + user.getEmail()
                    + ". Thư có thể nằm trong mục Thư rác.");
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
        }
        redirect(req, resp, back);
    }
}
