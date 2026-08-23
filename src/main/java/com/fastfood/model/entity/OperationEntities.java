package com.fastfood.model.entity;

import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.OrderEntities.CartItem;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.OrderEntities.OrderItemNote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OperationEntities {

    private OperationEntities() {
    }

    public static class KitchenIssue {

        private int issueId;
        private int orderItemId;
        private int createdBy;
        private String issueType;
        private String description;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime resolvedAt;

        private String createdByName;
        private String productName;
        private int orderId;

        public int getIssueId() { return issueId; }
        public void setIssueId(int issueId) { this.issueId = issueId; }

        public int getOrderItemId() { return orderItemId; }
        public void setOrderItemId(int orderItemId) { this.orderItemId = orderItemId; }

        public int getCreatedBy() { return createdBy; }
        public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

        public String getCreatedByName() { return createdByName; }
        public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public int getOrderId() { return orderId; }
        public void setOrderId(int orderId) { this.orderId = orderId; }

        public boolean isOpen() { return "OPEN".equals(status); }
    }

    public static class KitchenNote {

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

    public static class PrepTask {

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

        public boolean isPlanned() { return "PLANNED".equals(status); }

        public boolean isDone() { return "DONE".equals(status); }

        public int getRemainingQty() { return plannedQty - doneQty; }
    }

    public static class AuditLog {

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

        public String getActorDisplay() {
            return actorName == null || actorName.isBlank() ? "Hệ thống" : actorName;
        }
    }

    public static class RevenueTarget {

        private int targetId;
        private String periodType;
        private LocalDate periodStart;
        private BigDecimal targetAmount;
        private String note;
        private int createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String createdByName;

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

        public int getAchievedPercent() {
            if (achieved == null || targetAmount == null || targetAmount.signum() == 0) {
                return 0;
            }
            return achieved.multiply(BigDecimal.valueOf(100))
                    .divide(targetAmount, 0, java.math.RoundingMode.DOWN)
                    .intValue();
        }

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

}
