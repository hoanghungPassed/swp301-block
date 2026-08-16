package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Nhật ký giao dịch với cổng thanh toán. Ánh xạ tới bảng PaymentTransaction.
 * <p>
 * {@code externalTransactionId} là khoá duy nhất: cổng thanh toán có thể gọi lại nhiều lần
 * cho cùng một giao dịch, lần thứ hai sẽ bị cơ sở dữ liệu từ chối nên tiền không bị ghi nhận hai lần.
 */
public class Transaction {

    private int transactionId;
    private int paymentId;
    private String gateway;
    private String externalTransactionId;
    private String status;
    private String rawReference;
    private LocalDateTime createdAt;

    public int getTransactionId() { return transactionId; }
    public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRawReference() { return rawReference; }
    public void setRawReference(String rawReference) { this.rawReference = rawReference; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
