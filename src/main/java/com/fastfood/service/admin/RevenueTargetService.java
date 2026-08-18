package com.fastfood.service.admin;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.admin.RevenueTargetDAO;
import com.fastfood.model.entity.OperationEntities.RevenueTarget;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class RevenueTargetService {

    private static final int HISTORY_LIMIT = 24;
    private static final int MAX_NOTE_LENGTH = 500;

    private final RevenueTargetDAO targetDAO = new RevenueTargetDAO();
    private final ReportService reportService = new ReportService();
    private final AuditService auditService = new AuditService();

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

    public RevenueTarget currentMonth() {
        LocalDate today = DateTimeUtil.now().toLocalDate();
        return findForPeriod("MONTH", today.withDayOfMonth(1));
    }

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

    private void fillAchieved(RevenueTarget target) {
        LocalDateTime from = target.getPeriodStart().atStartOfDay();
        LocalDateTime to = (target.isMonthly()
                ? target.getPeriodStart().plusMonths(1)
                : target.getPeriodStart().plusDays(1)).atStartOfDay().minusSeconds(1);
        target.setAchieved(reportService.loadKpi(from, to).getNetRevenue());
    }

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

    private String requirePeriodType(String periodType) {
        String type = periodType == null ? "" : periodType.trim().toUpperCase();
        if (!"DAY".equals(type) && !"MONTH".equals(type)) {
            throw new ValidationException("Kỳ chỉ tiêu chỉ nhận theo ngày hoặc theo tháng.");
        }
        return type;
    }

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
