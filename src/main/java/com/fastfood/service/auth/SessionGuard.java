package com.fastfood.service.auth;

import com.fastfood.common.util.WebUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.model.entity.UserEntities.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class SessionGuard {

    private static final String CHECKED_AT = "accountCheckedAt";

    private static final int DEFAULT_INTERVAL_SECONDS = 30;

    private final AuthService authService = new AuthService();

    /**
     * Định kỳ tải lại user từ database; tài khoản bị khóa/không tồn tại sẽ bị hủy session, còn
     * tài khoản hợp lệ được cập nhật thông tin mới vào session.
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
            session.invalidate();
            return null;
        }
        WebUtil.putCurrentUser(session, fresh);
        session.setAttribute(CHECKED_AT, System.currentTimeMillis());
        return fresh;
    }

    /** Kiểm tra đã đến thời điểm cần đối chiếu lại trạng thái tài khoản hay chưa. */
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
