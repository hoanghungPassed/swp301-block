package com.fastfood.flow;

import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Order;
import com.fastfood.service.customer.CustomerOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ lọc trên màn hình "Đơn của tôi".
 * <p>
 * Điều đáng kiểm nhất ở đây không phải là danh sách lọc ra đúng, mà là <b>câu đếm và câu lấy
 * dữ liệu dùng chung một mệnh đề lọc</b>. Hai bên lệch nhau thì trang vẫn hiện ra bình thường,
 * chỉ có dòng "đang xem 1–20 trong N" nói một con số của tập khác — và không ai nhận ra cho
 * tới khi bấm sang trang cuối rồi thấy trống trơn.
 * <p>
 * Kèm theo là chốt chặn cũ vẫn phải còn: mọi bộ lọc đều đi qua điều kiện customer_id.
 */
@DisplayName("Bộ lọc lịch sử đơn của khách")
class OrderHistoryFilterIT extends IntegrationTestBase {

    private final CustomerOrderService customerOrders = new CustomerOrderService();

    private Page<Order> loc(int khach, String trangThai, LocalDate tu, LocalDate den) {
        return customerOrders.historyOfCustomer(khach, trangThai, tu, den, 1);
    }

    @Nested
    @DisplayName("Lọc theo trạng thái")
    class ByStatus {

        @Test
        @DisplayName("Chỉ trả về đơn đúng trạng thái, và tổng số đếm theo cùng bộ lọc")
        void statusFilterMatchesCount() {
            int khach = userId(CUSTOMER_1);
            String trang_thai = text("SELECT TOP 1 order_status FROM dbo.Orders " +
                    "WHERE customer_id = ? GROUP BY order_status ORDER BY COUNT(*) DESC", khach);
            int that_su = count("SELECT COUNT(*) FROM dbo.Orders " +
                    "WHERE customer_id = ? AND order_status = ?", khach, trang_thai);

            Page<Order> trang = loc(khach, trang_thai, null, null);

            assertEquals(that_su, trang.getTotalItems(),
                    "Câu đếm và câu lấy dữ liệu phải dùng chung một mệnh đề lọc");
            assertTrue(trang.getItems().stream()
                            .allMatch(o -> trang_thai.equals(o.getOrderStatus())),
                    "Lọt một đơn khác trạng thái nghĩa là điều kiện không xuống tới SQL");
        }

        @Test
        @DisplayName("Trạng thái lạ trên địa chỉ được hiểu là không lọc, không phải một lỗi")
        void unknownStatusMeansNoFilter() {
            int khach = userId(CUSTOMER_1);
            long tat_ca = loc(khach, null, null, null).getTotalItems();

            Page<Order> trang = loc(khach, "KHONG_CO_THAT'; DROP TABLE dbo.Orders; --", null, null);

            assertEquals(tat_ca, trang.getTotalItems(),
                    "Tham số này chỉ sai khi có người sửa tay địa chỉ, và câu trả lời đúng cho "
                            + "việc đó là hiện toàn bộ lịch sử chứ không phải một trang lỗi");
            assertTrue(count("SELECT COUNT(*) FROM dbo.Orders") > 0, "Bảng Orders phải còn nguyên");
        }

        @Test
        @DisplayName("Trạng thái rỗng cũng là không lọc")
        void blankStatusMeansNoFilter() {
            int khach = userId(CUSTOMER_1);

            assertEquals(loc(khach, null, null, null).getTotalItems(),
                    loc(khach, "   ", null, null).getTotalItems());
        }
    }

    @Nested
    @DisplayName("Lọc theo khoảng ngày")
    class ByDate {

        @Test
        @DisplayName("Chọn đến hôm nay vẫn thấy đơn đặt hôm nay")
        void endDateCoversWholeDay() {
            int khach = userId(CUSTOMER_1);
            LocalDateTime moc = scalar(LocalDateTime.class,
                    "SELECT TOP 1 created_at FROM dbo.Orders WHERE customer_id = ? " +
                    "ORDER BY created_at DESC", khach);
            LocalDate ngay = moc.toLocalDate();

            Page<Order> trang = loc(khach, null, ngay, ngay);

            assertFalse(trang.isEmptyPage(),
                    "Ngày kết thúc phải mở tới cuối ngày. Lấy đúng 0 giờ thì chọn \"đến hôm nay\" "
                            + "lại bỏ sót trọn vẹn đơn đặt hôm nay");
            assertTrue(trang.getItems().stream()
                            .allMatch(o -> o.getCreatedAt().toLocalDate().equals(ngay)));
        }

        @Test
        @DisplayName("Khoảng ngày trong tương lai cho ra danh sách rỗng chứ không phải lỗi")
        void futureRangeIsEmpty() {
            LocalDate mai = LocalDate.now().plusDays(1);

            Page<Order> trang = loc(userId(CUSTOMER_1), null, mai, mai.plusDays(30));

            assertTrue(trang.isEmptyPage());
            assertEquals(0, trang.getTotalItems(),
                    "Trang rỗng phải kèm tổng số 0, để màn hình nói được \"không có đơn nào khớp "
                            + "bộ lọc\" thay vì \"bạn chưa có đơn hàng nào\"");
        }

        @Test
        @DisplayName("Hai đầu khoảng ngày lọc độc lập được")
        void openEndedRanges() {
            int khach = userId(CUSTOMER_1);
            LocalDate hom_nay = LocalDate.now();

            long tu_hom_nay = loc(khach, null, hom_nay, null).getTotalItems();
            long den_hom_nay = loc(khach, null, null, hom_nay).getTotalItems();
            long tat_ca = loc(khach, null, null, null).getTotalItems();

            assertTrue(tu_hom_nay <= tat_ca);
            assertTrue(den_hom_nay <= tat_ca);
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Mọi bộ lọc vẫn đi qua điều kiện customer_id")
        void filtersNeverLeakAnotherCustomer() {
            int khach = userId(CUSTOMER_1);

            List<Order> don = loc(khach, null, null, LocalDate.now().plusDays(1)).getItems();

            assertFalse(don.isEmpty(), "Dữ liệu mẫu phải có đơn cho khách này");
            assertTrue(don.stream().allMatch(o -> o.getCustomerId() != null
                            && o.getCustomerId() == khach),
                    "Điều kiện customer_id nằm ngoài phần lọc tuỳ chọn chính vì lý do này: "
                            + "thêm một bộ lọc mới không được phép làm mất nó");
        }

        @Test
        @DisplayName("Không lọc gì thì kết quả đúng bằng toàn bộ lịch sử của khách")
        void noFilterEqualsWholeHistory() {
            int khach = userId(CUSTOMER_1);

            assertEquals(count("SELECT COUNT(*) FROM dbo.Orders WHERE customer_id = ?", khach),
                    loc(khach, null, null, null).getTotalItems());
        }
    }
}
