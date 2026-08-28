package com.fastfood.integration.notification;

import java.util.logging.Logger;

public class MockNotificationSender implements NotificationSender {

    private static final Logger LOG = Logger.getLogger(MockNotificationSender.class.getName());

    @Override
    /** Trả tên kênh MOCK. */
    public String getChannel() {
        return "MOCK";
    }

    @Override
    /** Ghi nội dung thư vào log thay vì gửi ra SMTP, dùng cho phát triển và kiểm thử. */
    public boolean send(String recipient, String subject, String content) {
        LOG.info(() -> String.format("[THONG BAO] den=%s | %s | %s",
                recipient == null ? "(khong co email)" : recipient, subject, content));
        return true;
    }
}
