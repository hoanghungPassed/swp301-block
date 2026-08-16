package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Ghi chú điều phối gắn với một đơn hàng — của thu ngân.
 * <p>
 * Khác {@link OrderItemNote} của bếp ở phạm vi: cái kia nói về một món đang chế biến, cái này
 * nói về cả đơn — "khách gọi báo đến muộn", "đơn này ưu tiên".
 */
public class OrderNote {

    private int orderNoteId;
    private int orderId;
    private int authorId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String authorName;

    public int getOrderNoteId() { return orderNoteId; }
    public void setOrderNoteId(int orderNoteId) { this.orderNoteId = orderNoteId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

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
