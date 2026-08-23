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
                    + " - quay ve PAYOS");
        } else if (!"PAYOS".equals(name) && !name.isEmpty()) {
            LOG.warning("Khong biet cong thanh toan '" + provider + "', dung PAYOS");
        }

        PayOsGateway payos = new PayOsGateway();
        if (!payos.isConfigured()) {
            /* Không tự chuyển sang một cổng khác cho "chạy được": thiếu bộ khoá thì không có
               cách nào thu tiền, và một cổng giả lập âm thầm thế chỗ là đúng thứ khiến người ta
               tưởng đã thu được. Để nó hỏng ngay ở bước mở cổng, kèm lý do. */
            LOG.severe("Thieu payment.payos.clientId/apiKey/checksumKey"
                    + " - khach se khong mo duoc cong thanh toan");
        } else {
            LOG.info("Cong thanh toan: PAYOS, ma cua hang " + payos.getClientId());
        }
        return payos;
    }

    static void reset() {
        instance = null;
    }
}
