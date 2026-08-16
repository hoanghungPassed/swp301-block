package com.fastfood.common.constant;

/**
 * Role - mục 4 & 8 (ma trận phân quyền). MVP: 1 User = 1 Role.
 * RBAC được enforce ở backend (Filter + Service), không dựa UI hiding (case J mục 15).
 */
public enum RoleName {

    CUSTOMER,
    CASHIER,
    KITCHEN,
    ADMIN;

    /** Vai trò theo tên, ném lỗi nếu không nhận ra — dùng khi tên chắc chắn phải hợp lệ. */
    public static RoleName from(String value) {
        RoleName role = parse(value);
        if (role == null) {
            throw new IllegalArgumentException("Unknown role: " + value);
        }
        return role;
    }

    /**
     * Vai trò theo tên, hoặc {@code null} nếu không nhận ra.
     * <p>
     * Dành cho tầng phân quyền. Ở đó một tên vai trò lạ phải dẫn tới "không có quyền", chứ không
     * phải một ngoại lệ bay lên thành trang lỗi 500 — trang lỗi vừa không nói được gì cho người
     * dùng, vừa làm mất dấu chuyện thật sự đáng chú ý là dữ liệu vai trò đã hỏng.
     */
    public static RoleName parse(String value) {
        for (RoleName r : values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return null;
    }
}
