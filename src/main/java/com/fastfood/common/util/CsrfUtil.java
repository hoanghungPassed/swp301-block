package com.fastfood.common.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class CsrfUtil {

    public static final String PARAM = "_csrf";

    public static final String REQUEST_ATTRIBUTE = "csrfToken";

    private static final String SESSION_KEY = "csrfToken";

    private CsrfUtil() {
    }

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

    public static String rotate(HttpSession session) {
        String fresh = SecureToken.generate();
        session.setAttribute(SESSION_KEY, fresh);
        return fresh;
    }

    public static boolean isValid(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        return expected instanceof String s && SecureToken.matches(s, req.getParameter(PARAM));
    }
}
