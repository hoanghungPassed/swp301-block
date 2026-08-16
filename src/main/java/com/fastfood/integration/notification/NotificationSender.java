package com.fastfood.integration.notification;

/**
 * Kênh gửi tin cho khách.
 * Tách giao diện để đổi từ bản giả lập sang gửi email thật mà không sửa tầng Service.
 */
public interface NotificationSender {

    String getChannel();

    /** Trả về false nếu gửi thất bại; nhật ký sẽ ghi lại trạng thái đó. */
    boolean send(String recipient, String subject, String content);
}
