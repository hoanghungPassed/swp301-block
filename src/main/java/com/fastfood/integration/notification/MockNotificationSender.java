package com.fastfood.integration.notification;

import java.util.logging.Logger;

/**
 * Bản giả lập: ghi nội dung tin ra log máy chủ thay vì gửi email.
 * Đủ để kiểm tra rằng tin báo "món đã sẵn sàng" có kèm giờ hẹn và mã nhận hàng.
 */
public class MockNotificationSender implements NotificationSender {

    private static final Logger LOG = Logger.getLogger(MockNotificationSender.class.getName());

    @Override
    public String getChannel() {
        return "MOCK";
    }

    @Override
    public boolean send(String recipient, String subject, String content) {
        LOG.info(() -> String.format("[THONG BAO] den=%s | %s | %s",
                recipient == null ? "(khong co email)" : recipient, subject, content));
        return true;
    }
}
