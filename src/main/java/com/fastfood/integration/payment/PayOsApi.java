package com.fastfood.integration.payment;

import com.google.gson.JsonObject;

/**
 * Đường ra Internet của {@link PayOsGateway}, tách khỏi phần nghiệp vụ để bài kiểm tra thay
 * được bằng một bản giả — nếu không thì mọi thứ dưới đây chỉ chạy được khi có mạng và có khoá
 * thật, tức là trên thực tế không bao giờ được kiểm.
 *
 * <p>Trả về nguyên khối JSON bọc ngoài của PayOS ({@code code}, {@code desc}, {@code data},
 * {@code signature}) chứ không bóc sẵn: phần {@code code} khác "00" mang thông tin mà bên gọi
 * cần để phân biệt "đơn đã tồn tại" với "sai khoá".
 */
public interface PayOsApi {

    /**
     * @param method  GET hoặc POST
     * @param path    đường dẫn tính từ gốc máy chủ, ví dụ {@code /v2/payment-requests}
     * @param jsonBody thân yêu cầu, null nếu không có
     * @return khối JSON bọc ngoài PayOS trả về
     * @throws com.fastfood.common.exception.AppException.BusinessException khi không gọi tới
     *         nơi hoặc câu trả lời không phải JSON
     */
    JsonObject send(String method, String path, String jsonBody);
}
