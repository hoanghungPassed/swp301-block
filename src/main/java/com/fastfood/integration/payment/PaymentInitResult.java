package com.fastfood.integration.payment;

public class PaymentInitResult {

    private final String redirectUrl;
    private final String externalTransactionId;

    public PaymentInitResult(String redirectUrl, String externalTransactionId) {
        this.redirectUrl = redirectUrl;
        this.externalTransactionId = externalTransactionId;
    }

    public String getRedirectUrl() { return redirectUrl; }
    public String getExternalTransactionId() { return externalTransactionId; }
}
