package com.fastfood.controller.auth;

import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.controller.BaseServlet;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.EmailVerificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

/**
 * Xác thực địa chỉ email: mở liên kết trong thư (GET), và xin gửi lại thư (POST).
 * <p>
 * Trang này <b>không</b> đăng nhập hộ người bấm liên kết, kể cả khi mã hoàn toàn hợp lệ. Mở
 * được lá thư mới chỉ chứng minh quyền với hộp thư, chưa chứng minh biết mật khẩu — mà liên kết
 * thì nằm trong hộp thư suốt hai mươi tư giờ và đi qua bao nhiêu máy chủ trung gian. Cùng lý do
 * với {@link ResetPasswordServlet}.
 * <p>
 * Nhưng nếu người bấm <i>đang</i> đăng nhập sẵn ở chính trình duyệt đó — trường hợp thường gặp
 * nhất, vì họ vừa đăng ký xong ở tab bên cạnh — thì bản chụp người dùng trong phiên được làm
 * mới ngay. Không làm thì dải nhắc "chưa xác thực email" còn nằm nguyên trên đầu trang cho tới
 * nhịp soi lại tiếp theo, và việc vừa làm trông như không ăn thua gì.
 */
@WebServlet("/verify-email")
public class VerifyEmailServlet extends BaseServlet {

    private final EmailVerificationService verificationService = new EmailVerificationService();

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
            // Đã xác thực từ trước cũng là một kết cục tốt, nên nói bằng giọng của một việc đã
            // xong chứ không phải giọng của một lỗi. Người dùng ở đây thường chỉ bấm lại lá thư cũ.
            case ALREADY_VERIFIED -> WebUtil.flashSuccess(req,
                    "Địa chỉ email này đã được xác thực rồi. Bạn không cần làm gì thêm.");
            // Chỉ ra đường đi tiếp: nút gửi lại nằm ngay trên dải nhắc sau khi đăng nhập. Báo
            // liên kết hỏng rồi thôi thì người dùng không có cách nào biết phải làm gì.
            case INVALID -> WebUtil.flashError(req,
                    "Liên kết xác thực không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập rồi bấm "
                    + "\"Gửi lại thư xác thực\" để nhận liên kết mới.");
        }
        redirect(req, resp, WebUtil.currentUser(req) == null ? "/login" : "/menu");
    }

    /** Người bấm liên kết đang đăng nhập bằng chính tài khoản vừa xác thực. */
    private void refreshSessionIfSamePerson(HttpServletRequest req, String token) {
        User inSession = WebUtil.currentUser(req);
        if (inSession == null) {
            return;
        }
        User owner = verificationService.findAccountFor(token);
        if (owner == null || owner.getUserId() != inSession.getUserId()) {
            // Người đang đăng nhập là một tài khoản khác — họ vừa bấm liên kết của người thân
            // trên cùng máy chẳng hạn. Việc xác thực vẫn có hiệu lực với tài khoản kia, nhưng
            // phiên ở đây thì không được đụng tới.
            return;
        }
        HttpSession session = req.getSession(false);
        if (session != null) {
            WebUtil.putCurrentUser(session, owner);
        }
    }

    /**
     * Gửi lại thư xác thực cho người đang đăng nhập.
     * <p>
     * Chỉ nhận POST, và chỉ từ người đã đăng nhập. Để ở GET thì một thẻ {@code <img>} trên trang
     * bất kỳ cũng bắt hệ thống gửi thư được — biến chính chức năng này thành công cụ dội thư,
     * đúng thứ mà trần số lần xin trong {@code EmailVerificationService} đang chặn.
     */
    @Override
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
