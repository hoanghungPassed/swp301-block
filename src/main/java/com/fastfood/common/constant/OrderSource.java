package com.fastfood.common.constant;

/**
 * Kênh Order - BR-03: MVP chỉ có hai order_source, KHÔNG có DELIVERY.
 */
public enum OrderSource {

    /**
     * Đặt trước từ xa + Scheduled Store Pickup.
     * Bắt buộc login, chọn pickup_time, thanh toán ONLINE_GATEWAY (BR-04).
     */
    ONLINE_PREORDER,

    /**
     * Walk-in tại quầy. Cashier tạo Order, thu CASH hoặc ONLINE_GATEWAY,
     * release KDS ngay sau payment (BR-10). Không cần Pickup Code.
     */
    POS;

    public boolean isOnline() {
        return this == ONLINE_PREORDER;
    }

    /** BR-15: chỉ Online cần verify Pickup Code/QR trước handoff. */
    public boolean requiresPickupCode() {
        return this == ONLINE_PREORDER;
    }
}
