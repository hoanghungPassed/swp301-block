package com.fastfood.filter;

import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.User;
import com.fastfood.service.auth.SessionGuard;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * Chặn các trang cần đăng nhập.
 * <p>
 * Danh sách trang công khai được liệt kê tường minh, còn lại mặc định là phải đăng nhập.
 * Cách này an toàn hơn liệt kê trang cần bảo vệ: thêm màn hình mới mà quên khai báo thì
 * hậu quả là bắt đăng nhập thừa, chứ không phải để lộ dữ liệu.
 * <p>
 * Khai báo và thứ tự nằm trong {@code WEB-INF/web.xml} — xem ghi chú ở đó.
 */
public class AuthenticationFilter implements Filter {

    /** Trang ai cũng xem được: thực đơn, đăng nhập, đăng ký, tài nguyên tĩnh. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/", "/index.jsp", "/menu", "/product/detail", "/login", "/logout", "/register",
            // Quên mật khẩu: chỉ người đang KHÔNG vào được tài khoản mới cần tới, nên bắt buộc
            // phải mở cho người chưa đăng nhập.
            "/forgot-password", "/reset-password",
            // Xác thực email: người dùng bấm liên kết từ hộp thư, rất thường là ở máy khác hoặc
            // trên điện thoại — nơi không có phiên đăng nhập nào. Bắt đăng nhập trước ở đây thì
            // việc xác thực bị chặn đúng bởi rào cản mà nó sinh ra để gỡ.
            "/verify-email",
            // Cổng thanh toán gọi vào từ máy chủ của họ, không có phiên đăng nhập nào để mang
            // theo. Bù lại, cả hai địa chỉ đều phải tự chứng minh: chữ ký với cổng chuyển hướng,
            // khoá API ở header với SePay — và không địa chỉ nào đọc dữ liệu của người dùng ra.
            "/payment/callback",
            "/payment/sepay/webhook"
    );

    private static final Set<String> PUBLIC_PREFIXES = Set.of("/assets/");

    /**
     * Nơi duy nhất một tài khoản đang bị buộc đổi mật khẩu còn đi tới được.
     * Phải có {@code /logout}, nếu không thì người dùng bị kẹt hẳn: không đổi được mật khẩu
     * mà cũng không thoát ra để đăng nhập bằng tài khoản khác.
     */
    private static final Set<String> PASSWORD_CHANGE_PATHS = Set.of("/profile", "/logout");

    private final SessionGuard sessionGuard = new SessionGuard();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = RequestPath.of(req);

        if (isPublicPath(path)) {
            // Trang công khai vẫn phải đồng bộ: thanh điều hướng trên /menu đọc vai trò từ
            // phiên, và một tài khoản vừa bị khoá không được tiếp tục hiện ra như đang đăng nhập.
            refreshIfLoggedIn(req);
            chain.doFilter(request, response);
            return;
        }

        User user = WebUtil.currentUser(req);
        if (user == null) {
            // Nhớ trang khách định vào để đăng nhập xong quay lại đúng chỗ đó
            String target = req.getQueryString() == null ? path : path + "?" + req.getQueryString();
            req.getSession().setAttribute(WebUtil.REDIRECT_AFTER_LOGIN, target);
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // Quyền trong phiên là bản chụp tại thời điểm đăng nhập, mà phiên sống tới 60 phút.
        // Không soi lại thì việc khoá một tài khoản chỉ có hiệu lực sau khi người đó tự đăng
        // xuất — đúng điều họ sẽ không làm. Xem SessionGuard để biết vì sao không soi mọi lần.
        user = sessionGuard.refresh(req);
        if (user == null) {
            WebUtil.flashError(req, "Tài khoản của bạn vừa bị khoá hoặc không còn hiệu lực. "
                    + "Vui lòng đăng nhập lại.");
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // Mật khẩu do quản trị viên đặt hộ là mật khẩu ít nhất hai người biết. Giữ tài khoản
        // ở trang tài khoản cho tới khi chủ tài khoản tự đặt lại — chặn ở đây chứ không ở
        // từng màn hình, vì chỉ cần sót một màn hình là rào chắn mất tác dụng.
        if (user.isMustChangePassword() && !PASSWORD_CHANGE_PATHS.contains(path)) {
            WebUtil.flashError(req, "Mật khẩu của bạn vừa được quản trị viên đặt lại. "
                    + "Vui lòng đặt mật khẩu mới trước khi tiếp tục.");
            resp.sendRedirect(req.getContextPath() + "/profile");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Soi lại tài khoản trên trang công khai, nhưng chỉ khi đang có người đăng nhập.
     * Khách vãng lai xem thực đơn thì không đụng tới cơ sở dữ liệu — đó là trang được mở
     * nhiều nhất và phần lớn lượt mở không có ai đăng nhập cả.
     */
    private void refreshIfLoggedIn(HttpServletRequest req) {
        if (WebUtil.currentUser(req) != null) {
            // Huỷ phiên là việc của SessionGuard; ở đây chỉ cần không đi tiếp bằng bản cũ.
            sessionGuard.refresh(req);
        }
    }

    /**
     * Địa chỉ này có mở cho người chưa đăng nhập không.
     * <p>
     * Công khai và tĩnh để kiểm chứng được từ bài test: danh sách trang mở là một <b>quyết định
     * về quyền</b>, và thêm nhầm một dòng vào đó thì không có gì hỏng ầm ĩ — màn hình vẫn chạy,
     * chỉ là ai cũng vào được. Loại lỗi im lặng như vậy phải có bài test canh, mà bài test thì
     * không đọc được một phương thức riêng tư.
     */
    public static boolean isPublicPath(String path) {
        if (path == null) {
            return false;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
