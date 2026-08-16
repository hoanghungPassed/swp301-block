package com.fastfood.common.constant;

/**
 * KDS Release State - mục 7.2.
 * <p>
 * <b>KHÔNG phải Order Status.</b> Giá trị được suy ra từ {@code kitchen_release_at}
 * và {@code released_to_kds_at} để tránh làm phình state machine. Không có bảng riêng.
 */
public enum KdsReleaseState {

    /** Chưa đủ điều kiện release (ví dụ Order chưa CONFIRMED). */
    NOT_RELEASED,

    /** CONFIRMED + current_time < kitchen_release_at. */
    SCHEDULED,

    /** current_time >= kitchen_release_at, hoặc POS CONFIRMED (release ngay). */
    RELEASED_TO_KDS
}
