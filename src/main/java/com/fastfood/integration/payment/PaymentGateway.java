package com.fastfood.integration.payment;

import java.math.BigDecimal;

public interface PaymentGateway {

    String getName();

    PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl);

    boolean verifySignature(GatewayCallback callback);
}
