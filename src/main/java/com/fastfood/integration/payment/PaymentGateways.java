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
                    + " - tam dung ban gia lap");
            return new MockPaymentGateway();
        }
        if (!"MOCK".equals(name) && !name.isEmpty()) {
            LOG.warning("Khong biet cong thanh toan '" + provider + "', dung ban gia lap");
        }
        LOG.info("Cong thanh toan: MOCK (trang thanh toan gia lap trong ung dung)");
        return new MockPaymentGateway();
    }

    static void reset() {
        instance = null;
    }
}
