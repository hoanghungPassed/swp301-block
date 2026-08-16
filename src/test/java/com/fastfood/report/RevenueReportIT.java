package com.fastfood.report;

import com.fastfood.model.dto.DashboardKpi;
import com.fastfood.model.dto.ReportRow;
import com.fastfood.service.admin.ReportService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doanh thu thuần — bài test quan trọng nhất của cả bộ, vì đây là chỗ từng sai.
 * <p>
 * <b>Lỗi cũ:</b> vế thu lọc theo {@code payment_status = 'PAID'}. Bảng Payment chỉ có một cột
 * trạng thái và hoàn tiền ghi đè PAID thành REFUNDED, nên một khoản đã thu rồi hoàn lại sẽ
 * biến mất khỏi vế thu nhưng vẫn nằm ở vế hoàn — bị trừ hai lần. Đơn 100.000đ thu rồi hoàn
 * cho ra <b>âm</b> 100.000đ thay vì bằng không.
 * <p>
 * <b>Cách đúng:</b> mỗi vế đếm theo mốc thời gian của chính nó — thu theo {@code paid_at},
 * hoàn theo {@code refunded_at} — và không đụng tới {@code payment_status}.
 * <p>
 * Mọi dữ liệu của lớp này nằm ở năm 2031 để không lẫn với dữ liệu mẫu.
 */
@DisplayName("Doanh thu thuần tính đúng khi có hoàn tiền")
class RevenueReportIT extends IntegrationTestBase {

    /** Khoảng thời gian báo cáo dùng trong hầu hết các bài dưới đây. */
    private static final LocalDateTime WINDOW_FROM = LocalDateTime.of(2031, 3, 1, 0, 0);
    private static final LocalDateTime WINDOW_TO = LocalDateTime.of(2031, 3, 31, 23, 59, 59);

    private static final BigDecimal AMOUNT = new BigDecimal("100000.00");

    private final ReportService reportService = new ReportService();

    @BeforeEach
    void clearFutureData() {
        // Chỉ dọn dữ liệu của chính lớp này. Payment không xoá được (trigger chặn hard-delete),
        // nên đẩy các mốc thời gian ra khỏi mọi khoảng báo cáo mà bài test quan tâm.
        exec("UPDATE dbo.Payment SET paid_at = NULL, refunded_at = NULL " +
             "WHERE order_id IN (SELECT order_id FROM dbo.Orders WHERE created_at >= ?)",
             LocalDateTime.of(2031, 1, 1, 0, 0));
    }

    // ------------------------------------------------------------------ các bài test

