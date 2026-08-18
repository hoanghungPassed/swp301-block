package com.fastfood.common.exception;

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

    public static class ValidationException extends AppException {
        public ValidationException(String message) {
            super(message, 400);
        }
    }

    public static class AccessDeniedException extends AppException {
        public AccessDeniedException(String message) {
            super(message, 403);
        }
    }

    public static class NotFoundException extends AppException {
        public NotFoundException(String message) {
            super(message, 404);
        }
    }

    public static class BusinessException extends AppException {
        public BusinessException(String message) {
            super(message, 409);
        }
    }

    public static class DataAccessException extends RuntimeException {
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
