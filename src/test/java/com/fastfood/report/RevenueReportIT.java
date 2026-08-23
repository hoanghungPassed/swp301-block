package com.fastfood.report;

import com.fastfood.model.dto.Dtos.DashboardKpi;
import com.fastfood.model.dto.Dtos.ReportRow;
import com.fastfood.service.admin.ReportService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Doanh thu cộng đúng theo kỳ và theo ngày")
class RevenueReportIT extends IntegrationTestBase {

    private static final LocalDateTime WINDOW_FROM = LocalDateTime.of(2031, 3, 1, 0, 0);
    private static final LocalDateTime WINDOW_TO = LocalDateTime.of(2031, 3, 31, 23, 59, 59);

    private static final BigDecimal AMOUNT = new BigDecimal("100000.00");

    private final ReportService reportService = new ReportService();

    @BeforeEach
    void clearFutureData() {
        exec("UPDATE dbo.Payment SET paid_at = NULL " +
             "WHERE order_id IN (SELECT order_id FROM dbo.Orders WHERE created_at >= ?)",
             LocalDateTime.of(2031, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("Khoản thu rơi vào đúng kỳ chứa paid_at, không phải kỳ lập đơn")
    void revenueLandsInThePeriodTheMoneyArrived() {
        paidOrder(AMOUNT, LocalDateTime.of(2031, 4, 5, 10, 0));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                        reportService.loadKpi(WINDOW_FROM, WINDOW_TO).getNetRevenue()),
                "Đơn lập tháng 3 nhưng tiền về tháng 4 thì tháng 3 không được tính — mốc doanh "
                + "thu là paid_at chứ không phải created_at");
    }

    @Test
    @DisplayName("Tổng hai kỳ liền nhau khớp với tổng cả giai đoạn")
    void periodsAddUp() {
        paidOrder(AMOUNT, LocalDateTime.of(2031, 3, 20, 10, 0));
        paidOrder(AMOUNT, LocalDateTime.of(2031, 4, 5, 10, 0));

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
    @DisplayName("Biểu đồ theo ngày gom khoản thu về đúng ngày của nó")
    void revenueByDay_groupsByPaidDate() {
        paidOrder(AMOUNT, LocalDateTime.of(2031, 3, 8, 11, 0));
        paidOrder(AMOUNT, LocalDateTime.of(2031, 3, 9, 11, 0));
        paidOrder(AMOUNT, LocalDateTime.of(2031, 3, 9, 19, 0));

        List<ReportRow> rows = reportService.revenueByDay(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, AMOUNT.compareTo(amountOn(rows, "2031-03-08")));
        assertEquals(0, AMOUNT.multiply(new BigDecimal("2")).compareTo(amountOn(rows, "2031-03-09")),
                "Hai lần thu trong cùng một ngày phải gộp thành một cột, không thành hai dòng");
    }

    @Test
    @DisplayName("Khoản chưa từng thu không lọt vào doanh thu")
    void unpaidAttemptsAreIgnored() {
        int orderId = posOrder(AMOUNT);
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, created_at) " +
             "VALUES (?, 'CASH', ?, 'FAILED', 1, ?)",
             orderId, AMOUNT, LocalDateTime.of(2031, 3, 11, 10, 0));

        DashboardKpi kpi = reportService.loadKpi(WINDOW_FROM, WINDOW_TO);

        assertEquals(0, BigDecimal.ZERO.compareTo(kpi.getNetRevenue()),
                "Lần thanh toán thất bại không phải doanh thu");
    }

    private void paidOrder(BigDecimal amount, LocalDateTime paidAt) {
        int orderId = posOrder(amount);
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, " +
             "created_at, paid_at) VALUES (?, 'CASH', ?, 'PAID', 1, ?, ?)",
             orderId, amount, paidAt, paidAt);
    }

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
