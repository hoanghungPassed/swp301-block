package com.fastfood.testsupport;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.User;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Yêu cầu, phản hồi và chuỗi bộ lọc giả — đủ để chạy một bộ lọc mà không cần máy chủ.
 * <p>
 * <b>Vì sao dựng tay thay vì thêm thư viện giả lập.</b> Dự án cố ý giữ số thư viện ngoài ở mức
 * tối thiểu, và cả bộ lọc phân quyền chỉ gọi tới bảy phương thức của Servlet API. Thêm một thư
 * viện nữa vào tệp {@code pom.xml} cho bảy phương thức là cái giá đắt hơn tệp này.
 * <p>
 * <b>Vì sao dùng lớp uỷ nhiệm động.</b> {@link HttpServletRequest} có hơn sáu mươi phương thức;
 * viết tay lớp cài đặt đầy đủ là hai trăm dòng thân rỗng, và người đọc phải lướt hết chúng để
 * tìm ra bảy dòng thật sự có nội dung. Phương thức chưa dựng thì <b>ném lỗi kèm đúng tên nó</b>
 * chứ không lặng lẽ trả {@code null}: bài test sau này cần thêm một phương thức sẽ đọc được
 * ngay phải bổ sung gì, thay vì đi tìm một giá trị rỗng không rõ từ đâu ra.
 */
public final class FakeHttp {

    private FakeHttp() {
    }

    public static Request request(String servletPath) {
        return new Request(servletPath, null);
    }

    /**
     * Yêu cầu tới servlet có ánh xạ dạng tiền tố, nơi máy chủ tách địa chỉ làm hai phần.
     * Dùng để kiểm rằng bộ lọc ghép lại đúng đường dẫn mà máy chủ sắp gọi tới.
     */
    public static Request request(String servletPath, String pathInfo) {
        return new Request(servletPath, pathInfo);
    }

    /**
     * Một phiên trình duyệt dùng lại được qua nhiều yêu cầu.
     * <p>
     * Cần thiết cho mọi thứ liên quan tới <i>thời gian</i>: nhịp soi lại tài khoản, mã chống giả
     * mạo giữ nguyên giữa hai lần tải trang, phiên bị huỷ khi tài khoản bị khoá. Mỗi yêu cầu một
     * phiên riêng thì những hành vi đó không có chỗ nào thể hiện ra.
     */
    public static Session session() {
        return new Session();
    }

    public static Response response() {
        return new Response();
    }

    public static Chain chain() {
        return new Chain();
    }

    // ------------------------------------------------------------------ phiên

    /** Trạng thái phiên, sống lâu hơn một yêu cầu — xem {@link FakeHttp#session()}. */
    public static final class Session {

        private Map<String, Object> attributes = new HashMap<>();
        private boolean alive;
        private int invalidations;

        public Session signedInAs(User user) {
            alive = true;
            attributes.put(WebUtil.SESSION_USER, user);
            return this;
        }

        public Object value(String name) {
            return attributes.get(name);
        }

        public User currentUser() {
            return (User) attributes.get(WebUtil.SESSION_USER);
        }

        /** Phiên còn sống không. Bị huỷ rồi thì người dùng phải đăng nhập lại. */
        public boolean alive() {
            return alive;
        }

        public int invalidations() {
            return invalidations;
        }
    }

    // ------------------------------------------------------------------ yêu cầu

    public static final class Request {

        private final String servletPath;
        private final String pathInfo;
        private final Map<String, Object> attributes = new HashMap<>();
        private final Map<String, String> parameters = new HashMap<>();
        private Session session = new Session();
        private String method = "GET";
        private String queryString;
        private String forwardedTo;

        private Request(String servletPath, String pathInfo) {
            this.servletPath = servletPath;
            this.pathInfo = pathInfo;
        }

        /** Gửi yêu cầu này bằng một phiên đã có, thay vì mở phiên mới. */
        public Request in(Session existing) {
            this.session = existing;
            return this;
        }

        public Request method(String httpMethod) {
            this.method = httpMethod;
            return this;
        }

