package com.fastfood.common.constant;

/**
 * Payment Method - module 3.
 * ONLINE_PREORDER chỉ ONLINE_GATEWAY (BR-04); POS hỗ trợ cả CASH và ONLINE_GATEWAY.
 */
public enum PaymentMethod {

    ONLINE_GATEWAY,
    CASH;

    /** BR-04: không Pay at Counter cho Online Pre-order. */
    public boolean isAllowedFor(OrderSource source) {
        return source == OrderSource.POS || this == ONLINE_GATEWAY;
    }
}
