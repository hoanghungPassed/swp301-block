package com.fastfood.common;

import com.fastfood.common.constant.OrderSource;
import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.model.dto.DashboardKpi;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Các phép tính nhỏ mà sai thì cả con số hiển thị ra sai theo.
 */
@DisplayName("Phép tính nghiệp vụ")
class BusinessMathTest {

    @Nested
    @DisplayName("Thành tiền của một dòng món")
    class LineTotal {

        @Test
        @DisplayName("Nhân đúng đơn giá với số lượng")
        void multipliesPriceByQuantity() {
            assertEquals(0, new BigDecimal("165000").compareTo(item("55000", 3).getLineTotal()));
        }

        @Test
        @DisplayName("Giữ nguyên hai chữ số thập phân, không dùng số thực")
        void keepsExactDecimals() {
            BigDecimal total = item("33333.33", 3).getLineTotal();
            assertEquals(0, new BigDecimal("99999.99").compareTo(total),
                    "Dùng double thì cộng dồn doanh thu cả tháng sẽ lệch dần");
        }

        private OrderItem item(String price, int qty) {
            OrderItem i = new OrderItem();
            i.setUnitPrice(new BigDecimal(price));
            i.setQuantity(qty);
            return i;
        }
    }

    @Nested
    @DisplayName("Tỷ lệ món sẵn sàng đúng hẹn")
    class OnTimeRate {

        @Test
        @DisplayName("Tính đúng phần trăm")
        void computesPercentage() {
            DashboardKpi kpi = new DashboardKpi();
            kpi.setTotalReadyMeasured(4);
            kpi.setOnTimeReadyCount(3);
            assertEquals(75.0, kpi.getOnTimeReadyRate(), 0.001);
        }

        @Test
        @DisplayName("Chưa có đơn nào đo được thì trả 0, không chia cho 0")
        void handlesNoData() {
            assertEquals(0.0, new DashboardKpi().getOnTimeReadyRate(), 0.001,
                    "Chia cho 0 sẽ làm hỏng cả trang bảng điều khiển");
        }

        @Test
        @DisplayName("Tổng số đơn là cộng của hai kênh")
        void totalOrdersIsSumOfChannels() {
            DashboardKpi kpi = new DashboardKpi();
            kpi.setOnlineOrderCount(7);
            kpi.setPosOrderCount(5);
            assertEquals(12, kpi.getTotalOrderCount());
        }
    }

    @Nested
    @DisplayName("Phân trang")
    class Paging {

        @Test
        @DisplayName("Số trang làm tròn lên")
        void roundsUpPageCount() {
            assertEquals(3, page(1, 20, 41).getTotalPages());
            assertEquals(2, page(1, 20, 40).getTotalPages());
        }

        @Test
        @DisplayName("Không có bản ghi nào thì vẫn là một trang")
        void emptyResultStillHasOnePage() {
            assertEquals(1, page(1, 20, 0).getTotalPages());
            assertFalse(page(1, 20, 0).isPaged(), "Một trang duy nhất thì không vẽ thanh chuyển trang");
        }

        @Test
        @DisplayName("Nói đúng đang xem từ dòng nào tới dòng nào")
        void reportsVisibleRange() {
            Page<String> p = new Page<>(List.of("a", "b", "c"), 3, 20, 214);
            assertEquals(41, p.getFirstIndex());
            assertEquals(43, p.getLastIndex(),
                    "Đây chính là điều mà cách cắt cứng bằng TOP(n) không nói ra");
        }

        @Test
        @DisplayName("Số trang do người dùng gõ tay được ép về khoảng hợp lệ")
        void clampsUserSuppliedPageNumber() {
            assertEquals(1, Page.safePage(0));
            assertEquals(1, Page.safePage(-5),
                    "Số âm lọt xuống OFFSET của câu lệnh SQL là lỗi ngay tại cơ sở dữ liệu");
            assertEquals(7, Page.safePage(7));
        }

        @Test
        @DisplayName("Không lùi trước trang đầu, không vượt quá trang cuối")
        void neighboursStayInRange() {
            assertEquals(1, page(1, 20, 100).getPrevPage());
            assertEquals(5, page(5, 20, 100).getNextPage());
            assertTrue(page(1, 20, 100).isFirst());
            assertTrue(page(5, 20, 100).isLast());
        }

        private Page<String> page(int no, int size, long total) {
            return new Page<>(List.of(), no, size, total);
        }
    }

    @Nested
    @DisplayName("Hình thức thanh toán theo kênh bán (BR-04)")
    class PaymentRules {

        @Test
        @DisplayName("Đơn đặt trước chỉ nhận thanh toán trực tuyến")
        void onlineOrderRejectsCash() {
            assertFalse(PaymentMethod.CASH.isAllowedFor(OrderSource.ONLINE_PREORDER),
                    "Cho phép trả tiền mặt cho đơn đặt trước là mở đường cho khách đặt rồi không tới");
            assertTrue(PaymentMethod.ONLINE_GATEWAY.isAllowedFor(OrderSource.ONLINE_PREORDER));
        }

        @Test
        @DisplayName("Đơn tại quầy nhận cả hai hình thức")
        void posAcceptsBoth() {
            assertTrue(PaymentMethod.CASH.isAllowedFor(OrderSource.POS));
            assertTrue(PaymentMethod.ONLINE_GATEWAY.isAllowedFor(OrderSource.POS));
        }
    }
}
