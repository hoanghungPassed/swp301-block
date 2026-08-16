package com.fastfood.common.exception;

/** Dữ liệu người dùng nhập không hợp lệ. */
public class ValidationException extends AppException {
    public ValidationException(String message) {
        super(message, 400);
    }
}
