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

public class CsrfFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(CsrfFilter.class.getName());

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/payment/vnpay/return", "/payment/vnpay/ipn", "/payment/sepay/webhook");

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

        req.setAttribute(CsrfUtil.REQUEST_ATTRIBUTE, CsrfUtil.token(req));

        if (rejected) {
            reject(req, resp, path);
            return;
        }
        chain.doFilter(request, response);
    }

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
