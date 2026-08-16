package com.fastfood.common.exception;

/** Đã đăng nhập nhưng không đủ quyền thực hiện thao tác. */
public class AccessDeniedException extends AppException {
    public AccessDeniedException(String message) {
        super(message, 403);
    }
}
