package com.fastfood.report;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.RevenueTarget;
import com.fastfood.service.admin.ReportService;
import com.fastfood.service.admin.RevenueTargetService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chỉ tiêu doanh thu trên bảng điều khiển của quản trị viên.
 * <p>
 * Các bài về mức đạt dùng mốc thời gian <b>năm 2020</b>: xa hẳn mọi khoảng mặc định của báo cáo
 * (14 và 30 ngày gần nhất) nên khoản tiền dựng thêm ở đây không lọt vào con số của bài test khác.
 */
@DisplayName("Chỉ tiêu doanh thu")
class RevenueTargetIT extends IntegrationTestBase {

    private final RevenueTargetService targetService = new RevenueTargetService();
    private final ReportService reportService = new ReportService();

    private static final LocalDate NGAY_A = LocalDate.of(2020, 1, 15);
    private static final LocalDate NGAY_B = LocalDate.of(2020, 1, 16);

    /**
     * Một khoản tiền mặt đã thu, gắn vào một đơn <b>tại quầy</b>.
     * <p>
     * Phải là đơn tại quầy: trigger BR-04 chặn thu tiền mặt cho đơn đặt trước, và lấy bừa đơn
     * đầu tiên trong bảng thì rơi trúng một đơn đặt trước. Bảng Payment bị trigger chặn xoá nên
     * không dọn lại được — đó là lý do mốc thời gian đặt tận năm 2020.
     */
    private void thuTien(BigDecimal amount, LocalDateTime paidAt, int attemptNo) {
        Integer orderId = scalar(Integer.class,
                "SELECT TOP 1 order_id FROM dbo.Orders WHERE order_source = 'POS' ORDER BY order_id");
        exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, " +
             "created_at, paid_at) VALUES (?, 'CASH', ?, 'PAID', ?, ?, ?)",
             orderId, amount, attemptNo, java.sql.Timestamp.valueOf(paidAt),
             java.sql.Timestamp.valueOf(paidAt));
    }

    @Nested
    @DisplayName("Vòng đời một chỉ tiêu")
    class Crud {

        @Test
        @DisplayName("Đặt, sửa, xoá — mỗi bước để lại một dòng nhật ký")
        void fullCycle() {
            int admin = userId(ADMIN);
            RevenueTarget target = targetService.create(admin, "MONTH", LocalDate.of(2020, 3, 1),
                    new BigDecimal("100000000"), "  chỉ tiêu quý một  ");
            int id = target.getTargetId();
            assertEquals("chỉ tiêu quý một", target.getNote(), "Phải cắt khoảng trắng thừa");
            assertEquals(1, count("SELECT COUNT(*) FROM dbo.AuditLog WHERE entity_type = 'REVENUE_TARGET' " +
                    "AND entity_id = ? AND action = ?", String.valueOf(id), AuditAction.TARGET_CREATED));

            targetService.update(admin, id, new BigDecimal("120000000"), "nâng chỉ tiêu");
            RevenueTarget after = targetService.findById(id);
            assertEquals(0, new BigDecimal("120000000").compareTo(after.getTargetAmount()));
            assertTrue(after.isEdited());

            targetService.delete(admin, id);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.RevenueTarget WHERE target_id = ?", id));
        }

        @Test
        @DisplayName("Xoá rồi thì con số cũ vẫn còn trong nhật ký — đó là lý do bảng này xoá hẳn được")
        void deleteKeepsOldValueInAudit() {
            int admin = userId(ADMIN);
            int id = targetService.create(admin, "DAY", LocalDate.of(2020, 4, 2),
                    new BigDecimal("7000000"), null).getTargetId();

            targetService.delete(admin, id);

            String so_cu = text("SELECT old_value FROM dbo.AuditLog WHERE entity_type = 'REVENUE_TARGET' " +
                    "AND entity_id = ? AND action = ?", String.valueOf(id), AuditAction.TARGET_DELETED);
            assertNotNull(so_cu, "Thiếu dòng TARGET_DELETED thì chỉ tiêu cũ biến mất không dấu vết");
            assertEquals(0, new BigDecimal("7000000").compareTo(new BigDecimal(so_cu)));
        }

        @Test
        @DisplayName("Kỳ tháng luôn quy về ngày mùng 1, chọn ngày nào trong tháng cũng vậy")
        void monthAlwaysStartsOnTheFirst() {
            int admin = userId(ADMIN);

            RevenueTarget target = targetService.create(admin, "MONTH", LocalDate.of(2020, 5, 27),
                    new BigDecimal("90000000"), null);

            assertEquals(LocalDate.of(2020, 5, 1), target.getPeriodStart(),
                    "Không quy về thì hai chỉ tiêu 'tháng 5' đặt lệch ngày sẽ cùng tồn tại");
            assertEquals("05/2020", target.getPeriodLabel());
        }

        @Test
        @DisplayName("Mỗi kỳ đúng một chỉ tiêu")
        void onePerPeriod() {
            int admin = userId(ADMIN);
            targetService.create(admin, "MONTH", LocalDate.of(2020, 6, 1),
                    new BigDecimal("80000000"), null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> targetService.create(admin, "MONTH", LocalDate.of(2020, 6, 15),
                            new BigDecimal("85000000"), null));

            assertTrue(e.getMessage().contains("đã có chỉ tiêu"), "Nhận được: " + e.getMessage());
            assertEquals(1, count("SELECT COUNT(*) FROM dbo.RevenueTarget " +
                    "WHERE period_type = 'MONTH' AND period_start = ?", java.sql.Date.valueOf(LocalDate.of(2020, 6, 1))));
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Số tiền không dương và kỳ lạ đều bị từ chối")
        void invalidInput() {
            int admin = userId(ADMIN);
            LocalDate ngay = LocalDate.of(2020, 7, 1);

            assertThrows(ValidationException.class,
                    () -> targetService.create(admin, "MONTH", ngay, BigDecimal.ZERO, null));
            assertThrows(ValidationException.class,
                    () -> targetService.create(admin, "MONTH", ngay, new BigDecimal("-5"), null));
            assertThrows(ValidationException.class,
                    () -> targetService.create(admin, "MONTH", ngay, null, null));
            assertThrows(ValidationException.class,
                    () -> targetService.create(admin, "QUY", ngay, new BigDecimal("1000"), null));
            assertThrows(ValidationException.class,
                    () -> targetService.create(admin, "MONTH", null, new BigDecimal("1000"), null));

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.RevenueTarget WHERE period_start = ?",
                    java.sql.Date.valueOf(ngay)));
        }

        @Test
        @DisplayName("Sửa hoặc xoá chỉ tiêu không tồn tại thì báo không tìm thấy")
        void missingTarget() {
            int admin = userId(ADMIN);
            assertThrows(NotFoundException.class,
                    () -> targetService.update(admin, 999_999, new BigDecimal("1000"), null));
            assertThrows(NotFoundException.class, () -> targetService.delete(admin, 999_999));
            assertThrows(NotFoundException.class, () -> targetService.findById(999_999));
        }
    }

    @Nested
    @DisplayName("Mức đã đạt")
    class Achieved {

        @Test
        @DisplayName("Dùng đúng doanh thu thuần của báo cáo, không tự tính công thức thứ hai")
        void reusesReportNetRevenue() {
            int admin = userId(ADMIN);
            LocalDate dau_thang = LocalDate.of(2020, 2, 1);
            RevenueTarget target = targetService.create(admin, "MONTH", dau_thang,
                    new BigDecimal("50000000"), null);

            RevenueTarget doc_lai = targetService.recent().stream()
                    .filter(t -> t.getTargetId() == target.getTargetId()).findFirst().orElseThrow();

            BigDecimal tu_bao_cao = reportService.loadKpi(
                    dau_thang.atStartOfDay(),
                    dau_thang.plusMonths(1).atStartOfDay().minusSeconds(1)).getNetRevenue();
            assertEquals(0, tu_bao_cao.compareTo(doc_lai.getAchieved()),
                    "Hai con số trên cùng màn hình mà lệch nhau thì không ai biết tin cái nào");
        }

        @Test
        @DisplayName("Khoản thu đúng 00:00:00 ngày hôm sau thuộc về kỳ sau, không tính cho cả hai")
        void periodBoundaryDoesNotOverlap() {
            int admin = userId(ADMIN);
            thuTien(new BigDecimal("1000000"), NGAY_A.atTime(12, 0), 91);
            thuTien(new BigDecimal("2000000"), NGAY_B.atStartOfDay(), 92);

            targetService.create(admin, "DAY", NGAY_A, new BigDecimal("4000000"), null);
            targetService.create(admin, "DAY", NGAY_B, new BigDecimal("4000000"), null);

            BigDecimal ngay_a = targetService.recent().stream()
                    .filter(t -> NGAY_A.equals(t.getPeriodStart())).findFirst().orElseThrow().getAchieved();
            BigDecimal ngay_b = targetService.recent().stream()
                    .filter(t -> NGAY_B.equals(t.getPeriodStart())).findFirst().orElseThrow().getAchieved();

            assertEquals(0, new BigDecimal("1000000").compareTo(ngay_a),
                    "Báo cáo lọc bằng BETWEEN nên lấy thẳng 00:00 ngày sau làm mốc cuối "
                            + "sẽ đếm khoản đó vào cả hai kỳ");
            assertEquals(0, new BigDecimal("2000000").compareTo(ngay_b));
        }

        @Test
        @DisplayName("Phần trăm, phần còn thiếu và cờ đã đạt tính đúng cả khi vượt chỉ tiêu")
        void percentAndRemaining() {
            RevenueTarget t = new RevenueTarget();
            t.setTargetAmount(new BigDecimal("1000000"));

            t.setAchieved(new BigDecimal("250000"));
            assertEquals(25, t.getAchievedPercent());
            assertEquals(0, new BigDecimal("750000").compareTo(t.getRemaining()));
            assertFalse(t.isReached());

            t.setAchieved(new BigDecimal("1500000"));
            assertEquals(150, t.getAchievedPercent());
            assertEquals(0, BigDecimal.ZERO.compareTo(t.getRemaining()),
                    "Vượt chỉ tiêu thì phần còn thiếu là 0, không phải số âm");
            assertTrue(t.isReached());

            t.setTargetAmount(BigDecimal.ZERO);
            assertEquals(0, t.getAchievedPercent(), "Không được chia cho 0");
        }

        @Test
        @DisplayName("Dữ liệu mẫu có sẵn chỉ tiêu tháng và chỉ tiêu ngày")
        void seedHasTargets() {
            assertNotNull(targetService.currentMonth(), "Thiếu chỉ tiêu tháng thì ô đầu trang trống");
            assertNotNull(targetService.today());
            assertNotNull(targetService.currentMonth().getAchieved(),
                    "Chỉ tiêu đọc ra phải kèm sẵn mức đã đạt");
        }
    }
}
