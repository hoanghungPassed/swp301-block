package com.fastfood.integration.notification;

import com.fastfood.config.AppConfig;

import java.util.logging.Logger;

/**
 * Chọn kênh gửi tin theo {@code notification.channel} trong {@code app.properties}.
 * <p>
 * Trước đây mỗi lớp Service tự viết {@code new MockNotificationSender()}, nên khối
 * {@code notification.*} trong file cấu hình không có tác dụng gì: đổi cấu hình xong ứng dụng
 * vẫn ghi log như cũ, và không có dấu hiệu nào cho biết vì sao. Một chỗ chọn duy nhất thì đổi
 * một dòng cấu hình là đổi cả hệ thống.
 * <p>
 * <b>Mặc định là MOCK, và đó là lựa chọn có chủ ý.</b> Máy chạy demo không có SMTP, hoặc mất
 * mạng giữa buổi bảo vệ, thì mọi luồng vẫn đi hết được — thư "gửi" ra log, người trình bày mở
 * log lấy liên kết. Bật SMTP nhầm ở máy không cấu hình được sẽ chỉ đổi một chức năng chạy được
 * thành một chức năng báo lỗi.
 * <p>
 * Giữ lại đúng một thực thể cho mỗi lần chạy: kênh SMTP dựng sẵn tham số kết nối, và không có
 * lý do gì để mỗi lớp Service giữ một bản riêng.
 */
public final class NotificationSenders {

    private static final Logger LOG = Logger.getLogger(NotificationSenders.class.getName());

    private static volatile NotificationSender instance;

    private NotificationSenders() {
    }

    /**
     * Kênh đang dùng. Đọc cấu hình một lần rồi nhớ lại — đổi {@code notification.channel} thì
     * phải nạp lại ứng dụng, cùng cách với mọi tham số khác trong {@code app.properties}.
     */
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

    private static NotificationSender create(String channel) {
        String name = channel == null ? "" : channel.trim().toUpperCase();
        if ("SMTP".equals(name)) {
            SmtpNotificationSender smtp = new SmtpNotificationSender();
            if (smtp.isConfigured()) {
                LOG.info("Kenh gui tin: SMTP");
                return smtp;
            }
            // Quay về bản giả lập thay vì để mọi lá thư hỏng lặng lẽ từng cái một. Dòng log này
            // là thứ duy nhất cho biết vì sao thư không tới, nên nó ở mức SEVERE.
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

    /** Dựng lại kênh ở lần gọi sau. Chỉ dùng cho kiểm thử. */
    static void reset() {
        instance = null;
    }
}
