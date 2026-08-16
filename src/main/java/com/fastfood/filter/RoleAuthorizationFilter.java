package com.fastfood.filter;

import com.fastfood.common.constant.RoleName;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.User;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Phân quyền theo vai trò dựa trên tiền tố địa chỉ.
 * <p>
 * Kiểm tra ở phía máy chủ chứ không chỉ ẩn nút trên giao diện: người dùng có thể gõ thẳng
 * địa chỉ vào trình duyệt. Ẩn nút là để giao diện gọn, không phải là biện pháp bảo vệ.
 * <p>
 * Quyền theo dữ liệu — như khách chỉ xem được đơn của chính mình — không kiểm tra được ở đây
 * vì phải đọc cơ sở dữ liệu, nên nằm trong tầng Service.
 * <p>
 * Khai báo và danh sách địa chỉ được bảo vệ nằm trong {@code WEB-INF/web.xml} — xem ghi chú ở đó.
 */
public class RoleAuthorizationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = RequestPath.of(req);
        boolean isApi = path.startsWith("/api/");

        User user = WebUtil.currentUser(req);
        if (user == null) {
            deny(req, resp, isApi, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        RoleName required = requiredRole(path);
        // Vai trò đọc từ cơ sở dữ liệu nên về nguyên tắc luôn nằm trong bốn giá trị đã biết,
        // nhưng nếu có ngày nó không nằm thì kết cục phải là "không có quyền", chứ không phải
        // một trang lỗi 500 — hỏng dữ liệu không được biến thành cửa mở.
        RoleName actual = RoleName.parse(user.getRoleName());

        // Quản trị viên xem được mọi màn hình vận hành để hỗ trợ và kiểm tra
        boolean allowed = actual != null && (actual == required || actual == RoleName.ADMIN);
        if (!allowed) {
            deny(req, resp, isApi, HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Địa chỉ trả dữ liệu JSON được từ chối bằng mã trạng thái, không phải bằng trang lỗi:
     * trang gọi bằng JavaScript nên nhận về HTML sẽ hỏng chỗ đọc kết quả.
     */
    private void deny(HttpServletRequest req, HttpServletResponse resp, boolean isApi, int status)
            throws IOException, ServletException {
        if (isApi) {
            resp.setStatus(status);
            return;
        }
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }
        req.setAttribute("errorMessage",
                "Tài khoản của bạn không có quyền truy cập khu vực này.");
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        req.getRequestDispatcher("/WEB-INF/views/error/403.jsp").forward(req, resp);
    }

    /**
     * Vai trò cần có để đi vào một địa chỉ.
     * <p>
     * Công khai và tĩnh để bài test đọc thẳng được bảng ánh xạ này. Nó là quyết định về quyền
     * chứ không phải chi tiết cài đặt, và cái sai ở đây — một tiền tố rơi vào nhánh mặc định —
     * không làm hỏng màn hình nào cả, nên chỉ có bài test mới phát hiện ra.
     * <p>
     * Nhánh mặc định trả về {@code ADMIN} chứ không phải "ai cũng được": địa chỉ lạ mà lọt vào
     * đây thì phải là cửa đóng, không phải cửa mở. Nhưng bộ lọc chỉ chạy trên những tiền tố khai
     * báo trong {@code web.xml}, nên một servlet đặc quyền đặt ngoài các tiền tố đó sẽ không đi
     * qua đây lần nào — đó là chỗ {@code RoutePolicyTest} canh giúp.
     */
    public static RoleName requiredRole(String path) {
        if (path != null && path.startsWith("/staff/")) {
            return RoleName.CASHIER;
        }
        // Hàng chờ bếp lộ ra qua hai đường: trang /kitchen/* và dữ liệu JSON /api/kds/*.
        // Thiếu nhánh thứ hai thì bất kỳ ai đăng nhập cũng đọc được toàn bộ việc của bếp.
        if (path != null && (path.startsWith("/kitchen/") || path.startsWith("/api/kds/"))) {
            return RoleName.KITCHEN;
        }
        return RoleName.ADMIN;
    }
}
