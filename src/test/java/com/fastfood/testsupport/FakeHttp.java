package com.fastfood.testsupport;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.UserEntities.User;

import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class FakeHttp {

    private FakeHttp() {
    }

    public static Request request(String servletPath) {
        return new Request(servletPath, null);
    }

    public static Request request(String servletPath, String pathInfo) {
        return new Request(servletPath, pathInfo);
    }

    public static Session session() {
        return new Session();
    }

    public static Response response() {
        return new Response();
    }

    public static Chain chain() {
        return new Chain();
    }

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

        public boolean alive() {
            return alive;
        }

        public int invalidations() {
            return invalidations;
        }
    }

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

        public Request signedInAs(User user) {
            session.signedInAs(user);
            return this;
        }

        public Request signedInAs(String roleName) {
            User user = new User();
            user.setUserId(1);
            user.setFullName("Nguoi Dung Kiem Thu");
            user.setRoleName(roleName);
            user.setStatus("ACTIVE");
            return signedInAs(user);
        }

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

        public String forwardedTo() {
            return forwardedTo;
        }

        public Object attribute(String name) {
            return attributes.get(name);
        }

        public Object sessionValue(String name) {
            return session.attributes.get(name);
        }

        public int invalidations() {
            return session.invalidations;
        }
    }

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

        public String redirectedTo() {
            return redirectedTo;
        }
    }

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
