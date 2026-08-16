package com.fastfood.integration.payment;

import java.math.BigDecimal;

/**
 * Cổng thanh toán trực tuyến.
 * <p>
 * Tách thành giao diện vì đây là chỗ thật sự cần thay thế được: bản chạy thử dùng
 * {@link MockPaymentGateway}, bản chạy thật dùng {@link SePayGateway}. Chọn bằng một dòng
 * cấu hình, xem {@link PaymentGateways}.
 * <p>
 * Giao diện này cố ý <b>không</b> giả định khách bị chuyển sang một trang của bên thứ ba.
 * Hai bản cài đặt hiện có đều dựng trang thanh toán ngay trong ứng dụng, chỉ khác nội dung:
 * bản giả lập cho bấm chọn kết quả, còn SePay hiện mã VietQR để khách quét bằng ứng dụng
 * ngân hàng. Vì vậy tham số truyền vào là địa chỉ gốc của ứng dụng chứ không phải một địa chỉ
 * quay về cố định — mỗi cổng tự biết trang của mình nằm ở đâu.
 */
public interface PaymentGateway {

    /** Tên cổng, ghi vào nhật ký đối soát. */
    String getName();

    /**
     * Khởi tạo một lần thanh toán.
     *
     * @param baseUrl địa chỉ gốc của ứng dụng, ví dụ {@code http://localhost:8080/fastfood}
     * @return địa chỉ để chuyển khách sang trang thanh toán, kèm mã giao dịch phía cổng
     */
    PaymentInitResult initiate(int paymentId, int orderId, BigDecimal amount, String baseUrl);

    /**
     * Kiểm tra dữ liệu cổng thanh toán gửi về có thật sự do cổng phát ra không.
     * Trả về false thì tuyệt đối không được ghi nhận tiền.
     * <p>
     * Bằng chứng khác nhau tuỳ cổng — chữ ký tính từ khoá dùng chung với cổng chuyển hướng,
     * khoá API ở header với SePay — nên chỗ này chỉ hỏi kết luận, không hỏi cách chứng minh.
     */
    boolean verifySignature(GatewayCallback callback);
}
