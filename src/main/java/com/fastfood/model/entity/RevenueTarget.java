package com.fastfood.model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chỉ tiêu doanh thu của một kỳ — ngày hoặc tháng.
 * <p>
 * Chỉ là con số để đối chiếu: không tham gia vào bất kỳ phép tính tiền nào của hệ thống. Doanh
 * thu thật vẫn do {@code ReportDAO} tính, còn đây chỉ nói "lẽ ra phải đạt bao nhiêu".
 */
public class RevenueTarget {

    private int targetId;
    private String periodType;
    private LocalDate periodStart;
    private BigDecimal targetAmount;
    private String note;
    private int createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String createdByName;

    /** Doanh thu thuần đã đạt trong kỳ — gắn vào khi màn hình cần so, không lưu xuống bảng. */
    private BigDecimal achieved;

    public int getTargetId() { return targetId; }
    public void setTargetId(int targetId) { this.targetId = targetId; }

    public String getPeriodType() { return periodType; }
    public void setPeriodType(String periodType) { this.periodType = periodType; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public BigDecimal getAchieved() { return achieved; }
    public void setAchieved(BigDecimal achieved) { this.achieved = achieved; }

    public boolean isMonthly() { return "MONTH".equals(periodType); }

    /**
     * Nhãn kỳ để hiện lên màn hình — "08/2026" hoặc "15/08/2026".
     * <p>
     * Định dạng ở đây chứ không dùng hàm thẻ {@code ff:date}: hàm đó nhận {@code LocalDateTime},
     * còn kỳ chỉ tiêu là một ngày không giờ. Ép sang có giờ chỉ để hiển thị sẽ đẻ ra một mốc
     * 00:00 giả trong khi bản thân dữ liệu không có khái niệm đó.
     */
    public String getPeriodLabel() {
        if (periodStart == null) {
            return "";
        }
        return isMonthly()
                ? String.format("%02d/%d", periodStart.getMonthValue(), periodStart.getYear())
                : String.format("%02d/%02d/%d", periodStart.getDayOfMonth(),
                        periodStart.getMonthValue(), periodStart.getYear());
    }

    public boolean isEdited() { return updatedAt != null; }

    /**
     * Phần trăm đã đạt, làm tròn xuống hàng đơn vị.
     * <p>
     * Tính ở đây chứ không trong JSP: EL không làm được phép chia {@code BigDecimal}, và viết
     * phép tính tiền vào trang hiển thị là chỗ dễ sai nhất mà không có bài test nào chạm tới.
     */
    public int getAchievedPercent() {
        if (achieved == null || targetAmount == null || targetAmount.signum() == 0) {
            return 0;
        }
        return achieved.multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 0, java.math.RoundingMode.DOWN)
                .intValue();
    }

    /** Còn thiếu bao nhiêu để chạm chỉ tiêu; đã vượt thì trả về 0 chứ không trả số âm. */
    public BigDecimal getRemaining() {
        if (achieved == null || targetAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal con_thieu = targetAmount.subtract(achieved);
        return con_thieu.signum() < 0 ? BigDecimal.ZERO : con_thieu;
    }

    public boolean isReached() {
        return achieved != null && targetAmount != null && achieved.compareTo(targetAmount) >= 0;
    }
}
