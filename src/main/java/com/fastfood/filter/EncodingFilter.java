package com.fastfood.filter;

import javax.servlet.*;
import java.io.IOException;

/**
 * Đặt bảng mã UTF-8 cho mọi request và response.
 * Không có bộ lọc này thì tên món và họ tên tiếng Việt gửi lên từ biểu mẫu sẽ thành ký tự lỗi.
 * Phải chạy trước mọi bộ lọc khác vì việc đọc tham số sẽ chốt bảng mã.
 * <p>
 * Khai báo và thứ tự nằm trong {@code WEB-INF/web.xml}, không dùng {@code @WebFilter}:
 * đặc tả Servlet không bảo đảm thứ tự của bộ lọc khai báo bằng annotation.
 */
public class EncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);
    }
}
