package com.fastfood.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng kế hoạch chuẩn bị sẵn trong ca: hôm nay làm sẵn bao nhiêu phần của món này.
 * <p>
 * Khác mọi thứ khác trong bếp ở chỗ nó <b>không gắn với đơn hàng nào</b> — bếp làm sẵn theo
 * dự đoán chứ không đợi khách gọi. Vì vậy lớp này trỏ thẳng tới món, không qua {@link OrderItem}.
 */
public class PrepTask {

    private int prepTaskId;
    private int productId;
    private LocalDate prepDate;
    private int plannedQty;
    private int doneQty;
    private String note;
    private int createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;

    private String productName;
    private String createdByName;

    public int getPrepTaskId() { return prepTaskId; }
    public void setPrepTaskId(int prepTaskId) { this.prepTaskId = prepTaskId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public LocalDate getPrepDate() { return prepDate; }
    public void setPrepDate(LocalDate prepDate) { this.prepDate = prepDate; }

    public int getPlannedQty() { return plannedQty; }
    public void setPlannedQty(int plannedQty) { this.plannedQty = plannedQty; }

    public int getDoneQty() { return doneQty; }
    public void setDoneQty(int doneQty) { this.doneQty = doneQty; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    /** Còn sửa được hay không. Kế hoạch đã chốt hoặc đã thu hồi thì không. */
    public boolean isPlanned() { return "PLANNED".equals(status); }

    public boolean isDone() { return "DONE".equals(status); }

    /**
     * Còn thiếu bao nhiêu phần so với kế hoạch. Âm nghĩa là làm dư — vẫn trả về đúng số âm
     * chứ không kẹp về 0, vì làm dư cũng là chuyện cần nhìn thấy khi đặt số cho ca sau.
     * <p>
     * Tên phải có tiền tố {@code get}: {@code kds-queue.jsp} đọc nó bằng {@code ${t.remainingQty}},
     * mà EL chỉ nhìn thấy {@code getX}/{@code isX}. Viết {@code remainingQty()} thì trang vẫn
     * dịch được và chỉ đổ lỗi lúc chạy, đúng lúc trong bảng có dòng kế hoạch.
     */
    public int getRemainingQty() { return plannedQty - doneQty; }
}
