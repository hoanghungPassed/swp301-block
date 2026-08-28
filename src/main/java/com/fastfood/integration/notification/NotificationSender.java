package com.fastfood.integration.notification;

public interface NotificationSender {

    /** Tên kênh gửi để NotificationService ghi nhận SMTP hay MOCK. */
    String getChannel();

    /** Gửi một thông báo; trả false để service lưu trạng thái FAILED thay vì báo thành công giả. */
    boolean send(String recipient, String subject, String content);
}