        public Request param(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        public Request queryString(String value) {
            this.queryString = value;
            return this;
        }

        /** Gắn một người dùng vào phiên, đúng cách {@link WebUtil#currentUser} đọc ra. */
        public Request signedInAs(User user) {
            session.signedInAs(user);
            return this;
        }

        /** Người dùng có vai trò cho trước; các trường khác không ảnh hưởng tới phân quyền. */
        public Request signedInAs(String roleName) {
            User user = new User();
            user.setUserId(1);
            user.setFullName("Nguoi Dung Kiem Thu");
            user.setRoleName(roleName);
            user.setStatus("ACTIVE");
            return signedInAs(user);
        }

        /** Đặt sẵn một giá trị trong phiên, ví dụ mã chống giả mạo đã cấp từ lần tải trang trước. */
        public Request sessionAttribute(String name, Object value) {
            session.alive = true;
            session.attributes.put(name, value);
            return this;
        }

        public HttpServletRequest build() {
            return proxy(HttpServletRequest.class, (m, args) -> switch (m) {
                case "getServletPath" -> servletPath;
                case "getPathInfo" -> pathInfo;
                case "getContextPath" -> "";
                case "getMethod" -> method;
                case "getQueryString" -> queryString;
                case "getParameter" -> parameters.get((String) args[0]);
                case "getRemoteAddr" -> "10.0.0.9";
                case "getRequestURI" -> servletPath + (pathInfo == null ? "" : pathInfo);
                case "getSession" -> session(args.length == 0 || (boolean) args[0]);
                case "setAttribute" -> {
                    attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "getAttribute" -> attributes.get((String) args[0]);
                case "getRequestDispatcher" -> dispatcher((String) args[0]);
                default -> UNHANDLED;
            });
        }

        private HttpSession session(boolean create) {
            if (!session.alive && !create) {
                return null;
            }
            session.alive = true;
            return proxy(HttpSession.class, (m, args) -> switch (m) {
                case "getAttribute" -> session.attributes.get((String) args[0]);
                case "setAttribute" -> {
                    session.attributes.put((String) args[0], args[1]);
                    yield null;
                }
                case "removeAttribute" -> {
                    session.attributes.remove((String) args[0]);
                    yield null;
                }
                // Huỷ phiên theo đúng nghĩa của máy chủ thật: dữ liệu cũ mất hẳn, và lần xin
                // phiên tiếp theo nhận một phiên trắng. Giữ lại bản đồ cũ thì bài test về xoay
                // phiên lúc đăng nhập sẽ xanh trong khi mã phiên thật ra không hề đổi.
                case "invalidate" -> {
                    session.attributes = new HashMap<>();
                    session.alive = false;
                    session.invalidations++;
                    yield null;
                }
                default -> UNHANDLED;
            });
        }

        private RequestDispatcher dispatcher(String path) {
            return proxy(RequestDispatcher.class, (m, args) -> {
                if ("forward".equals(m)) {
                    forwardedTo = path;
                    return null;
                }
                return UNHANDLED;
            });
        }

        /** Trang JSP mà bộ lọc đã chuyển tiếp sang, hoặc null nếu không chuyển tiếp đi đâu. */
        public String forwardedTo() {
            return forwardedTo;
        }

        public Object attribute(String name) {
            return attributes.get(name);
        }

        /** Giá trị còn lại trong phiên sau khi bộ lọc chạy xong. */
        public Object sessionValue(String name) {
            return session.attributes.get(name);
        }

        /** Số lần phiên bị huỷ — dùng để chứng minh việc xoay phiên lúc đăng nhập có thật xảy ra. */
        public int invalidations() {
            return session.invalidations;
        }
    }

    // ------------------------------------------------------------------ phản hồi

    public static final class Response {

        private int status = HttpServletResponse.SC_OK;
        private String redirectedTo;

        public HttpServletResponse build() {
            return proxy(HttpServletResponse.class, (method, args) -> switch (method) {
                case "setStatus" -> {
                    status = (int) args[0];
                    yield null;
                }
                case "getStatus" -> status;
                case "sendRedirect" -> {
                    redirectedTo = (String) args[0];
                    yield null;
                }
                case "setContentType", "setCharacterEncoding" -> null;
                default -> UNHANDLED;
            });
        }

        public int status() {
            return status;
        }

        /** Địa chỉ đã chuyển hướng tới, hoặc null nếu không chuyển hướng. */
        public String redirectedTo() {
            return redirectedTo;
        }
    }

    // ------------------------------------------------------------------ chuỗi bộ lọc

    /**
     * Mắt xích tiếp theo của chuỗi. Điều đáng kiểm nhất ở một bộ lọc phân quyền là nó có
     * <b>đi tiếp hay không</b>: chặn mà vẫn gọi tiếp thì servlet vẫn chạy và dữ liệu vẫn ra,
     * chỉ khác là kèm theo một mã lỗi không ai đọc.
     */
    public static final class Chain {

        private boolean ran;

        public FilterChain build() {
            return proxy(FilterChain.class, (method, args) -> {
                if ("doFilter".equals(method)) {
                    ran = true;
                    return null;
                }
                return UNHANDLED;
            });
        }

        public boolean ran() {
            return ran;
        }
    }

    // ------------------------------------------------------------------ bộ khung

    /** Dấu hiệu "phương thức này chưa dựng" — xem ghi chú ở đầu lớp. */
    private static final Object UNHANDLED = new Object();

    @FunctionalInterface
    private interface Behaviour {
        Object invoke(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Behaviour behaviour) {
        InvocationHandler handler = (proxy, method, args) -> {
            Object[] safeArgs = args == null ? new Object[0] : args;
            switch (method.getName()) {
                case "toString": return "Fake" + type.getSimpleName();
                case "hashCode": return System.identityHashCode(proxy);
                case "equals":   return proxy == safeArgs[0];
                default: break;
            }
            Object result = behaviour.invoke(method.getName(), safeArgs);
            if (result == UNHANDLED) {
                throw new UnsupportedOperationException(
                        "FakeHttp chua dung phuong thuc " + type.getSimpleName() + "." + method.getName()
                                + " — them vao FakeHttp neu bai test can toi no");
            }
            return result;
        };
        return (T) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
