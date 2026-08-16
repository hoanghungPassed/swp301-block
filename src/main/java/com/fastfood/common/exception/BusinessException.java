package com.fastfood.common.exception;

/**
 * Thao tác hợp lệ về mặt dữ liệu nhưng vi phạm quy tắc nghiệp vụ.
 * Ví dụ: huỷ đơn khi bếp đã bắt đầu làm, giao món khi chưa thanh toán.
 */
public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message, 409);
    }
}
