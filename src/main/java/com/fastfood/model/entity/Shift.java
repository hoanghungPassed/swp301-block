package com.fastfood.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một ca làm việc của thu ngân — đơn vị đối soát tiền mặt.
 * <p>
 * Khoản thu bằng thẻ hay mã QR có mã giao dịch trên biên lai để đối chiếu với sao kê; khoản thu
 * tiền mặt thì không có gì ngoài chính bản ghi thanh toán. Ca làm việc cho nó một con số kiểm
 * chứng được: đầu ca đếm tiền, cuối ca đếm lại, hệ thống nói ra chênh lệch.
 */
public class Shift {

    private int shiftId;
    private int cashierId;
    private LocalDateTime openedAt;
    private BigDecimal openingCash = BigDecimal.ZERO;
    private LocalDateTime closedAt;
    private BigDecimal countedCash;
    private BigDecimal expectedCash;
    private BigDecimal variance;
    private String note;
    private String status;

    private String cashierName;
    /** Số đơn tại quầy đã gắn vào ca — dùng để biết ca có thu hồi được hay không. */
    private int orderCount;

    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }

    public int getCashierId() { return cashierId; }
    public void setCashierId(int cashierId) { this.cashierId = cashierId; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }

    public BigDecimal getOpeningCash() { return openingCash; }
    public void setOpeningCash(BigDecimal openingCash) { this.openingCash = openingCash; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }

    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }

    public BigDecimal getVariance() { return variance; }
    public void setVariance(BigDecimal variance) { this.variance = variance; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }

    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }

    public boolean isOpen() { return "OPEN".equals(status); }

    public boolean isClosed() { return "CLOSED".equals(status); }

    /**
     * Ca có lệch tiền hay không. Chỉ có nghĩa với ca đã đóng.
     * <p>
     * So với 0 bằng {@code signum} chứ không {@code equals}: {@code 0} và {@code 0.00} là hai
     * đối tượng {@link BigDecimal} khác nhau, và cơ sở dữ liệu trả về dạng nào là tuỳ cột.
     * <p>
     * Tên bắt đầu bằng {@code is} chứ không {@code has}: EL chỉ nhận ra thuộc tính khi phương
     * thức đọc theo đúng quy ước JavaBean — {@code getX} hoặc {@code isX}. Đặt tên
     * {@code hasVariance} thì {@code ${s.hasVariance}} trong trang hiển thị lặng lẽ thành rỗng
     * và nhánh "thừa tiền" không bao giờ hiện ra.
     */
    public boolean isVaried() {
        return variance != null && variance.signum() != 0;
    }

    /**
     * Thiếu tiền — đáng chú ý hơn hẳn thừa tiền, nên màn hình tô khác màu.
     * <p>
     * Không đặt tên {@code isShort}: {@code short} là từ khoá của Java nên
     * {@code ${s.short}} không phân tích cú pháp được, và cả trang lịch sử ca đổ lỗi lúc chạy.
     */
    public boolean isShortOfCash() {
        return variance != null && variance.signum() < 0;
    }

    /**
     * Chênh lệch bỏ dấu, để trang hiển thị viết "thiếu 50.000đ" thay vì "-50.000đ".
     * Tính ở đây chứ không bằng phép toán trong EL: EL ép kiểu số thập phân theo cách riêng
     * của nó, và tiền là chỗ không nên phó thác cho việc ép kiểu ngầm.
     */
    public BigDecimal getVarianceAbs() {
        return variance == null ? BigDecimal.ZERO : variance.abs();
    }
}
