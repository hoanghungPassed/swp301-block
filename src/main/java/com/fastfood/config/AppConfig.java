package com.fastfood.config;

import com.fastfood.common.constant.Constants.BusinessRule;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

public final class AppConfig {

    private static final Logger LOG = Logger.getLogger(AppConfig.class.getName());
    private static Properties props = new Properties();

    private AppConfig() {
    }

    public static synchronized void init() {
        if (!load("app.properties")) {
            LOG.warning("AppConfig: khong thay app.properties, dung gia tri mac dinh");
        }
        /* Nạp chồng bằng app.local.properties nếu có. Tệp này bị .gitignore bỏ qua, nên đây là
           chỗ để KHOÁ THẬT — payos clientId/apiKey/checksumKey, mật khẩu SMTP — nằm mà không
           theo commit đi ra kho chung. Nạp SAU nên mọi khoá trùng tên đều đè lên app.properties;
           không có tệp này thì ứng dụng chạy y như cũ. Xem docs/PAYOS.md §2. */
        if (load("app.local.properties")) {
            LOG.info("AppConfig: da nap de app.local.properties");
        }
    }

    /** Đọc một tệp cấu hình trong classpath vào {@link #props}. Trả về false nếu không có tệp. */
    private static boolean load(String resource) {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            /* Đọc qua Reader UTF-8 chứ không đưa thẳng InputStream: Properties.load(InputStream)
               luôn hiểu tệp theo ISO-8859-1, bất kể máy đang chạy bảng mã nào. Hiện mọi GIÁ TRỊ
               trong app.properties đều là ASCII nên chưa lộ, nhưng có hai ô người dùng sẽ điền
               tiếng Việt vào — notification.mail.fromName và payment.sepay.accountName — và lúc
               đó tên cửa hàng sẽ đi vào thư và mã VietQR ở dạng hỏng. */
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            LOG.info("AppConfig: da nap " + resource);
            return true;
        } catch (IOException e) {
            LOG.warning("AppConfig: loi doc " + resource + ", bo qua - " + e.getMessage());
            return false;
        }
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int pickupMinLeadMinutes() {
        return getInt("business.pickup.minLeadMinutes", BusinessRule.PICKUP_MIN_LEAD_MINUTES);
    }

    public static int kitchenPrepLeadMinutes() {
        return getInt("business.kitchen.prepLeadMinutes", BusinessRule.KITCHEN_PREP_LEAD_MINUTES);
    }

    public static int paymentExpiryMinutes() {
        return getInt("business.payment.expiryMinutes", BusinessRule.PAYMENT_EXPIRY_MINUTES);
    }

    public static int pickupOverdueMinutes() {
        return getInt("business.pickup.overdueMinutes", BusinessRule.PICKUP_OVERDUE_MINUTES);
    }

    public static int storeOpenHour() {
        return getInt("business.store.openHour", BusinessRule.STORE_OPEN_HOUR);
    }

    public static int storeCloseHour() {
        return getInt("business.store.closeHour", BusinessRule.STORE_CLOSE_HOUR);
    }

    public static int releaseIntervalSeconds() {
        return getInt("scheduler.kitchenRelease.intervalSeconds", 30);
    }

    public static int expiryIntervalSeconds() {
        return getInt("scheduler.paymentExpiry.intervalSeconds", 60);
    }

    public static String gatewayProvider() {
        return get("payment.gateway.provider", "PAYOS");
    }

    public static String payosClientId() {
        return get("payment.payos.clientId", "");
    }

    public static String payosApiKey() {
        return get("payment.payos.apiKey", "");
    }

    public static String payosChecksumKey() {
        return get("payment.payos.checksumKey", "");
    }

    public static String payosReturnUrl() {
        return get("payment.payos.returnUrl", "");
    }

    public static String payosBaseUrl() {
        return get("payment.payos.baseUrl", "");
    }

    /**
     * Khoảng cộng thêm vào mã khoản thu để ra mã đơn gửi sang PayOS.
     *
     * <p>PayOS không cho dùng lại một mã đơn, kể cả sau khi liên kết đã huỷ, còn mã khoản thu ở
     * đây thì quay về đếm từ 1 mỗi lần nạp lại cơ sở dữ liệu. Nạp lại xong mà cổng báo "đơn
     * thanh toán đã tồn tại" thì tăng số này lên quá số khoản thu đã từng tạo là hết.
     */
    public static long payosOrderCodeOffset() {
        try {
            return Long.parseLong(get("payment.payos.orderCodeOffset", "0").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String sepayAccountNumber() {
        return get("payment.sepay.accountNumber", "");
    }

    public static String sepayBank() {
        return get("payment.sepay.bank", "");
    }

    public static String sepayAccountName() {
        return get("payment.sepay.accountName", "");
    }

    public static String sepayApiKey() {
        return get("payment.sepay.apiKey", "");
    }

    public static String sepayContentPrefix() {
        return get("payment.sepay.contentPrefix", "FF");
    }

    public static String notificationChannel() {
        return get("notification.channel", "MOCK");
    }
}
