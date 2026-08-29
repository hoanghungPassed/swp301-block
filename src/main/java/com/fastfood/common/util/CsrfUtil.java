package com.fastfood.common.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class CsrfUtil {

    public static final String PARAM = "_csrf";

    public static final String REQUEST_ATTRIBUTE = "csrfToken";

    private static final String SESSION_KEY = "csrfToken";

    private CsrfUtil() {
    }

    /** Lấy CSRF token từ session hoặc sinh mới khi session chưa có. */
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

    /** Thay CSRF token sau khi đổi trạng thái đăng nhập/session. */
    public static String rotate(HttpSession session) {
        String fresh = SecureToken.generate();
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }

    /** So token form/header với token session bằng phép so constant time. */
    public static boolean isValid(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        return expected instanceof String s && SecureToken.matches(s, req.getParameter(PARAM));
    }
}
