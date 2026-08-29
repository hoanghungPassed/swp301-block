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

    /** Lấy User đang đăng nhập từ session mà không tự tạo session mới. */
    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (User) session.getAttribute(SESSION_USER);
    }

    /** Ghi hoặc xóa User hiện tại trong session. */
    public static void putCurrentUser(HttpSession session, User user) {
        user.setPasswordHash(null);
        session.setAttribute(SESSION_USER, user);
    }

    /** Tạo session mới sau login và cho phép quay lại URL đã yêu cầu trước đó. */
    public static String startAuthenticatedSession(HttpServletRequest req, User user, String fallback) {
        return startSession(req, user, fallback, true);
    }

    /*
      Dành cho đăng ký: mở phiên nhưng KHÔNG dùng địa chỉ đã lưu trước đó, luôn đi tới target.

      Địa chỉ đã lưu là nơi khách bị chặn lúc chưa đăng nhập. Với đăng nhập thì đưa họ trở lại
      đó là đúng. Với đăng ký thì không: tài khoản vừa tạo chắc chắn là CUSTOMER, nên nếu chỗ
      bị chặn là khu vực nhân viên (gõ thẳng /admin/... rồi bấm sang trang đăng ký), đưa họ về
      đó chỉ đổi một trang chặn này lấy một trang 403 khác — trong khi việc họ cần làm là xem
      thực đơn.
    */
    /** Tạo session sau đăng ký nhưng luôn chuyển tới target an toàn đã chỉ định. */
    public static String startRegisteredSession(HttpServletRequest req, User user, String target) {
        return startSession(req, user, target, false);
    }

    /** Hủy session cũ, tạo session mới, lưu user và rotate CSRF để chống session fixation. */
    private static String startSession(HttpServletRequest req, User user, String fallback,
                                       boolean honorSavedTarget) {
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
        return honorSavedTarget ? safeRedirect(savedTarget, fallback) : fallback;
    }

    /** Trả userId trong session hoặc 0 nếu chưa đăng nhập. */
    public static int currentUserId(HttpServletRequest req) {
        User u = currentUser(req);
        return u == null ? 0 : u.getUserId();
    }

    /** Đọc parameter số nguyên, dùng defaultValue khi thiếu hoặc sai định dạng. */
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

    /** Đọc parameter Integer nullable. */
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

    /** Đọc parameter chuỗi và trim; chuỗi thiếu vẫn trả null. */
    public static String getString(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        return raw == null ? null : raw.trim();
    }

    /** Đọc parameter boolean theo các giá trị HTML thường dùng. */
    public static boolean getBoolean(HttpServletRequest req, String name) {
        String raw = req.getParameter(name);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "on".equalsIgnoreCase(raw);
    }

    /** Parse parameter datetime-local thành LocalDateTime hoặc null. */
    public static LocalDateTime getDateTime(HttpServletRequest req, String name) {
        return DateTimeUtil.parseHtmlInput(req.getParameter(name));
    }

    /** Parse parameter ngày ISO thành LocalDate hoặc null. */
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

    /** Lưu thông báo thành công một lần trong session để hiện sau redirect. */
    public static void flashSuccess(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_SUCCESS, message);
    }

    /** Lưu thông báo lỗi một lần trong session để hiện sau redirect. */
    public static void flashError(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_ERROR, message);
    }

    /** Chuyển flash message từ session sang request rồi xóa khỏi session. */
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

    /** Chỉ cho redirect nội bộ, loại bỏ URL ngoài hệ thống để chống open redirect. */
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

    /** Dựng lại query string hiện tại nhưng bỏ các parameter được chỉ định. */
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

    /** Ghép đường dẫn với query filter, xử lý đúng trường hợp query rỗng. */
    public static String pathWithFilters(String path, String filterQuery) {
        if (filterQuery == null || filterQuery.isBlank()) {
            return path;
        }
        return safeRedirect(path + "?" + filterQuery, path);
    }

    /** Lấy IP client đã chuẩn hóa từ request để audit và rate limit. */
    public static String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    /** Tạo URL gốc scheme-host-port-context dùng trong link email và callback. */
    public static String baseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        String portPart = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443))
                ? "" : ":" + port;
        return scheme + "://" + host + portPart + req.getContextPath();
    }
}
