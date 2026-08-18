package com.fastfood.filter;

import javax.servlet.http.HttpServletRequest;

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
