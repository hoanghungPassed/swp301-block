package com.fastfood.model.entity;

import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.constant.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một lần thanh toán cho đơn hàng.
 * Khách thanh toán thất bại rồi thử lại sẽ sinh dòng mới chứ không ghi đè dòng cũ,
 * để còn đối soát được với cổng thanh toán.
 */
public class Payment {

    private int paymentId;
    private int orderId;
    private String method;
    private BigDecimal amount;
    private String paymentStatus;
    private int attemptNo;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;

    private String orderSource;

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }

    public String getOrderSource() { return orderSource; }
    public void setOrderSource(String orderSource) { this.orderSource = orderSource; }

    public PaymentStatus statusEnum() { return PaymentStatus.valueOf(paymentStatus); }
    public PaymentMethod methodEnum() { return PaymentMethod.valueOf(method); }

    public boolean isPaid()     { return PaymentStatus.PAID.name().equals(paymentStatus); }
    public boolean isPending()  { return PaymentStatus.PENDING.name().equals(paymentStatus); }
    public boolean isRefunded() { return PaymentStatus.REFUNDED.name().equals(paymentStatus); }
    public boolean isCash()     { return PaymentMethod.CASH.name().equals(method); }
}
