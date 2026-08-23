package com.fastfood.integration.payment;

import com.fastfood.common.exception.AppException.BusinessException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Lời gọi HTTP thật tới PayOS.
 *
 * <p>Dùng {@link HttpClient} có sẵn từ Java 11, không thêm thư viện nào: một cổng thanh toán
 * chỉ cần POST và GET vài đường dẫn JSON.
 *
 * <p>Hai hạn thời gian đều ngắn có chủ ý. Lời gọi này nằm TRÊN đường khách bấm nút trả tiền,
 * nên PayOS chậm là khách ngồi nhìn trang trắng; thà hỏng sau mươi giây kèm một câu đọc được
 * còn hơn treo cho tới lúc Tomcat tự cắt.
 */
public class HttpPayOsApi implements PayOsApi {

    private static final Logger LOG = Logger.getLogger(HttpPayOsApi.class.getName());

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final String clientId;
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;

    public HttpPayOsApi(String clientId, String apiKey, String baseUrl) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String url = baseUrl == null || baseUrl.isBlank()
                ? PayOsGateway.BASE_URL : baseUrl.trim();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.http = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                /* Không tự đi theo chuyển hướng: API trả JSON, một lời đáp 3xx nghĩa là địa chỉ
                   sai chứ không phải nội dung nằm chỗ khác — đi theo chỉ làm lỗi khó đọc hơn. */
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public JsonObject send(String method, String path, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json");

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else {
            builder.method(method.toUpperCase(),
                    jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                                     : HttpRequest.BodyPublishers.ofString(jsonBody));
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            LOG.severe("Khong goi duoc PayOS " + method + " " + path + ": " + e.getMessage());
            throw new BusinessException("Không kết nối được tới cổng thanh toán PayOS. "
                    + "Vui lòng thử lại sau ít phút.");
        } catch (InterruptedException e) {
            /* Đặt lại cờ ngắt rồi mới ném: nuốt cờ đi thì luồng bị ngắt vẫn chạy tiếp như
               không có chuyện gì, và Tomcat mất đường dừng luồng lúc tắt máy chủ. */
            Thread.currentThread().interrupt();
            throw new BusinessException("Lời gọi tới cổng thanh toán PayOS bị ngắt giữa chừng.");
        }

        /* Thân JSON đọc trước, mã HTTP xét sau: PayOS trả lý do thật trong trường desc, kể cả
           khi mã HTTP là 4xx. Bỏ thân đi rồi chỉ báo "lỗi 400" là vứt mất câu trả lời. */
        JsonObject envelope = parse(response.body());
        if (envelope == null) {
            LOG.severe("PayOS " + method + " " + path + " tra ve HTTP " + response.statusCode()
                    + " khong phai JSON: " + tomTat(response.body()));
            throw new BusinessException("Cổng thanh toán PayOS trả về dữ liệu không đọc được "
                    + "(HTTP " + response.statusCode() + ").");
        }
        return envelope;
    }

    private static JsonObject parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    /** Cắt ngắn thân lời đáp trước khi ghi log: một trang lỗi HTML có thể dài vài chục nghìn ký tự. */
    private static String tomTat(String body) {
        if (body == null) {
            return "(rong)";
        }
        String s = body.strip().replaceAll("\\s+", " ");
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
