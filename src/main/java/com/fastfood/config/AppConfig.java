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

    /** Nạp app.properties và file override cục bộ một lần khi ứng dụng khởi động. */
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
    /** Nạp một resource properties vào bộ cấu hình, trả false khi resource không tồn tại. */
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

    /** Lấy cấu hình chuỗi hoặc giá trị mặc định. */
    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /** Lấy cấu hình số nguyên, dùng mặc định khi thiếu hoặc sai định dạng. */
    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Trả số phút tối thiểu khách phải đặt trước giờ nhận. */
    public static int pickupMinLeadMinutes() {
        return getInt("business.pickup.minLeadMinutes", BusinessRule.PICKUP_MIN_LEAD_MINUTES);
    }

    /** Trả số phút trước giờ nhận để đưa đơn xuống bếp. */
    public static int kitchenPrepLeadMinutes() {
        return getInt("business.kitchen.prepLeadMinutes", BusinessRule.KITCHEN_PREP_LEAD_MINUTES);
    }

    /** Trả thời gian đơn được giữ khi chưa thanh toán. */
    public static int paymentExpiryMinutes() {
        return getInt("business.payment.expiryMinutes", BusinessRule.PAYMENT_EXPIRY_MINUTES);
    }

    /** Trả số phút sau giờ hẹn để UI đánh dấu đơn nhận muộn. */
    public static int pickupOverdueMinutes() {
        return getInt("business.pickup.overdueMinutes", BusinessRule.PICKUP_OVERDUE_MINUTES);
    }

    /** Trả giờ cửa hàng mở cửa. */
    public static int storeOpenHour() {
        return getInt("business.store.openHour", BusinessRule.STORE_OPEN_HOUR);
    }

    /** Trả giờ cửa hàng đóng cửa. */
    public static int storeCloseHour() {
        return getInt("business.store.closeHour", BusinessRule.STORE_CLOSE_HOUR);
    }

    /** Trả chu kỳ scheduler kiểm tra đơn cần xuống bếp. */
    public static int releaseIntervalSeconds() {
        return getInt("scheduler.kitchenRelease.intervalSeconds", 30);
    }

    /** Trả chu kỳ scheduler kiểm tra đơn hết hạn thanh toán. */
    public static int expiryIntervalSeconds() {
        return getInt("scheduler.paymentExpiry.intervalSeconds", 60);
    }

    /** Trả mã cổng thanh toán đang dùng như PAYOS hoặc SEPAY. */
    public static String gatewayProvider() {
        return get("payment.gateway.provider", "PAYOS");
    }

    /** Trả client ID dùng xác thực API PayOS. */
    public static String payosClientId() {
        return get("payment.payos.clientId", "");
    }

    /** Trả API key dùng gọi PayOS. */
    public static String payosApiKey() {
        return get("payment.payos.apiKey", "");
    }

    /** Trả checksum key dùng ký request và xác minh webhook PayOS. */
    public static String payosChecksumKey() {
        return get("payment.payos.checksumKey", "");
    }

    /** Trả URL PayOS đưa khách quay lại sau thanh toán. */
    public static String payosReturnUrl() {
        return get("payment.payos.returnUrl", "");
    }

    /** Trả base URL API PayOS, cho phép thay bằng fake server khi test. */
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
    /** Trả offset tránh trùng orderCode giữa các lần cài đặt PayOS. */
    public static long payosOrderCodeOffset() {
        try {
            return Long.parseLong(get("payment.payos.orderCodeOffset", "0").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Trả số tài khoản nhận chuyển khoản SePay. */
    public static String sepayAccountNumber() {
        return get("payment.sepay.accountNumber", "");
    }

    /** Trả mã ngân hàng nhận chuyển khoản SePay. */
    public static String sepayBank() {
        return get("payment.sepay.bank", "");
    }

    /** Trả tên chủ tài khoản nhận tiền. */
    public static String sepayAccountName() {
        return get("payment.sepay.accountName", "");
    }

    /** Trả API key dùng xác thực webhook SePay. */
    public static String sepayApiKey() {
        return get("payment.sepay.apiKey", "");
    }

    /** Trả tiền tố nội dung chuyển khoản dùng tìm paymentId. */
    public static String sepayContentPrefix() {
        return get("payment.sepay.contentPrefix", "FF");
    }

    /** Trả kênh gửi thông báo đang cấu hình, ví dụ SMTP hoặc MOCK. */
    public static String notificationChannel() {
        return get("notification.channel", "MOCK");
    }
}
