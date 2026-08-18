package com.fastfood.integration.notification;

public interface NotificationSender {

    String getChannel();

    boolean send(String recipient, String subject, String content);
}
