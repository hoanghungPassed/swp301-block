package com.fastfood.common.exception;

/** Không tìm thấy đối tượng, hoặc người dùng không có quyền xem nên coi như không tồn tại. */
public class NotFoundException extends AppException {
    public NotFoundException(String message) {
        super(message, 404);
    }
}
