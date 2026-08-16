package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Ghi chú chế biến gắn với một món cụ thể — "làm lại vì rớt", "khách dặn ít cay".
 * <p>
 * Khác {@link KitchenIssue} ở chỗ nó <b>không phải chuyện phải xử lý</b>: không có trạng thái
 * mở/đóng, không hiện thành cảnh báo trên màn hình thu ngân, và xoá hẳn được.
 */
public class OrderItemNote {

    private int noteId;
    private int orderItemId;
    private int authorId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String authorName;

    public int getNoteId() { return noteId; }
    public void setNoteId(int noteId) { this.noteId = noteId; }

    public int getOrderItemId() { return orderItemId; }
    public void setOrderItemId(int orderItemId) { this.orderItemId = orderItemId; }

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

    /** Đã sửa lần nào chưa — màn hình hiện thêm dấu "đã sửa" để người đọc biết. */
    public boolean isEdited() { return updatedAt != null; }
}
