package com.fastfood.common.exception;

/**
 * Lỗi nghiệp vụ có thông báo hiển thị được cho người dùng.
 * Controller bắt loại này và hiện thông báo, thay vì trả về trang lỗi 500.
 */
public class AppException extends RuntimeException {

    private final int httpStatus;

    public AppException(String message) {
        this(message, 400);
    }

    public AppException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 400;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
