package com.fastfood.service.staff;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.staff.ShiftDAO;
import com.fastfood.model.entity.OperationEntities.Shift;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class ShiftService {

    private static final int HISTORY_LIMIT = 30;

    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final AuditService auditService = new AuditService();

    public Shift currentShift(int cashierId) {
        return Tx.read(con -> shiftDAO.findOpenOf(con, cashierId));
    }

    public List<Shift> myShifts(int cashierId) {
        return Tx.read(con -> shiftDAO.findByCashier(con, cashierId, HISTORY_LIMIT));
    }

    public Shift findById(int shiftId) {
        Shift shift = Tx.read(con -> shiftDAO.findById(con, shiftId));
        if (shift == null) {
            throw new NotFoundException("Không tìm thấy ca làm việc.");
        }
        return shift;
    }

    public BigDecimal expectedCashNow(int shiftId) {
        return Tx.read(con -> shiftDAO.expectedCash(con, shiftId));
    }

    public Shift open(int cashierId, BigDecimal openingCash, String note) {
        BigDecimal opening = openingCash == null ? BigDecimal.ZERO : openingCash;
        if (opening.signum() < 0) {
            throw new ValidationException("Tiền đầu ca không được âm.");
        }
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                Shift shift = new Shift();
                shift.setCashierId(cashierId);
                shift.setOpenedAt(now);
                shift.setOpeningCash(opening);
                shift.setNote(note);
                shift.setStatus("OPEN");
                shiftDAO.insert(con, shift);
                auditService.log(con, cashierId, "SHIFT", shift.getShiftId(),
                        AuditAction.SHIFT_OPENED, null, opening.toPlainString());
                return shift;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đang có một ca chưa đóng. "
                    + "Hãy đóng ca đó trước khi mở ca mới.");
        }
    }

    public void updateNote(int shiftId, int cashierId, String note) {
        Tx.writeVoid(con -> {
            requireOwnOpenShift(con, shiftId, cashierId);
            shiftDAO.updateNote(con, shiftId, cashierId, note);
        });
    }

    public Shift close(int shiftId, int cashierId, BigDecimal countedCash) {
        if (countedCash == null || countedCash.signum() < 0) {
            throw new ValidationException("Vui lòng nhập số tiền đếm được, không âm.");
        }
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            requireOwnOpenShift(con, shiftId, cashierId);
            BigDecimal expected = shiftDAO.expectedCash(con, shiftId);
            BigDecimal variance = countedCash.subtract(expected);

            int changed = shiftDAO.close(con, shiftId, cashierId, countedCash, expected, variance, now);
            if (changed == 0) {
                throw new BusinessException("Ca này vừa được đóng hoặc thu hồi bởi thao tác khác.");
            }
            auditService.log(con, cashierId, "SHIFT", shiftId, AuditAction.SHIFT_CLOSED,
                    expected.toPlainString(), countedCash.toPlainString());
            return shiftDAO.findById(con, shiftId);
        });
    }

    public void cancel(int shiftId, int cashierId) {
        Tx.writeVoid(con -> {
            Shift shift = requireOwnOpenShift(con, shiftId, cashierId);
            if (shift.getOrderCount() > 0) {
                throw new BusinessException("Ca này đã có " + shift.getOrderCount()
                        + " đơn nên không thu hồi được. Hãy đóng ca để đối soát tiền.");
            }
            int changed = shiftDAO.cancel(con, shiftId, cashierId);
            if (changed == 0) {
                throw new BusinessException("Ca vừa có đơn mới hoặc vừa được đóng, không thu hồi được nữa.");
            }
            auditService.log(con, cashierId, "SHIFT", shiftId,
                    AuditAction.SHIFT_CANCELLED, "OPEN", "CANCELLED");
        });
    }

    private Shift requireOwnOpenShift(Connection con, int shiftId, int cashierId) throws SQLException {
        Shift shift = shiftDAO.findById(con, shiftId);
        if (shift == null) {
            throw new NotFoundException("Không tìm thấy ca làm việc.");
        }
        if (shift.getCashierId() != cashierId) {
            throw new BusinessException("Chỉ người mở ca mới thao tác được trên ca này.");
        }
        if (!shift.isOpen()) {
            throw new BusinessException("Ca này đã khép lại.");
        }
        return shift;
    }
}
