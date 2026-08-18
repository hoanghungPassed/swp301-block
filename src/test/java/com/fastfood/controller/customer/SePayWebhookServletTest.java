package com.fastfood.controller.customer;

import com.fastfood.integration.payment.SePayGateway;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Webhook SePay đọc dữ liệu gửi tới")
class SePayWebhookServletTest {

    private final SePayGateway gateway =
            new SePayGateway("0011223344", "Vietcombank", "CUA HANG ABC", "khoa-abc", "FF");

    @Nested
    @DisplayName("Bóc khoá API từ header Authorization")
    class BocKhoa {

        @Test
        @DisplayName("Dạng chuẩn SePay gửi: Apikey <khoá>")
        void standardScheme() {
            assertEquals("khoa-abc",
                    SePayWebhookServlet.apiKeyFromHeader("Apikey khoa-abc"));
        }

        @Test
        @DisplayName("Tên lược đồ viết hoa thường kiểu gì cũng nhận")
        void schemeIsCaseInsensitive() {
            assertEquals("khoa-abc", SePayWebhookServlet.apiKeyFromHeader("APIKEY khoa-abc"));
            assertEquals("khoa-abc", SePayWebhookServlet.apiKeyFromHeader("apikey khoa-abc"));
            assertEquals("khoa-abc", SePayWebhookServlet.apiKeyFromHeader("  Apikey   khoa-abc  "));
        }

        @Test
        @DisplayName("Khoá gửi trần, không có tên lược đồ, cũng nhận")
        void bareKeyIsAccepted() {
            assertEquals("khoa-abc", SePayWebhookServlet.apiKeyFromHeader("khoa-abc"));
        }

        @Test
        @DisplayName("Không có header thì không có khoá — và cổng sẽ từ chối")
        void missingHeaderYieldsNothing() {
            assertNull(SePayWebhookServlet.apiKeyFromHeader(null));
        }
    }

    @Nested
    @DisplayName("Tìm mã thanh toán trong dữ liệu gửi tới")
    class TimMa {

        private static final String PAYLOAD = """
                {
                  "id": 92704,
                  "gateway": "Vietcombank",
                  "transactionDate": "2024-07-02 11:08:33",
                  "accountNumber": "0011223344",
                  "subAccount": "",
                  "code": null,
                  "content": "SEVN63DC8E5C FF57 chuyen tien",
                  "transferType": "in",
                  "description": "NGUYEN VAN A chuyen tien",
                  "transferAmount": 150000,
                  "accumulated": 105000000,
                  "referenceCode": "FT24012345678"
                }""";

        @Test
        @DisplayName("Đọc được từ nội dung chuyển khoản")
        void readsFromContent() {
            assertEquals(57, SePayWebhookServlet.firstPaymentId(gateway, json(PAYLOAD)));
        }

        @Test
        @DisplayName("Mã nằm ở trường code cũng tìm ra")
        void fallsBackToCode() {
            String chiCoOCode = PAYLOAD
                    .replace("\"code\": null", "\"code\": \"FF57\"")
                    .replace("SEVN63DC8E5C FF57 chuyen tien", "SEVN63DC8E5C")
                    .replace("NGUYEN VAN A chuyen tien", "NGUYEN VAN A");

            assertEquals(57, SePayWebhookServlet.firstPaymentId(gateway, json(chiCoOCode)));
        }

        @Test
        @DisplayName("Trường rỗng không chặn mất các trường còn lại")
        void nullFieldsDoNotStopTheSearch() {
            assertTrue(json(PAYLOAD).get("code").isJsonNull(),
                    "Chuẩn bị: trường code trong dữ liệu mẫu phải rỗng, để bài readsFromContent "
                            + "thật sự chứng minh việc đi tiếp qua trường rỗng");
        }

        @Test
        @DisplayName("Dữ liệu không mang mã nào thì trả về rỗng, không phải lỗi")
        void unrelatedTransferYieldsNothing() {
            String khongLienQuan = PAYLOAD
                    .replace("SEVN63DC8E5C FF57 chuyen tien", "NGUYEN VAN B tra tien hang")
                    .replace("NGUYEN VAN A chuyen tien", "NGUYEN VAN B tra tien hang");

            assertNull(SePayWebhookServlet.firstPaymentId(gateway, json(khongLienQuan)),
                    "Tài khoản của cửa hàng còn nhận cả những khoản không liên quan tới đơn nào");
        }

        @Test
        @DisplayName("Mã nằm ở trường mô tả cũng tìm ra")
        void fallsBackToDescription() {
            String chiCoOMoTa = PAYLOAD
                    .replace("SEVN63DC8E5C FF57 chuyen tien", "SEVN63DC8E5C")
                    .replace("NGUYEN VAN A chuyen tien", "NGUYEN VAN A chuyen tien FF57");

            assertEquals(57, SePayWebhookServlet.firstPaymentId(gateway, json(chiCoOMoTa)));
        }

        private static JsonObject json(String raw) {
            return JsonParser.parseString(raw).getAsJsonObject();
        }
    }
}
