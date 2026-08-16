package com.fastfood.common.constant;

/**
 * Payment Status - mục 7.4. MVP không partial refund (chỉ full refund).
 */
public enum PaymentStatus {

    /** Chỉ dùng cho POS Cash trước khi Cashier thu tiền. */
    UNPAID,

    /** Online gateway đang xử lý. */
    PENDING,

    /** Payment thành công hợp lệ. */
    PAID,

    /** Attempt thất bại; có thể retry khi Order chưa EXPIRED (BR-14). */
    FAILED,

    /** Full refund thành công (UC-23). */
    REFUNDED;

    public boolean isRetryable() {
        return this == FAILED;
    }

    /** BR-15: điều kiện handoff yêu cầu Payment PAID. */
    public boolean isSettled() {
        return this == PAID;
    }
}
