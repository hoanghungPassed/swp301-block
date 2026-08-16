package com.fastfood.filter;

import javax.servlet.http.HttpServletRequest;

/**
 * Đường dẫn của yêu cầu tính từ gốc ứng dụng, ở dạng mà ba bộ lọc đều so sánh được.
 * <p>
 * <b>Vì sao không cắt chuỗi từ {@code getRequestURI()} ở mỗi bộ lọc.</b> Chuỗi đó là địa chỉ
 * thô: còn nguyên mã hoá phần trăm, và còn mang theo <i>tham số đường dẫn</i> — phần sau dấu
 * chấm phẩy, như {@code /menu;jsessionid=ABC}. Trình duyệt bình thường không sinh ra nó, nhưng
 * bất kỳ ai cũng gõ thêm được, mà máy chủ vẫn định tuyến tới đúng servlet vì phần đó bị bỏ qua
 * khi so khớp địa chỉ. Nghĩa là bộ lọc và máy chủ nhìn hai chuỗi khác nhau cho cùng một yêu cầu.
 * <p>
 * Lệch kiểu này nguy hiểm ở chỗ nó lệch theo cả hai chiều. Với danh sách trang công khai của
 * {@link AuthenticationFilter} thì chỉ gây bắt đăng nhập thừa. Nhưng {@link CsrfFilter} dùng
 * chính chuỗi đó để tra danh sách <i>miễn trừ</i>, và ở đó chiều ngược lại mới là chiều đáng lo.
 * <p>
 * {@code getServletPath()} và {@code getPathInfo()} là phần địa chỉ đã được chính máy chủ tách
 * ra để chọn servlet: đã giải mã, đã bỏ tham số đường dẫn. Ghép hai phần đó lại thì bộ lọc chắc
 * chắn đang nói về đúng địa chỉ mà máy chủ sắp gọi tới.
 */
final class RequestPath {

    private RequestPath() {
    }

    static String of(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();
        String path = pathInfo == null ? servletPath : servletPath + pathInfo;
        return path == null || path.isEmpty() ? "/" : path;
    }
}
