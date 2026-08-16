package com.fastfood.service.staff;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.staff.ShiftDAO;
import com.fastfood.model.entity.Shift;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ca làm việc của thu ngân — đối soát tiền mặt đầu ca và cuối ca.
 * <p>
 * <b>Vì sao cần.</b> Tài liệu đã tự chỉ ra lỗ hổng: khoản thu bằng thẻ hay mã QR có mã giao dịch
 * trên biên lai để đối chiếu với sao kê, còn khoản thu tiền mặt "chỉ có chính bản ghi thanh toán"
 * làm dấu vết — tức là không đối chiếu được với gì cả. Ca làm việc cho nó một con số kiểm chứng
 * được.
 * <p>
 * <b>Không bắt buộc mở ca mới bán được.</b> Chặn bán khi chưa mở ca sẽ khiến thu ngân kẹt giữa
 * giờ cao điểm vì một thủ tục hành chính. Đơn không thuộc ca nào vẫn bán bình thường, chỉ không
 * vào được bảng đối soát — và màn hình có cảnh báo để chuyện đó không xảy ra trong im lặng.
 */
public class ShiftService {

    /** Số ca hiện trong lịch sử. Đủ để nhìn lại vài ngày mà không biến bảng thành sổ vô tận. */
    private static final int HISTORY_LIMIT = 30;

    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final AuditService auditService = new AuditService();

    // ============================================================ đọc

    /** Ca đang mở của thu ngân, hoặc null nếu chưa mở ca nào. */
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

    /**
     * Số tiền lẽ ra phải có trong két ngay lúc này — hiện trên màn hình trước khi thu ngân đếm.
     * <p>
     * Cố ý hiện <b>sau</b> khi nhập số đếm được thì tốt hơn về mặt đối soát, nhưng ở đây hiện
     * trước: đây là cửa hàng ăn nhanh, không phải ngân hàng, và giấu con số chỉ làm thu ngân
     * phải bấm thêm một lần để biết mình có đếm nhầm không.
     */
    public BigDecimal expectedCashNow(int shiftId) {
        return Tx.read(con -> shiftDAO.expectedCash(con, shiftId));
    }

    // ============================================================ mở ca

    /**
     * Mở ca với số tiền lẻ đầu ca.
     * <p>
     * Một thu ngân chỉ có một ca đang mở, và điều đó do ràng buộc duy nhất trong cơ sở dữ liệu
     * bảo đảm chứ không phải một lượt đọc trước: hai tab cùng bấm mở ca thì cả hai đều đọc thấy
     * "chưa có ca nào".
     */
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

    // ============================================================ sửa và đóng

    /** Ghi chú trong ca — bàn giao lại cho quản lý những chuyện không nằm trong con số. */
    public void updateNote(int shiftId, int cashierId, String note) {
        Tx.writeVoid(con -> {
            requireOwnOpenShift(con, shiftId, cashierId);
            shiftDAO.updateNote(con, shiftId, cashierId, note);
        });
    }

    /**
     * Đóng ca: thu ngân nhập số tiền đếm được, hệ thống tính chênh lệch.
     * <p>
     * Cả ba con số — đếm được, lẽ ra phải có, và chênh lệch — được <b>lưu lại</b> chứ không tính
     * lại mỗi lần đọc. Chúng là ảnh chụp tại thời điểm đóng ca; tính lại về sau thì một khoản
     * hoàn tiền phát sinh muộn sẽ lặng lẽ đổi con số của một ca đã chốt.
     *
     * @return ca sau khi đóng, kèm chênh lệch để màn hình báo ngay
     */
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

    /**
     * Thu hồi ca mở nhầm — chỉ khi chưa có đơn nào gắn vào.
     * <p>
     * Đã có đơn thì phải đóng ca cho đúng thủ tục, vì tiền của những đơn đó đã thật sự nằm trong
     * két. Thu hồi lúc ấy sẽ làm khoản tiền đó không thuộc về ca nào.
     */
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

    // ============================================================ dùng chung

    /**
     * Đọc trước để báo lỗi cho đúng: các câu lệnh ghi đã gộp cả ba điều kiện (tồn tại, đúng
     * người, còn mở) vào chính chúng, nên khi trả về 0 dòng thì không biết hỏng ở điều kiện nào.
     */
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
