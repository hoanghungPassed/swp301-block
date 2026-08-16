package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.util.logging.Logger;

/**
 * Chọn cổng thanh toán theo {@code payment.gateway.provider} trong {@code app.properties}.
 * <p>
 * Trước đây {@code PaymentService} tự viết {@code new MockPaymentGateway()}, nên dòng
 * {@code payment.gateway.provider} trong file cấu hình không có tác dụng gì. Một chỗ chọn duy
 * nhất thì đổi một dòng cấu hình là đổi cả hệ thống — cùng cách làm với
 * {@code NotificationSenders}.
 * <p>
 * <b>Mặc định là MOCK, và đó là lựa chọn có chủ ý.</b> Máy chạy demo không có tài khoản ngân
 * hàng thật, và một buổi bảo vệ không thể phụ thuộc vào việc có người thật sự chuyển khoản
 * đúng lúc. Bật SePay nhầm ở máy chưa cấu hình sẽ chỉ đổi một chức năng chạy được thành một
 * chức năng báo lỗi, nên thiếu tham số thì tự quay về bản giả lập kèm một dòng SEVERE.
 * <p>
 * Giữ đúng một thực thể cho mỗi lần chạy: cả hai bản cài đặt đều dựng sẵn tham số lúc khởi tạo
 * và không giữ trạng thái nào thay đổi theo từng lần gọi.
 */
public final class PaymentGateways {

    private static final Logger LOG = Logger.getLogger(PaymentGateways.class.getName());

    private static volatile PaymentGateway instance;

    private PaymentGateways() {
    }

    /**
     * Cổng đang dùng. Đọc cấu hình một lần rồi nhớ lại — đổi {@code payment.gateway.provider}
     * thì phải nạp lại ứng dụng, cùng cách với mọi tham số khác trong {@code app.properties}.
     */
    public static PaymentGateway fromConfig() {
        PaymentGateway local = instance;
        if (local == null) {
            synchronized (PaymentGateways.class) {
                local = instance;
                if (local == null) {
                    local = create(AppConfig.gatewayProvider());
                    instance = local;
                }
            }
        }
        return local;
    }

    private static PaymentGateway create(String provider) {
        String name = provider == null ? "" : provider.trim().toUpperCase();
        if ("SEPAY".equals(name)) {
            SePayGateway sepay = new SePayGateway();
            if (sepay.isConfigured()) {
                LOG.info("Cong thanh toan: SEPAY, tai khoan " + sepay.getBank()
                        + " " + sepay.getAccountNumber());
                return sepay;
            }
            // Dòng này ở mức SEVERE vì nếu không có nó thì triệu chứng duy nhất là mã QR hiện ra
            // trắng trơn hoặc dẫn tiền tới một tài khoản rỗng — không nơi nào nói vì sao.
            LOG.severe("Cong thanh toan dat la SEPAY nhung thieu payment.sepay.accountNumber/bank/apiKey"
                    + " - tam dung ban gia lap");
            return new MockPaymentGateway();
        }
        if (!"MOCK".equals(name) && !name.isEmpty()) {
            LOG.warning("Khong biet cong thanh toan '" + provider + "', dung ban gia lap");
        }
        LOG.info("Cong thanh toan: MOCK (trang thanh toan gia lap trong ung dung)");
        return new MockPaymentGateway();
    }

    /** Dựng lại cổng ở lần gọi sau. Chỉ dùng cho kiểm thử. */
    static void reset() {
        instance = null;
    }
}
