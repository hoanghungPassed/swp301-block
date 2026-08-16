package com.fastfood.common.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Mã chống giả mạo yêu cầu (CSRF), gắn theo phiên.
 * <p>
 * <b>Vấn đề nó giải.</b> Trình duyệt tự đính kèm cookie phiên vào mọi yêu cầu gửi tới máy chủ
 * này, kể cả yêu cầu do một trang khác phát ra. Một trang bất kỳ chỉ cần chứa biểu mẫu tự gửi
 * tới {@code /admin/users} là đủ để khoá tài khoản dưới danh nghĩa người quản trị đang đăng nhập,
 * mà người đó chỉ thấy mình vừa mở một liên kết lạ. Cookie chứng minh <i>ai</i> gửi, không chứng
 * minh <i>người đó có định gửi hay không</i> — mã này lấp đúng khoảng trống ấy: nó chỉ có trong
 * trang do chính máy chủ dựng ra, nên trang ngoài không đọc được để đính kèm.
 * <p>
 * Mã gắn với phiên chứ không sinh mới cho từng biểu mẫu: mở nhiều thẻ cùng lúc là chuyện bình
 * thường ở màn hình quầy và màn hình bếp, mà mã dùng một lần thì thẻ mở trước sẽ hỏng khi thẻ mở
 * sau gửi đi. Đổi mã mỗi lần đăng nhập hay đăng xuất là đủ (xem {@link #rotate}).
 */
public final class CsrfUtil {

    /** Tên ô ẩn trong biểu mẫu, và tên khoá trong phiên. */
    public static final String PARAM = "_csrf";

    /** Tên biến mà trang JSP đọc: {@code <input type="hidden" name="_csrf" value="${csrfToken}">}. */
    public static final String REQUEST_ATTRIBUTE = "csrfToken";

    private static final String SESSION_KEY = "csrfToken";

    private CsrfUtil() {
    }

    /**
     * Mã của phiên hiện tại, tạo mới nếu chưa có.
     * <p>
     * Tạo phiên khi cần: trang đăng nhập cũng phải có mã, mà lúc đó chưa ai đăng nhập cả.
     * Không bảo vệ được biểu mẫu đăng nhập thì kẻ tấn công đăng nhập hộ nạn nhân bằng tài khoản
     * của chính mình, và mọi thứ nạn nhân làm sau đó — đặt món, lưu thẻ — rơi vào tài khoản đó.
     */
    public static String token(HttpServletRequest req) {
        HttpSession session = req.getSession(true);
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }
        String fresh = SecureToken.generate();
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }

    /**
     * Cấp mã mới cho phiên. Gọi ngay sau khi đăng nhập thành công: phiên cũ đã bị huỷ nên mã cũ
     * cũng phải bỏ theo, không mang sang phiên mới.
     */
    public static String rotate(HttpSession session) {
        String fresh = SecureToken.generate();
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }

    /** Mã gửi lên có khớp mã của phiên không. Không có phiên nghĩa là không khớp. */
    public static boolean isValid(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        return expected instanceof String s && SecureToken.matches(s, req.getParameter(PARAM));
    }
}
