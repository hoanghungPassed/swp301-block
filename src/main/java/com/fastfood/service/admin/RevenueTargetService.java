package com.fastfood.service.admin;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.admin.RevenueTargetDAO;
import com.fastfood.model.entity.RevenueTarget;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chỉ tiêu doanh thu — con số để bảng điều khiển có cái mà đối chiếu.
 * <p>
 * <b>Vì sao chọn thực thể này cho màn hình bảng điều khiển.</b> Bảng điều khiển là màn hình duy
 * nhất của quản trị viên còn có chỗ cho một thực thể mới: nhật ký thao tác <b>phải</b> giữ nguyên
 * trạng chỉ đọc — một nhật ký kiểm toán mà quản trị viên sửa được thì không còn giá trị làm bằng
 * chứng, tức là mất đúng thứ nó sinh ra để làm.
 * <p>
 * <b>Chỉ tiêu không chạm vào đường đi của tiền.</b> Nó chỉ được đọc rồi đặt cạnh doanh thu thuần
 * đã tính sẵn. Đặt sai chỉ tiêu thì sai một dòng so sánh trên màn hình, không sai sổ sách — khác
 * hẳn với khuyến mãi hay mã giảm giá, những thứ phải chen vào giữa lúc lập đơn.
 * <p>
 * <b>Không tự tính lại doanh thu.</b> Con số đem ra so lấy từ {@link ReportService#loadKpi}, đúng
 * cái mà các ô số phía trên cùng màn hình đang hiện. Viết một câu tính doanh thu thứ hai ở đây
 * thì sớm muộn hai con số cạnh nhau trên cùng màn hình sẽ lệch nhau, và không ai biết tin cái nào.
 */
public class RevenueTargetService {

    private static final int HISTORY_LIMIT = 24;
    private static final int MAX_NOTE_LENGTH = 500;

    private final RevenueTargetDAO targetDAO = new RevenueTargetDAO();
    private final ReportService reportService = new ReportService();
    private final AuditService auditService = new AuditService();

    // ============================================================ đọc

    /** Danh sách chỉ tiêu kèm mức đã đạt của từng kỳ. */
    public List<RevenueTarget> recent() {
        List<RevenueTarget> list = Tx.read(con -> targetDAO.findRecent(con, HISTORY_LIMIT));
        for (RevenueTarget target : list) {
            fillAchieved(target);
        }
        return list;
    }

    public RevenueTarget findById(int targetId) {
        RevenueTarget target = Tx.read(con -> targetDAO.findById(con, targetId));
        if (target == null) {
            throw new NotFoundException("Không tìm thấy chỉ tiêu.");
        }
        return target;
    }

    /** Chỉ tiêu của tháng đang chạy, hoặc null nếu chưa đặt. */
    public RevenueTarget currentMonth() {
        LocalDate today = DateTimeUtil.now().toLocalDate();
        return findForPeriod("MONTH", today.withDayOfMonth(1));
    }

    /** Chỉ tiêu của hôm nay, hoặc null nếu chưa đặt. */
    public RevenueTarget today() {
        return findForPeriod("DAY", DateTimeUtil.now().toLocalDate());
    }

    private RevenueTarget findForPeriod(String periodType, LocalDate start) {
        RevenueTarget target = Tx.read(con -> targetDAO.findByPeriod(con, periodType, start));
        if (target != null) {
            fillAchieved(target);
        }
        return target;
    }

    /**
     * Gắn doanh thu thuần thật sự đạt được của kỳ vào chỉ tiêu.
     * <p>
     * Mốc cuối kỳ lùi lại <b>một giây</b> chứ không lấy thẳng đầu kỳ sau: báo cáo lọc bằng
     * {@code BETWEEN}, vốn tính cả hai đầu, nên lấy đúng 00:00:00 của ngày hôm sau sẽ kéo theo
     * mọi khoản thu rơi đúng giây đó vào cả hai kỳ. Cột thời gian là {@code DATETIME2(0)} nên
     * lùi một giây là chạm sát mép, không bỏ sót gì.
     */
    private void fillAchieved(RevenueTarget target) {
        LocalDateTime from = target.getPeriodStart().atStartOfDay();
        LocalDateTime to = (target.isMonthly()
                ? target.getPeriodStart().plusMonths(1)
                : target.getPeriodStart().plusDays(1)).atStartOfDay().minusSeconds(1);
        target.setAchieved(reportService.loadKpi(from, to).getNetRevenue());
    }

    // ============================================================ ghi

    /**
     * Đặt chỉ tiêu cho một kỳ.
     * <p>
     * Mỗi kỳ đúng một chỉ tiêu, và điều đó do {@code UX_Target_period} bảo đảm chứ không phải
     * một lượt đọc trước: hai tab cùng bấm lưu sẽ cùng đọc thấy "kỳ này chưa có chỉ tiêu".
     */
    public RevenueTarget create(int actorId, String periodType, LocalDate periodStart,
                                BigDecimal amount, String note) {
        String type = requirePeriodType(periodType);
        LocalDate start = requireStart(type, periodStart);
        BigDecimal money = requireAmount(amount);
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                RevenueTarget target = new RevenueTarget();
                target.setPeriodType(type);
                target.setPeriodStart(start);
                target.setTargetAmount(money);
                target.setNote(text);
                target.setCreatedBy(actorId);
                target.setCreatedAt(now);
                targetDAO.insert(con, target);
                auditService.log(con, actorId, "REVENUE_TARGET", target.getTargetId(),
                        AuditAction.TARGET_CREATED, null, money.toPlainString());
                return target;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Kỳ này đã có chỉ tiêu rồi. Hãy sửa chỉ tiêu đang có "
                    + "thay vì đặt thêm một cái nữa.");
        }
    }

    public void update(int actorId, int targetId, BigDecimal amount, String note) {
        BigDecimal money = requireAmount(amount);
        String text = optionalNote(note);
        LocalDateTime now = DateTimeUtil.now();

        Tx.writeVoid(con -> {
            RevenueTarget current = targetDAO.findById(con, targetId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy chỉ tiêu.");
            }
            targetDAO.update(con, targetId, money, text, now);
            auditService.log(con, actorId, "REVENUE_TARGET", targetId, AuditAction.TARGET_UPDATED,
                    current.getTargetAmount().toPlainString(), money.toPlainString());
        });
    }

    /**
     * Xoá hẳn chỉ tiêu.
     * <p>
     * Con số cũ được ghi vào nhật ký <b>trước khi</b> bản ghi biến mất. Đây là lý do bảng này
     * xoá hẳn được mà vẫn truy được: dòng {@code TARGET_DELETED} mang theo giá trị cũ, nên câu
     * hỏi "chỉ tiêu tháng đó là bao nhiêu và ai bỏ đi" vẫn còn chỗ để trả lời.
     */
    public void delete(int actorId, int targetId) {
        Tx.writeVoid(con -> {
            RevenueTarget current = targetDAO.findById(con, targetId);
            if (current == null) {
                throw new NotFoundException("Không tìm thấy chỉ tiêu.");
            }
            auditService.log(con, actorId, "REVENUE_TARGET", targetId, AuditAction.TARGET_DELETED,
                    current.getTargetAmount().toPlainString(), null);
            targetDAO.delete(con, targetId);
        });
    }

    // ============================================================ kiểm dữ liệu vào

    private String requirePeriodType(String periodType) {
        String type = periodType == null ? "" : periodType.trim().toUpperCase();
        if (!"DAY".equals(type) && !"MONTH".equals(type)) {
            throw new ValidationException("Kỳ chỉ tiêu chỉ nhận theo ngày hoặc theo tháng.");
        }
        return type;
    }

    /**
     * Kỳ tháng luôn quy về ngày mùng 1.
     * <p>
     * Không quy về thì hai chỉ tiêu "tháng 8" đặt lệch ngày sẽ cùng tồn tại, và ràng buộc duy
     * nhất không bắt được vì với nó đó là hai kỳ khác nhau. Sửa lặng lẽ ở đây tốt hơn báo lỗi:
     * người dùng chọn ngày nào trong tháng cũng đều có ý nói tháng đó.
     */
    private LocalDate requireStart(String periodType, LocalDate periodStart) {
        if (periodStart == null) {
            throw new ValidationException("Vui lòng chọn kỳ áp dụng chỉ tiêu.");
        }
        return "MONTH".equals(periodType) ? periodStart.withDayOfMonth(1) : periodStart;
    }

    private BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Chỉ tiêu phải là số tiền lớn hơn 0.");
        }
        return amount;
    }

    private String optionalNote(String note) {
        String text = note == null ? "" : note.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("Ghi chú tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }
        return text;
    }
}
