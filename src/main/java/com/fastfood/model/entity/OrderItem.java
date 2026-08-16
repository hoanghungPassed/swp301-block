package com.fastfood.model.entity;

import com.fastfood.common.constant.OrderItemStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng món trong đơn, đồng thời là một việc trên màn hình bếp.
 * <p>
 * Tên và giá được sao chép lại tại thời điểm đặt hàng. Nhờ vậy khi quản trị viên
 * sửa giá món thì hoá đơn cũ vẫn giữ nguyên giá lúc khách mua.
 * <p>
 * Mỗi dòng là một việc nguyên khối: đặt 3 phần gà thì bếp làm xong cả 3 mới đánh dấu
 * hoàn thành, không có trạng thái xong một phần.
 */
public class OrderItem {

    private int orderItemId;
    private int orderId;
    private int productId;
    private String productNameSnapshot;
    private BigDecimal unitPrice;
    private int quantity;
    private String itemStatus;
    private Integer assignedToUserId;
    private LocalDateTime startedAt;
    private LocalDateTime readyAt;

    // Bàn giao món từ bếp ra quầy — hai mốc của hai người khác nhau
    private LocalDateTime handedOverAt;
    private Integer handedOverBy;
    private LocalDateTime receivedAt;
    private Integer receivedBy;

    // Dữ liệu lấy kèm để màn hình bếp biết đơn này gấp tới đâu
    private String assignedToName;
    private String handedOverByName;
    private String receivedByName;
    private String orderSource;
    private String orderStatus;
    private LocalDateTime pickupTime;
    private int openIssueCount;

    public int getOrderItemId() { return orderItemId; }
    public void setOrderItemId(int orderItemId) { this.orderItemId = orderItemId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getItemStatus() { return itemStatus; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }

    public Integer getAssignedToUserId() { return assignedToUserId; }
    public void setAssignedToUserId(Integer assignedToUserId) { this.assignedToUserId = assignedToUserId; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getReadyAt() { return readyAt; }
    public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }

    public LocalDateTime getHandedOverAt() { return handedOverAt; }
    public void setHandedOverAt(LocalDateTime handedOverAt) { this.handedOverAt = handedOverAt; }

    public Integer getHandedOverBy() { return handedOverBy; }
    public void setHandedOverBy(Integer handedOverBy) { this.handedOverBy = handedOverBy; }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }

    public Integer getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Integer receivedBy) { this.receivedBy = receivedBy; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public String getHandedOverByName() { return handedOverByName; }
    public void setHandedOverByName(String handedOverByName) { this.handedOverByName = handedOverByName; }

    public String getReceivedByName() { return receivedByName; }
    public void setReceivedByName(String receivedByName) { this.receivedByName = receivedByName; }

    public String getOrderSource() { return orderSource; }
    public void setOrderSource(String orderSource) { this.orderSource = orderSource; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    /**
     * Đơn của món này đã bị đóng lại.
     * <p>
     * Món vẫn có thật và vẫn phải xử lý — nhưng là mang đi bỏ chứ không đưa cho khách.
     */
    public boolean isOrderClosed() {
        return orderStatus != null
            && !"CONFIRMED".equals(orderStatus)
            && !"PREPARING".equals(orderStatus)
            && !"READY".equals(orderStatus);
    }

    public LocalDateTime getPickupTime() { return pickupTime; }
    public void setPickupTime(LocalDateTime pickupTime) { this.pickupTime = pickupTime; }

    public int getOpenIssueCount() { return openIssueCount; }
    public void setOpenIssueCount(int openIssueCount) { this.openIssueCount = openIssueCount; }

    public BigDecimal getLineTotal() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public OrderItemStatus statusEnum() { return OrderItemStatus.valueOf(itemStatus); }

    public boolean isWaiting()   { return OrderItemStatus.WAITING.name().equals(itemStatus); }
    public boolean isPreparing() { return OrderItemStatus.PREPARING.name().equals(itemStatus); }
    public boolean isReady()     { return OrderItemStatus.READY.name().equals(itemStatus); }

    public boolean isClaimed() { return assignedToUserId != null; }

    public boolean isHandedOver() { return handedOverAt != null; }
    public boolean isReceived()   { return receivedAt != null; }

    /** Món đã xong nhưng bếp chưa đưa ra quầy — việc còn lại của đầu bếp. */
    public boolean isAwaitingHandover() { return isReady() && handedOverAt == null; }

    /** Món đang nằm trên quầy chờ thu ngân xác nhận đã cầm. */
    public boolean isAwaitingCounter() { return handedOverAt != null && receivedAt == null; }
}
