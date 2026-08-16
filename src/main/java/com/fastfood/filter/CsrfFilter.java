package com.fastfood.filter;

import com.fastfood.common.util.CsrfUtil;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chặn yêu cầu ghi dữ liệu không xuất phát từ trang của chính hệ thống.
 * <p>
 * Hai việc trong một bộ lọc, vì chúng là hai nửa của cùng một cơ chế:
 * <ol>
 *   <li><b>Phát mã</b> cho mọi trang được dựng ra, qua thuộc tính request {@code csrfToken}.
 *       Trang JSP đặt nó vào ô ẩn của biểu mẫu.</li>
 *   <li><b>Kiểm mã</b> ở mọi yêu cầu làm thay đổi dữ liệu.</li>
 * </ol>
 * Tách đôi thì sẽ có lúc phát mà không kiểm, hoặc kiểm mà trang không có mã để gửi.
 * <p>
 * <b>Mặc định là chặn.</b> Danh sách miễn trừ được liệt kê tường minh và rất ngắn; thêm màn hình
 * mới mà quên đặt ô ẩn thì hậu quả là biểu mẫu báo lỗi ngay lần thử đầu tiên, chứ không phải
 * một lỗ hổng im lặng. {@code CsrfTokenPresenceTest} bắt chuyện đó ngay trong {@code mvn test}.
 * <p>
 * Khai báo và thứ tự nằm trong {@code WEB-INF/web.xml} — xem ghi chú ở đó.
 */
public class CsrfFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class.getName());

    /** Phương thức chỉ đọc, không đổi gì ở máy chủ nên không cần mã. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * Cổng thanh toán gọi thẳng vào đây từ máy chủ của họ, không đi qua trang nào của mình nên
     * không thể có mã. Chỗ này được bảo vệ bằng thứ khác: bằng chứng cổng phát ra dữ liệu (chữ
     * ký, hoặc khoá API ở header với SePay), việc đối chiếu số tiền, và ràng buộc duy nhất trên
     * mã giao dịch để một lần gọi trùng không tính thành hai lần trả tiền.
     * <p>
     * Miễn trừ ở đây còn có một tác dụng nữa với webhook của SePay: nó gửi dữ liệu ở thân yêu
     * cầu dạng JSON, mà việc kiểm mã lại phải đọc tham số — đọc tham số của một yêu cầu POST là
     * đọc hết thân, và servlet đứng sau sẽ nhận được một thân rỗng.
     */
    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/payment/callback", "/payment/sepay/webhook");

    /** Tài nguyên tĩnh: không cần mã, và cũng không nên vì thế mà tạo phiên cho người lạ ghé qua. */
    private static final String STATIC_PREFIX = "/assets/";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = RequestPath.of(req);
        if (path.startsWith(STATIC_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        boolean needsToken = !SAFE_METHODS.contains(req.getMethod())
                && !EXEMPT_PATHS.contains(path);
        boolean rejected = needsToken && !CsrfUtil.isValid(req);

        // Phát mã trước cả khi từ chối: trang lỗi 403 cũng có thanh điều hướng, và trên đó có
        // nút thoát — vốn là một biểu mẫu. Từ chối rồi mới phát thì đúng cái nút để người dùng
        // gỡ mình ra khỏi tình huống lại là cái nút duy nhất không bấm được.
        req.setAttribute(CsrfUtil.REQUEST_ATTRIBUTE, CsrfUtil.token(req));

        if (rejected) {
            reject(req, resp, path);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Từ chối bằng 403.
     * <p>
     * Nguyên nhân thường gặp nhất lại hoàn toàn vô hại: người dùng để trang mở qua đêm, phiên
     * hết hạn, rồi bấm gửi. Vì vậy thông báo nói ra cách sửa — tải lại trang — chứ không chỉ nói
     * là bị từ chối. Địa chỉ trả dữ liệu JSON thì chỉ nhận mã trạng thái, cùng lý do với
     * {@link RoleAuthorizationFilter}: trang gọi bằng JavaScript không đọc được HTML.
     */
    private void reject(HttpServletRequest req, HttpServletResponse resp, String path)
            throws IOException, ServletException {
        LOG.log(Level.WARNING, () -> "Tu choi yeu cau thieu ma CSRF: " + req.getMethod() + " " + path);
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        if (path.startsWith("/api/")) {
            return;
        }
        req.setAttribute("errorMessage", "Phiên làm việc đã hết hạn hoặc yêu cầu không hợp lệ. "
                + "Vui lòng tải lại trang và thao tác lại.");
        req.getRequestDispatcher("/WEB-INF/views/error/403.jsp").forward(req, resp);
    }
}
