package com.fastfood.common.constant;

/**
 * Sự kiện gửi tin cho khách — module 6.
 * <p>
 * Tin báo món sẵn sàng phải chứa giờ hẹn và mã nhận hàng.
 * <p>
 * Hai sự kiện cuối là tin xấu, và chính vì thế mà chúng cần thiết: đơn hết hiệu lực hay bị huỷ
 * mà không báo thì khách vẫn đinh ninh mình sắp có đồ ăn, tới nơi mới biết. Nặng nhất là khoản
 * tiền về sau khi đơn đã chết rồi được hoàn lại ngay — không có tin này thì khách vừa mất tiền
 * vừa mất đơn và chỉ phát hiện qua sao kê ngân hàng.
 * <p>
 * Danh sách phải khớp ràng buộc {@code CK_Notification_event} của bảng Notification.
 */
public enum NotificationEvent {

    ORDER_CONFIRMED,
    ORDER_READY,
    ORDER_CANCELLED,
    ORDER_EXPIRED
}
