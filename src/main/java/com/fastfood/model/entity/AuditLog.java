package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Nhật ký thao tác. Thiết kế dạng chung để ghi được mọi loại đối tượng
 * mà không phải thêm bảng mỗi khi có nghiệp vụ mới.
 * {@code actorId} để trống nghĩa là do hệ thống tự thực hiện.
 */
public class AuditLog {

    private long auditId;
    private Integer actorId;
    private String entityType;
    private String entityId;
    private String action;
    private String oldValue;
    private String newValue;
    private LocalDateTime createdAt;

    private String actorName;

    public long getAuditId() { return auditId; }
    public void setAuditId(long auditId) { this.auditId = auditId; }

    public Integer getActorId() { return actorId; }
    public void setActorId(Integer actorId) { this.actorId = actorId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    /** Hiển thị "Hệ thống" khi thao tác do Scheduler hoặc cổng thanh toán gây ra. */
    public String getActorDisplay() {
        return actorName == null || actorName.isBlank() ? "Hệ thống" : actorName;
    }
}
