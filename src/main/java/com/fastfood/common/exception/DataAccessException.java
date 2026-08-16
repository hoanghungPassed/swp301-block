package com.fastfood.common.exception;

/** Bọc SQLException để tầng trên không phải phụ thuộc vào java.sql. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
