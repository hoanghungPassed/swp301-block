package com.fastfood.common.constant;

/**
 * Tham số nghiệp vụ đã khóa - mục 18 (Requirements Baseline V6).
 * Giá trị mặc định ở đây khớp app.properties; {@link com.fastfood.config.AppConfig}
 * nạp đè khi ứng dụng khởi động.
 */
public final class BusinessRule {

    /** BR-05: pickup_time phải cách checkout tối thiểu 30 phút. */
    public static final int PICKUP_MIN_LEAD_MINUTES = 30;

    /** BR-08: kitchen_release_at = pickup_time - 20 phút (lead time cố định). */
    public static final int KITCHEN_PREP_LEAD_MINUTES = 20;

    /** BR-13: Online PENDING_PAYMENT không PAID trong 15 phút -> EXPIRED. */
    public static final int PAYMENT_EXPIRY_MINUTES = 15;

    /** BR-17: READY quá pickup_time + 30 phút -> UI flag OVERDUE (không auto cancel/refund). */
    public static final int PICKUP_OVERDUE_MINUTES = 30;

    /** Độ dài Pickup Code sinh cho Online Order (UC-15). */
    public static final int PICKUP_CODE_LENGTH = 6;

    /**
     * Số lượng tối đa cho một dòng món, áp dụng cho cả giỏ hàng của khách và phiếu tạm ở quầy.
     * <p>
     * Đặt ở đây chứ không ở một trong hai tầng service vì cả hai đường đặt hàng phải chặn
     * cùng một con số — để riêng thì sửa một bên là hai đường lệch nhau mà không ai biết.
     */
    public static final int MAX_QUANTITY_PER_LINE = 50;

    /** NFR-03: release phải xảy ra trong vòng 60 giây kể từ kitchen_release_at. */
    public static final int RELEASE_ACCURACY_SECONDS = 60;

    /**
     * Giờ mở cửa và đóng cửa (theo giờ trong ngày, 0-23).
     * <p>
     * Không có ràng buộc này thì khách hẹn giờ lấy hàng lúc 3 giờ sáng vẫn đặt được, và bộ
     * hẹn giờ vẫn đẩy đơn xuống bếp lúc 2 giờ 40 khi cửa hàng không có ai.
     */
    public static final int STORE_OPEN_HOUR = 7;
    public static final int STORE_CLOSE_HOUR = 21;

    private BusinessRule() {
    }
}
