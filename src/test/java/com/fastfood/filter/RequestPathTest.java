package com.fastfood.filter;

import com.fastfood.testsupport.FakeHttp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Đường dẫn mà cả ba bộ lọc cùng nhìn thấy.
 * <p>
 * Một dòng mã nhưng đáng có bài test riêng, vì nó là <b>nguồn duy nhất</b> quyết định mọi so
 * khớp về quyền: danh sách trang công khai, danh sách miễn trừ mã chống giả mạo, và bảng tiền
 * tố vai trò đều tra bằng chuỗi này. Sai ở đây thì cả ba sai theo, và sai theo những chiều
 * khác nhau — chỗ thì đóng thừa, chỗ thì mở nhầm.
 * <p>
 * Bài test nằm cùng gói vì {@link RequestPath} ở mức gói: nó là chi tiết chung của các bộ lọc,
 * không phải thứ tầng khác được gọi tới.
 */
@DisplayName("Đường dẫn dùng chung cho các bộ lọc")
class RequestPathTest {

    @Test
    @DisplayName("Servlet ánh xạ chính xác: đường dẫn đúng bằng servletPath")
    void exactMapping() {
        assertEquals("/login", RequestPath.of(FakeHttp.request("/login").build()));
    }

    /**
     * Với ánh xạ dạng tiền tố, máy chủ tách địa chỉ làm hai mảnh. Chỉ đọc mảnh đầu thì
     * {@code /kitchen/queue/item} rút gọn thành {@code /kitchen} — vẫn khớp tiền tố vai trò
     * nên không lộ ra, nhưng danh sách miễn trừ và danh sách công khai thì tra bằng chuỗi
     * đầy đủ, và ở đó chênh lệch này là chênh lệch về quyền.
     */
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
