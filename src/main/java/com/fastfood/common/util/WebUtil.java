package com.fastfood.common.util;

import com.fastfood.model.entity.UserEntities.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class WebUtil {

    public static final String SESSION_USER = "currentUser";
    public static final String FLASH_SUCCESS = "flashSuccess";
    public static final String FLASH_ERROR = "flashError";

    public static final String REDIRECT_AFTER_LOGIN = "redirectAfterLogin";

    private WebUtil() {
    }

    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (User) session.getAttribute(SESSION_USER);
    }

    public static void putCurrentUser(HttpSession session, User user) {
        user.setPasswordHash(null);
        session.setAttribute(SESSION_USER, user);
    }

    public static String startAuthenticatedSession(HttpServletRequest req, User user, String fallback) {
        HttpSession old = req.getSession(false);
        String savedTarget = null;
        if (old != null) {
            Object saved = old.getAttribute(REDIRECT_AFTER_LOGIN);
            savedTarget = saved instanceof String s ? s : null;
            old.invalidate();
        }
        HttpSession session = req.getSession(true);
        putCurrentUser(session, user);
        CsrfUtil.rotate(session);
        return safeRedirect(savedTarget, fallback);
    }

    public static int currentUserId(HttpServletRequest req) {
        User u = currentUser(req);
        return u == null ? 0 : u.getUserId();
    }

    public static int getInt(HttpServletRequest req, String name, int defaultValue) {
        String raw = req.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Integer getInteger(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getString(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        return raw == null ? null : raw.trim();
    }

    public static boolean getBoolean(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "on".equalsIgnoreCase(raw);
    }

    public static LocalDateTime getDateTime(HttpServletRequest req, String name) {
        return DateTimeUtil.parseHtmlInput(req.getParameter(name));
    }

    public static java.time.LocalDate getDate(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static void flashSuccess(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_SUCCESS, message);
    }

    public static void flashError(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_ERROR, message);
    }

    public static void consumeFlash(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return;
        }
        Object success = session.getAttribute(FLASH_SUCCESS);
        if (success != null) {
            req.setAttribute(FLASH_SUCCESS, success);
            session.removeAttribute(FLASH_SUCCESS);
        }
        Object error = session.getAttribute(FLASH_ERROR);
        if (error != null) {
            req.setAttribute(FLASH_ERROR, error);
            session.removeAttribute(FLASH_ERROR);
        }
    }

    public static String safeRedirect(String target, String fallback) {
        if (target == null || target.isBlank() || !target.startsWith("/")) {
            return fallback;
        }
        for (int i = 0; i < target.length(); i++) {
            if (target.charAt(i) < ' ' || target.charAt(i) == 127) {
                return fallback;
            }
        }
        if (target.startsWith("//") || target.startsWith("/\\")) {
            return fallback;
        }
        int queryStart = target.indexOf('?');
        String path = queryStart < 0 ? target : target.substring(0, queryStart);
        if (path.indexOf(':') >= 0 || path.indexOf('\\') >= 0) {
            return fallback;
        }
        return target;
    }

    public static String queryStringWithout(HttpServletRequest req, String... omit) {
        List<String> skip = Arrays.asList(omit);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> e : req.getParameterMap().entrySet()) {
            if (skip.contains(e.getKey())) {
                continue;
            }
            for (String value : e.getValue()) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                  .append('=')
                  .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    public static String pathWithFilters(String path, String filterQuery) {
        if (filterQuery == null || filterQuery.isBlank()) {
            return path;
        }
        return safeRedirect(path + "?" + filterQuery, path);
    }

    public static String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    public static String baseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        String portPart = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443))
                ? "" : ":" + port;
        return scheme + "://" + host + portPart + req.getContextPath();
    }
}
