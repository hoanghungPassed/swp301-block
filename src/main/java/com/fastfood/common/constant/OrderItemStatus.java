package com.fastfood.common.constant;

/**
 * OrderItem Status - mục 7.3. Kitchen chỉ được cập nhật theo đúng chuỗi này (BR-11).
 * BR-18: một OrderItem chỉ READY khi toàn bộ quantity của line item hoàn tất.
 */
public enum OrderItemStatus {

    WAITING,
    PREPARING,
    READY;

    /** Chặn nhảy trạng thái / rollback tùy ý (BR-19). */
    public boolean canTransitionTo(OrderItemStatus next) {
        return (this == WAITING && next == PREPARING)
            || (this == PREPARING && next == READY);
    }
}
