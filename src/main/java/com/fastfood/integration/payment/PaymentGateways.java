package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.util.logging.Logger;

public final class PaymentGateways {

    private static final Logger LOG = Logger.getLogger(PaymentGateways.class.getName());

    private static volatile PaymentGateway instance;

    private PaymentGateways() {
    }

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
            LOG.severe("Cong thanh toan dat la SEPAY nhung thieu payment.sepay.accountNumber/bank/apiKey"
                    + " - quay ve VNPAY");
        } else if (!"VNPAY".equals(name) && !name.isEmpty()) {
            LOG.warning("Khong biet cong thanh toan '" + provider + "', dung VNPAY");
        }

        VnPayGateway vnpay = new VnPayGateway();
        if (!vnpay.isConfigured()) {
            /* Không tự chuyển sang một cổng khác cho "chạy được": thiếu mã website hoặc chuỗi bí
               mật thì không có cách nào thu tiền, và một cổng giả lập âm thầm thế chỗ là đúng
               thứ khiến người ta tưởng đã thu được. Để nó hỏng ngay ở bước mở cổng, kèm lý do. */
            LOG.severe("Thieu payment.vnpay.tmnCode hoac payment.vnpay.hashSecret"
                    + " - khach se khong mo duoc cong thanh toan");
        } else {
            LOG.info("Cong thanh toan: VNPAY, ma website " + vnpay.getTmnCode()
                    + ", may chu " + vnpay.getPayUrl());
        }
        return vnpay;
    }

    static void reset() {
        instance = null;
    }
}
