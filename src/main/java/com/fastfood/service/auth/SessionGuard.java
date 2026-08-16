package com.fastfood.service.auth;

import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.model.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Giữ cho bản chụp người dùng trong phiên không lệch quá xa so với cơ sở dữ liệu.
 * <p>
 * <b>Vấn đề.</b> Đối tượng {@code User} được đặt vào phiên đúng một lần, lúc đăng nhập, rồi
 * mọi thứ dựa vào nó suốt 60 phút sau đó: {@code RoleAuthorizationFilter} đọc vai trò từ nó,
 * {@code AuthenticationFilter} đọc cờ buộc đổi mật khẩu từ nó, thanh điều hướng hiện menu theo
 * nó. Nghĩa là khoá một tài khoản không có tác dụng gì cho tới khi người đó tự đăng xuất — và
 * người vừa bị khoá thì chính là người sẽ không tự đăng xuất. Hạ quyền một tài khoản cũng vậy:
 * thu ngân vừa bị chuyển vai trò vẫn mở được màn hình quầy cho tới hết giờ phiên.
 * <p>
 * <b>Vì sao không soi lại ở mọi yêu cầu.</b> Màn hình bếp hỏi máy chủ vài giây một lần để cập
 * nhật hàng chờ; soi lại ở mọi yêu cầu biến việc đó thành một truy vấn nữa mỗi vài giây cho mỗi
 * người đang mở máy. Vì vậy soi lại theo nhịp — mặc định 30 giây — <i>và</i> soi lại ngay lập
 * tức ở mọi yêu cầu ghi dữ liệu. Cửa sổ chậm trễ chỉ áp dụng cho việc xem, còn thao tác thật sự
 * làm thay đổi dữ liệu thì luôn được kiểm bằng trạng thái mới nhất.
 */
public class SessionGuard {

    /** Thời điểm soi lại gần nhất, tính bằng mili giây, lưu ngay trong phiên. */
    private static final String CHECKED_AT = "accountCheckedAt";

    private static final int DEFAULT_INTERVAL_SECONDS = 30;

    private final AuthService authService = new AuthService();

    /**
     * Trả về người dùng của phiên, đã đồng bộ với cơ sở dữ liệu nếu tới nhịp soi lại.
     *
     * @return {@code null} khi tài khoản không còn dùng được — nơi gọi phải huỷ phiên và
     *         đẩy người dùng về trang đăng nhập
     */
    public User refresh(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        User inSession = WebUtil.currentUser(req);
        if (inSession == null) {
            return null;
        }
        if (!isDue(req, session)) {
            return inSession;
        }

        User fresh = authService.findById(inSession.getUserId());
        if (fresh == null || !fresh.isActive()) {
            // Tài khoản bị khoá — hoặc biến mất hẳn, chuyện không xảy ra vì hệ thống không xoá
            // tài khoản bao giờ, nhưng nếu có thì cũng phải dẫn tới cùng một kết cục.
            session.invalidate();
            return null;
        }
        WebUtil.putCurrentUser(session, fresh);
        session.setAttribute(CHECKED_AT, System.currentTimeMillis());
        return fresh;
    }

    /**
     * Tới nhịp soi lại chưa. Yêu cầu ghi dữ liệu thì luôn luôn tới nhịp: một thao tác đổi dữ
     * liệu thực hiện bằng quyền đã bị thu hồi ba mươi giây trước vẫn là một thao tác không được
     * phép, còn một lần xem trang thì không để lại hậu quả gì.
     */
    private boolean isDue(HttpServletRequest req, HttpSession session) {
        if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
            return true;
        }
        Object last = session.getAttribute(CHECKED_AT);
        if (!(last instanceof Long lastAt)) {
            return true;
        }
        long intervalMs = 1000L * AppConfig.getInt(
                "security.session.revalidateSeconds", DEFAULT_INTERVAL_SECONDS);
        return System.currentTimeMillis() - lastAt >= intervalMs;
    }
}
