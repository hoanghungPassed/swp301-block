package com.fastfood.controller;

import com.fastfood.common.constant.RoleName;
import com.fastfood.common.exception.AppException;
import com.fastfood.common.util.WebUtil;
import com.fastfood.model.entity.User;
import com.fastfood.service.shared.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lớp cha cho mọi servlet.
 * <p>
 * Gom hai việc lặp lại ở khắp nơi: chuyển tiếp sang trang JSP, và biến ngoại lệ thành
 * thông báo cho người dùng. Lỗi nghiệp vụ hiện thành thông báo tiếng Việt ngay trên trang;
 * lỗi ngoài dự kiến được ghi log đầy đủ nhưng chỉ hiện thông báo chung, để không lộ
 * cấu trúc hệ thống ra ngoài.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    /** Dùng cho huy hiệu tin chưa đọc — xem {@link #attachUnreadNotifications}. */
    private final NotificationService notificationService = new NotificationService();

    protected void forward(HttpServletRequest req, HttpServletResponse resp, String view)
            throws ServletException, IOException {
        WebUtil.consumeFlash(req);
        attachUnreadNotifications(req);
        attachCurrentPath(req);
        req.getRequestDispatcher("/WEB-INF/views/" + view).forward(req, resp);
    }

    /**
     * Đường dẫn của chính trang đang dựng, để biểu mẫu trong khung dùng chung biết chỗ quay về.
     * <p>
     * Phải lấy ở đây, <b>trước</b> khi chuyển tiếp: sau khi chuyển tiếp thì
     * {@code getServletPath()} trả về đường dẫn của tệp JSP ({@code /WEB-INF/views/...}) chứ
     * không còn là địa chỉ người dùng đang mở, nên trang JSP tự hỏi lấy sẽ ra một chỗ không
     * quay về được.
     * <p>
     * Dùng ở dải nhắc "chưa xác thực email" trong {@code layout/page-start.jspf}: dải đó có mặt
     * trên mọi trang, nên nút gửi lại thư phải trả người dùng về đúng trang họ đang xem thay vì
     * hất tất cả về thực đơn.
     */
    private void attachCurrentPath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        String path = pathInfo == null ? req.getServletPath() : req.getServletPath() + pathInfo;
        req.setAttribute("currentPath", path == null || path.isEmpty() ? "/menu" : path);
    }

    /**
     * Số tin chưa đọc cho huy hiệu trên thanh điều hướng.
     * <p>
     * Đặt ở đây vì thanh điều hướng nằm trên <b>mọi</b> trang, nên con số cũng phải có trên mọi
     * trang. Để từng servlet tự nạp thì huy hiệu chỉ hiện đúng ở những trang có người nhớ thêm
     * dòng đó, và một huy hiệu lúc có lúc không còn tệ hơn không có: khách sẽ hiểu là tin mới
     * vừa tự biến mất.
     * <p>
     * Đọc lại ở mỗi lượt xem trang chứ không nhớ trong phiên: tin sinh ra từ bếp, từ quầy và
     * từ bộ hẹn giờ — không phải từ thao tác của chính khách — nên không có thời điểm nào để
     * làm mới một con số đã nhớ. Câu đếm chạy trên {@code IX_Notification_user} và chỉ chạy
     * cho tài khoản khách; trang thực đơn của người chưa đăng nhập không đụng tới.
     */
    private void attachUnreadNotifications(HttpServletRequest req) {
        User user = WebUtil.currentUser(req);
        if (user == null || !RoleName.CUSTOMER.name().equals(user.getRoleName())) {
            return;
        }
        try {
            req.setAttribute("unreadNotifications", notificationService.unreadCount(user.getUserId()));
        } catch (RuntimeException e) {
            // Huy hiệu là thứ trang trí. Cơ sở dữ liệu trục trặc thì trang vẫn phải dựng ra
            // được — hỏng ở đây mà làm đổ cả trang thực đơn là đổi một tiện ích nhỏ lấy
            // toàn bộ màn hình.
            LOG.log(Level.WARNING, "Khong dem duoc thong bao chua doc", e);
        }
    }

    protected void redirect(HttpServletRequest req, HttpServletResponse resp, String path)
            throws IOException {
        resp.sendRedirect(req.getContextPath() + path);
    }

    protected User requireUser(HttpServletRequest req) {
        User user = WebUtil.currentUser(req);
        if (user == null) {
            throw new AppException("Vui lòng đăng nhập.", 401);
        }
        return user;
    }

    /**
     * Lấy người dùng trên <b>trang công khai</b>; chưa đăng nhập thì đẩy sang trang đăng nhập
     * và trả về {@code null}.
     * <p>
     * {@code /menu} và {@code /product/detail} nằm ngoài {@code AuthenticationFilter}, nên mọi
     * yêu cầu gửi tới đó đều lọt qua — kể cả yêu cầu ghi dữ liệu của khách chưa đăng nhập. Dùng
     * {@link #requireUser} ở đó sẽ ném ra lỗi 401 và khách nhận được một trang lỗi thay vì ô
     * đăng nhập, dù việc họ vừa làm hoàn toàn hợp lệ — chỉ là làm sớm một bước.
     * <p>
     * Ghi lại đường quay về theo đúng khoá mà bộ lọc dùng, để đăng nhập xong khách trở lại đúng
     * trang đang xem chứ không rơi về trang chủ.
     *
     * @param returnTo đường dẫn quay lại sau khi đăng nhập, tính từ gốc ứng dụng
     * @return người dùng đang đăng nhập, hoặc null nếu đã chuyển hướng — hãy dừng lại ngay
     */
    protected User userOrLogin(HttpServletRequest req, HttpServletResponse resp, String returnTo)
            throws IOException {
        User user = WebUtil.currentUser(req);
        if (user != null) {
            return user;
        }
        req.getSession().setAttribute("redirectAfterLogin", WebUtil.safeRedirect(returnTo, "/menu"));
        WebUtil.flashError(req, "Vui lòng đăng nhập để dùng chức năng này.");
        redirect(req, resp, "/login");
        return null;
    }

    /**
     * Chạy một thao tác ghi dữ liệu rồi chuyển hướng.
     * Thất bại thì giữ nguyên thông báo lỗi và quay lại đúng trang cũ, để người dùng
     * sửa và làm lại mà không mất ngữ cảnh.
     */
    protected void handle(HttpServletRequest req, HttpServletResponse resp,
                          Action action, String successMessage, String redirectPath) throws IOException {
        try {
            action.run();
            if (successMessage != null) {
                WebUtil.flashSuccess(req, successMessage);
            }
        } catch (AppException e) {
            WebUtil.flashError(req, e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Loi khong mong doi tai " + req.getRequestURI(), e);
            WebUtil.flashError(req, "Có lỗi xảy ra, vui lòng thử lại.");
        }
        redirect(req, resp, redirectPath);
    }

    @FunctionalInterface
    protected interface Action {
        void run();
    }
}
