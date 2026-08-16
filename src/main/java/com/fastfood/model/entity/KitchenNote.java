package com.fastfood.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Một dòng trong sổ bàn giao ca bếp — thứ ca trước cần nói lại với ca sau.
 * <p>
 * Không gắn với đơn hay món nào: nội dung là chuyện của cả ca, ví dụ lò nóng chậm hay hết
 * vật tư. Cùng nhóm với {@link OrderItemNote} ở chỗ đây là thông tin để lại, không phải
 * chuyện phải xử lý — nên xoá hẳn được.
 */
public class KitchenNote {

    private int kitchenNoteId;
    private LocalDate shiftDate;
    private int authorId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String authorName;

    public int getKitchenNoteId() { return kitchenNoteId; }
    public void setKitchenNoteId(int kitchenNoteId) { this.kitchenNoteId = kitchenNoteId; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public int getAuthorId() { return authorId; }
    public void setAuthorId(int authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public boolean isEdited() { return updatedAt != null; }
}
