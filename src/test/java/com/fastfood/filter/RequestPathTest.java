package com.fastfood.filter;

import com.fastfood.testsupport.FakeHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Đường dẫn dùng chung cho các bộ lọc")
class RequestPathTest {

    @Test
    @DisplayName("Servlet ánh xạ chính xác: đường dẫn đúng bằng servletPath")
    void exactMapping() {
        assertEquals("/login", RequestPath.of(FakeHttp.request("/login").build()));
    }

    @Test
    @DisplayName("Servlet ánh xạ dạng tiền tố: ghép lại đủ cả hai mảnh")
    void prefixMappingJoinsBothParts() {
        assertEquals("/kitchen/queue/item",
                RequestPath.of(FakeHttp.request("/kitchen", "/queue/item").build()));
    }

    @Test
    @DisplayName("Trang chủ trả về dấu chéo, không phải chuỗi rỗng")
    void rootIsASlash() {
        assertEquals("/", RequestPath.of(FakeHttp.request("/").build()));
        assertEquals("/", RequestPath.of(FakeHttp.request("").build()),
                "Chuoi rong khong khop duoc voi muc \"/\" trong danh sach trang cong khai");
    }

    @Test
    @DisplayName("Tài nguyên tĩnh do máy chủ phục vụ vẫn ra đúng đường dẫn")
    void staticAssetPath() {
        assertEquals("/assets/css/main.css",
                RequestPath.of(FakeHttp.request("/assets/css/main.css").build()));
    }
}
