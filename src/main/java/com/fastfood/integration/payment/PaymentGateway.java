package com.fastfood.integration.payment;

import java.math.BigDecimal;

public interface PaymentGateway {

    /** Tên nhà cung cấp được dùng khi ghi PaymentTransaction. */
    String getName();

    /** Khởi tạo phiên thanh toán và trả địa chỉ để trình duyệt tiếp tục luồng. */
    PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl);

    /** Xác minh callback thực sự đến từ nhà cung cấp trước khi cập nhật đơn đã trả tiền. */
    boolean verifySignature(GatewayCallback callback);
}
