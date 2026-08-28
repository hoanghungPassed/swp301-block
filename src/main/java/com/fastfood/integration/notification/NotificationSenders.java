package com.fastfood.integration.notification;

import com.fastfood.config.AppConfig;

import java.util.logging.Logger;

public final class NotificationSenders {

    private static final Logger LOG = Logger.getLogger(NotificationSenders.class.getName());

    private static volatile NotificationSender instance;

    private NotificationSenders() {
    }

    /** Trả singleton sender theo kênh thông báo đang cấu hình. */
    public static NotificationSender fromConfig() {
        NotificationSender local = instance;
        if (local == null) {
            synchronized (NotificationSenders.class) {
                local = instance;
                if (local == null) {
                    local = create(AppConfig.notificationChannel());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Khởi tạo SMTP hoặc MOCK sender và từ chối kênh không được hỗ trợ. */
    private static NotificationSender create(String channel) {
        String name = channel == null ? "" : channel.trim().toUpperCase();
        if ("SMTP".equals(name)) {
            SmtpNotificationSender smtp = new SmtpNotificationSender();
            if (smtp.isConfigured()) {
                LOG.info("Kenh gui tin: SMTP");
                return smtp;
            }
            LOG.severe("Kenh gui tin dat la SMTP nhung thieu notification.mail.username/password"
                    + " - tam dung ban gia lap, thu chi ghi ra log");
            return new MockNotificationSender();
        }
        if (!"MOCK".equals(name) && !name.isEmpty()) {
            LOG.warning("Khong biet kenh gui tin '" + channel + "', dung ban gia lap");
        }
        LOG.info("Kenh gui tin: MOCK (thu chi ghi ra log may chu)");
        return new MockNotificationSender();
    }

    /** Xóa singleton sender để kiểm thử có thể nạp cấu hình khác. */
    static void reset() {
        instance = null;
    }
}
