package com.fastfood.integration.payment;

import com.fastfood.config.AppConfig;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SePayGateway implements PaymentGateway {

    private static final String QR_ENDPOINT = "https://qr.sepay.vn/img";

    public static final String API_KEY_SCHEME = "Apikey";

    private static final int MAX_PAYMENT_ID_DIGITS = 9;

    private final String accountNumber;
    private final String bank;
    private final String accountName;
    private final String apiKey;
    private final String contentPrefix;

    private final Pattern referencePattern;

    public SePayGateway() {
        this(AppConfig.sepayAccountNumber(), AppConfig.sepayBank(), AppConfig.sepayAccountName(),
             AppConfig.sepayApiKey(), AppConfig.sepayContentPrefix());
    }

    public SePayGateway(String accountNumber, String bank, String accountName,
                        String apiKey, String contentPrefix) {
        this.accountNumber = trim(accountNumber);
        this.bank = trim(bank);
        this.accountName = trim(accountName);
        this.apiKey = trim(apiKey);
        this.contentPrefix = trim(contentPrefix).isEmpty() ? "FF" : trim(contentPrefix).toUpperCase();
        this.referencePattern = Pattern.compile(
                "(?<![A-Za-z0-9])" + Pattern.quote(this.contentPrefix)
                        + "([0-9]{1," + MAX_PAYMENT_ID_DIGITS + "})(?![0-9])",
                Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String getName() {
        return "SEPAY";
    }

    @Override
    public PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl) {
        return new PaymentInitResult(baseUrl + "/payment/sepay?paymentId=" + paymentId,
                                     transferContent(paymentId));
    }

    @Override
    public boolean verifySignature(GatewayCallback callback) {
        if (apiKey.isEmpty()) {
            return false;
        }
        String presented = callback == null ? null : callback.getSignature();
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.trim().getBytes(StandardCharsets.UTF_8),
                                     apiKey.getBytes(StandardCharsets.UTF_8));
    }

    public String transferContent(int paymentId) {
        return contentPrefix + paymentId;
    }

    public Integer paymentIdFrom(String content) {
        if (content == null) {
            return null;
        }
        Matcher m = referencePattern.matcher(content);
        if (!m.find()) {
            return null;
        }
        try {
            int id = Integer.parseInt(m.group(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String qrImageUrl(BigDecimal amount, String transferContent) {
        return QR_ENDPOINT
                + "?acc=" + encode(accountNumber)
                + "&bank=" + encode(bank)
                + "&amount=" + amount.toBigInteger()
                + "&des=" + encode(transferContent);
    }

    public boolean isConfigured() {
        return !accountNumber.isEmpty() && !bank.isEmpty() && !apiKey.isEmpty();
    }

    public String getAccountNumber() { return accountNumber; }
    public String getBank()          { return bank; }
    public String getAccountName()   { return accountName; }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 luon co mat", e);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
