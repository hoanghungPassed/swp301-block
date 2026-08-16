package com.fastfood.common.util;

import com.fastfood.model.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Thao tác thường dùng với request và session. */
public final class WebUtil {

    public static final String SESSION_USER = "currentUser";
    public static final String FLASH_SUCCESS = "flashSuccess";
    public static final String FLASH_ERROR = "flashError";

    /**
     * Trang người dùng định vào trước khi bị chặn lại ở cửa đăng nhập.
     * <p>
     * Là hằng số chứ không phải chuỗi gõ lại ở từng nơi: giá trị này được <i>ghi</i> ở hai chỗ
     * ({@code AuthenticationFilter} và {@code BaseServlet.userOrLogin}) rồi mới được <i>đọc</i>
     * ở một chỗ khác lúc đăng nhập xong. Gõ lệch một ký tự thì không có gì hỏng ầm ĩ cả — người
     * dùng chỉ lặng lẽ rơi về trang chủ thay vì quay lại đúng chỗ đang dở.
     */
    public static final String REDIRECT_AFTER_LOGIN = "redirectAfterLogin";

    private WebUtil() {
    }

    public static User currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (User) session.getAttribute(SESSION_USER);
    }

    /**
     * Đưa người dùng vào phiên sau khi bỏ đi thứ không được phép nằm ở đó.
     * <p>
     * Trang JSP đọc được mọi thuộc tính của đối tượng trong phiên qua EL, nên một dòng
     * {@code ${me.passwordHash}} gõ nhầm ở bất kỳ trang nào là đủ để in bản băm mật khẩu ra
     * HTML. Bản băm không có việc gì phải đi theo người dùng qua từng trang — nó chỉ cần thiết
     * đúng một lần, lúc đối chiếu mật khẩu — nên bỏ lại ngay tại cửa.
     */
    public static void putCurrentUser(HttpSession session, User user) {
        user.setPasswordHash(null);
        session.setAttribute(SESSION_USER, user);
    }

    /**
     * Cấp phiên mới cho người vừa xác thực xong, và trả lại trang họ định vào trước đó.
     * <p>
     * Phải là phiên <b>mới</b>, không phải phiên cũ gắn thêm người dùng vào. Mã phiên trước khi
     * đăng nhập là thứ kẻ tấn công đặt được cho nạn nhân — gửi một liên kết có sẵn mã phiên, đợi
     * nạn nhân đăng nhập, rồi dùng chính mã đó để vào tài khoản của họ. Đổi mã ngay tại thời
     * điểm đăng nhập thì mã kẻ tấn công biết trở thành vô giá trị.
     * <p>
     * Mã chống giả mạo biểu mẫu cũng phải cấp lại theo, vì nó gắn với phiên vừa bị huỷ.
     *
     * @return đường dẫn nội bộ đã ghi nhớ trước khi đăng nhập, hoặc {@code fallback}
     */
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

    /**
     * Đọc ô {@code <input type="date">}, định dạng {@code yyyy-MM-dd}.
     * <p>
     * Giá trị hỏng trả về null chứ không ném lỗi, cùng quy ước với {@link #getDateTime}: tầng
     * dịch vụ sẽ nói ra bằng tiếng Việt là thiếu gì, thay vì để một lỗi phân tích chuỗi lọt lên
     * thành thông báo "có lỗi xảy ra".
     */
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

    /**
     * Thông báo hiển thị một lần rồi mất, đặt vào session để còn sống qua một lần chuyển hướng.
     * Dùng chuyển hướng thay vì forward sau khi ghi dữ liệu, để khách tải lại trang
     * không gửi lại biểu mẫu lần nữa.
     */
    public static void flashSuccess(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_SUCCESS, message);
    }

    public static void flashError(HttpServletRequest req, String message) {
        req.getSession().setAttribute(FLASH_ERROR, message);
    }

    /** Chuyển thông báo từ session sang request rồi xoá khỏi session. */
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

    /**
     * Lọc địa chỉ quay về do người dùng gửi lên.
     * <p>
     * Chỉ chấp nhận đường dẫn nội bộ. Kẻ tấn công gửi đi một liên kết đăng nhập của chính cửa
     * hàng, kèm địa chỉ quay về trỏ ra ngoài; khách đăng nhập xong bị đẩy sang trang giả mạo mà
     * vẫn tin là mình đang ở đúng nơi, vì trang họ vừa gõ mật khẩu đúng là trang thật.
     * <p>
     * Ba dạng trá hình bị chặn, cả ba đều <i>trông như</i> đường dẫn nội bộ:
     * <ul>
     *   <li>{@code //evil.com/x} — trình duyệt hiểu là địa chỉ tuyệt đối, giữ nguyên giao thức
     *       hiện tại. Không có tên giao thức nào trong chuỗi cả.</li>
     *   <li>{@code /\evil.com/x} — dấu chéo ngược, trình duyệt quy về dạng trên.</li>
     *   <li>{@code /javascript:alert(1)} và họ hàng — tên giao thức nấp trong phần đường dẫn.</li>
     * </ul>
     * Dấu hai chấm chỉ bị cấm ở <b>phần đường dẫn</b>, không cấm ở phần sau dấu hỏi. Cấm cả
     * chuỗi thì những địa chỉ hoàn toàn bình thường như {@code /staff/orders?from=2026-08-15T07:30}
     * bị vứt đi, và người dùng lặng lẽ rơi về trang chủ sau khi đăng nhập.
     * <p>
     * Không hợp lệ thì trả về {@code fallback} chứ không đi tiếp.
     */
    public static String safeRedirect(String target, String fallback) {
        if (target == null || target.isBlank() || !target.startsWith("/")) {
            return fallback;
        }
        // Ký tự điều khiển — xuống dòng, tab — dùng để chẻ đôi phần đầu của phản hồi HTTP.
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

    /**
     * Chuỗi tham số hiện tại sau khi bỏ đi những tham số được nêu tên, đã mã hoá sẵn.
     * <p>
     * Dùng cho liên kết chuyển trang: bấm sang trang 2 phải giữ nguyên bộ lọc đang áp dụng,
     * nếu không thì mỗi lần chuyển trang lại nhảy về xem toàn bộ dữ liệu. Bỏ tham số
     * {@code page} ra để thanh chuyển trang tự gắn số trang của nó vào.
     * <p>
     * Trả về chuỗi rỗng khi không còn tham số nào, để nơi gọi tự quyết định có cần dấu
     * {@code &} nối tiếp hay không.
     */
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

    /**
     * Ghép bộ lọc đang áp dụng vào sau một đường dẫn nội bộ.
     * <p>
     * Dùng cho các thao tác ghi trên màn hình danh sách: khoá một tài khoản ở trang 3 của danh
     * sách đang lọc theo vai trò Bếp mà xong việc lại rơi về trang đầu không lọc thì người dùng
     * phải gõ lại bộ lọc sau <i>mỗi</i> lần bấm — và với một danh sách dài, họ mất luôn chỗ đang đứng.
     * <p>
     * Chuỗi lọc đi qua biểu mẫu nên coi như do người dùng gửi lên: đẩy qua {@link #safeRedirect}
     * để một chuỗi bịa đặt chỉ làm mất bộ lọc, chứ không chẻ được phần đầu của phản hồi HTTP.
     */
    public static String pathWithFilters(String path, String filterQuery) {
        if (filterQuery == null || filterQuery.isBlank()) {
            return path;
        }
        return safeRedirect(path + "?" + filterQuery, path);
    }

    /**
     * Địa chỉ máy khách, dùng làm khoá đếm số lần thử sai.
     * <p>
     * Cố tình <b>không</b> đọc {@code X-Forwarded-For}. Tiêu đề đó do máy khách tự khai, nên khi
     * không có máy chủ trung gian nào ghi đè — đúng như cách dự án này chạy — thì tin nó chính là
     * để người dò mật khẩu tự đặt một địa chỉ khác cho mỗi lần thử, và cơ chế đếm số lần thử sai
     * mất tác dụng hoàn toàn trong khi vẫn trông như đang chạy. Nếu sau này đặt sau proxy thì
     * phải sửa ở đây, và chỉ tin tiêu đề đó khi yêu cầu đến từ đúng proxy đã biết.
     */
    public static String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    /** Địa chỉ đầy đủ của ứng dụng, dùng dựng địa chỉ quay về cho cổng thanh toán. */
    public static String baseUrl(HttpServletRequest req) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        String portPart = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443))
                ? "" : ":" + port;
        return scheme + "://" + host + portPart + req.getContextPath();
    }
}