    @Test
    @DisplayName("Thu rồi hoàn trong cùng kỳ: doanh thu bằng 0, không phải số âm")
    void paidAndRefundedInSamePeriod_netsToZero() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 3, 10, 12, 0),   // thu
                LocalDateTime.of(2031, 3, 12, 9, 0));   // hoàn, vẫn trong kỳ

        DashboardKpi kpi = reportService.loadKpi(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, AMOUNT.compareTo(kpi.getGrossRevenue()),
                "Tiền đã thu phải được ghi nhận đủ, kể cả khi sau đó hoàn lại");
        assertEquals(0, AMOUNT.compareTo(kpi.getRefundedAmount()),
                "Khoản hoàn phải được ghi nhận đúng một lần");
        assertEquals(0, BigDecimal.ZERO.compareTo(kpi.getNetRevenue()),
                "Thu 100.000 rồi hoàn 100.000 thì doanh thu thuần phải bằng 0. "
                + "Ra âm 100.000 nghĩa là lỗi trừ hai lần đã quay lại.");
    }

    @Test
    @DisplayName("Doanh thu thuần không bao giờ âm chỉ vì có hoàn tiền trong kỳ")
    void netRevenueNeverGoesNegativeFromItsOwnRefund() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 3, 5, 8, 0),
                LocalDateTime.of(2031, 3, 5, 18, 0));

        BigDecimal net = reportService.loadKpi(WINDOW_FROM, WINDOW_TO).getNetRevenue();

        assertTrue(net.signum() >= 0,
                "Doanh thu thuần ra " + net + " — một khoản thu rồi hoàn trong cùng kỳ "
                + "không được kéo doanh thu xuống dưới 0");
    }

    @Test
    @DisplayName("Thu kỳ này, hoàn kỳ sau: kỳ này ghi nhận đủ doanh thu")
    void refundInLaterPeriod_doesNotReduceEarlierPeriod() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 3, 20, 10, 0),   // thu trong kỳ
                LocalDateTime.of(2031, 4, 5, 10, 0));   // hoàn sang kỳ sau

        DashboardKpi kpi = reportService.loadKpi(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, AMOUNT.compareTo(kpi.getGrossRevenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(kpi.getRefundedAmount()),
                "Khoản hoàn của tháng sau không được tính vào tháng này");
        assertEquals(0, AMOUNT.compareTo(kpi.getNetRevenue()),
                "Tháng này đã thu thật thì phải ghi nhận đủ; tháng sau mới bị trừ");
    }

    @Test
    @DisplayName("Thu kỳ trước, hoàn kỳ này: kỳ này chỉ chịu khoản hoàn")
    void refundLandsInThePeriodItHappened() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 2, 10, 10, 0),   // thu kỳ trước
                LocalDateTime.of(2031, 3, 15, 10, 0));  // hoàn trong kỳ này

        DashboardKpi kpi = reportService.loadKpi(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, BigDecimal.ZERO.compareTo(kpi.getGrossRevenue()),
                "Không thu đồng nào trong kỳ này");
        assertEquals(0, AMOUNT.compareTo(kpi.getRefundedAmount()));
        assertEquals(0, AMOUNT.negate().compareTo(kpi.getNetRevenue()),
                "Kỳ chỉ có hoàn tiền thì doanh thu thuần âm — đúng, vì tiền đã ghi nhận ở kỳ trước");
    }

    @Test
    @DisplayName("Tổng hai kỳ liền nhau khớp với tổng cả giai đoạn")
    void periodsAddUp() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 3, 20, 10, 0),
                LocalDateTime.of(2031, 4, 5, 10, 0));

        BigDecimal march = reportService.loadKpi(WINDOW_FROM, WINDOW_TO).getNetRevenue();
        BigDecimal april = reportService.loadKpi(
                LocalDateTime.of(2031, 4, 1, 0, 0),
                LocalDateTime.of(2031, 4, 30, 23, 59, 59)).getNetRevenue();
        BigDecimal both = reportService.loadKpi(
                WINDOW_FROM,
                LocalDateTime.of(2031, 4, 30, 23, 59, 59)).getNetRevenue();

        assertEquals(0, march.add(april).compareTo(both),
                "Cộng từng kỳ (" + march + " + " + april + ") phải bằng tính gộp (" + both + ")");
    }

    @Test
    @DisplayName("Biểu đồ theo ngày: ngày thu cộng vào, ngày hoàn trừ ra")
    void revenueByDay_splitsAcrossTwoDays() {
        paidOrder(AMOUNT,
                LocalDateTime.of(2031, 3, 8, 11, 0),
                LocalDateTime.of(2031, 3, 9, 11, 0));

        List<ReportRow> rows = reportService.revenueByDay(WINDOW_FROM, WINDOW_TO);

        BigDecimal day8 = amountOn(rows, "2031-03-08");
        BigDecimal day9 = amountOn(rows, "2031-03-09");

        assertEquals(0, AMOUNT.compareTo(day8), "Ngày thu tiền phải là số dương");
        assertEquals(0, AMOUNT.negate().compareTo(day9), "Ngày hoàn tiền phải là số âm");
    }

    @Test
    @DisplayName("Khoản chưa từng thu không lọt vào doanh thu")
    void unpaidAttemptsAreIgnored() {
        int orderId = posOrder(AMOUNT);
        // Một lần thử thất bại: có bản ghi Payment nhưng chưa bao giờ có paid_at
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, created_at) " +
             "VALUES (?, 'CASH', ?, 'FAILED', 1, ?)",
             orderId, AMOUNT, LocalDateTime.of(2031, 3, 11, 10, 0));

        DashboardKpi kpi = reportService.loadKpi(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, BigDecimal.ZERO.compareTo(kpi.getGrossRevenue()),
                "Lần thanh toán thất bại không phải doanh thu");
    }

    // ------------------------------------------------------------------ dựng dữ liệu

    /** Một đơn tại quầy đã thu tiền, với mốc thu và mốc hoàn do bài test chỉ định. */
    private void paidOrder(BigDecimal amount, LocalDateTime paidAt, LocalDateTime refundedAt) {
        int orderId = posOrder(amount);
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, " +
             "created_at, paid_at, refunded_at) VALUES (?, 'CASH', ?, ?, 1, ?, ?, ?)",
             orderId, amount,
             refundedAt == null ? "PAID" : "REFUNDED",
             paidAt, paidAt, refundedAt);
    }

    /** Đơn tại quầy tối thiểu, đặt ở năm 2031 để không lẫn với dữ liệu mẫu. */
    private int posOrder(BigDecimal amount) {
        exec("INSERT INTO dbo.Orders (customer_id, created_by_user_id, order_source, total_amount, " +
             "order_status, created_at, completed_at) VALUES (NULL, ?, 'POS', ?, 'COMPLETED', ?, ?)",
             userId(CASHIER_1), amount,
             LocalDateTime.of(2031, 3, 1, 8, 0), LocalDateTime.of(2031, 3, 1, 8, 5));
        Integer id = scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");
        exec("INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, " +
             "quantity, item_status) VALUES (?, ?, N'Mon test', ?, 1, 'READY')",
             id, anyOrderableProductId(), amount);
        return id;
    }

    private BigDecimal amountOn(List<ReportRow> rows, String day) {
        return rows.stream()
                .filter(r -> day.equals(r.getLabel()))
                .map(ReportRow::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
