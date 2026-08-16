package com.fastfood.model.entity;

import java.time.LocalDateTime;

/**
 * Sự cố trong bếp: hết nguyên liệu, món hỏng phải làm lại.
 * Chạy song song với trạng thái món — món đang làm lại vẫn giữ trạng thái đang chế biến
 * chứ không lùi về hàng chờ.
 */
public class KitchenIssue {

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
