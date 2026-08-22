package com.fastfood.model.entity;

import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.OperationEntities.KitchenIssue;
import com.fastfood.common.constant.Constants.KdsReleaseState;
import com.fastfood.common.constant.Constants.OrderItemStatus;
import com.fastfood.common.constant.Constants.OrderSource;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.constant.Constants.PaymentMethod;
import com.fastfood.common.constant.Constants.PaymentStatus;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class OrderEntities {

    private OrderEntities() {
    }

    public static class Cart {
        private int cartId;
        private int userId;
        private LocalDateTime updatedAt;

        public int getCartId() { return cartId; }
        public void setCartId(int cartId) { this.cartId = cartId; }

        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    public static class CartItem {
        private int cartItemId;
        private int cartId;
        private int productId;
        private int quantity;

        private String productName;
        private BigDecimal unitPrice;
        private String imageUrl;
        private boolean available;
        private String productStatus;
        private String categoryStatus;

        public int getCartItemId() { return cartItemId; }
        public void setCartItemId(int cartItemId) { this.cartItemId = cartItemId; }

        public int getCartId() { return cartId; }
        public void setCartId(int cartId) { this.cartId = cartId; }

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public String getProductStatus() { return productStatus; }
        public void setProductStatus(String productStatus) { this.productStatus = productStatus; }

        public String getCategoryStatus() { return categoryStatus; }
        public void setCategoryStatus(String categoryStatus) { this.categoryStatus = categoryStatus; }

        public BigDecimal getLineTotal() {
            return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
        }

        public boolean isOrderable() {
            return available && "ACTIVE".equals(productStatus) && "ACTIVE".equals(categoryStatus);
        }
    }

    public static class Order {

        private int orderId;
        private Integer customerId;
        private Integer createdByUserId;
        private String orderSource;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private String orderStatus;
        private String idempotencyKey;

        private LocalDateTime pickupTime;
        private LocalDateTime kitchenReleaseAt;
        private LocalDateTime releasedToKdsAt;
        private String pickupCode;

        private LocalDateTime readyAt;
        private LocalDateTime pickedUpAt;
        private Integer handoffByUserId;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelledAt;
        private LocalDateTime expiredAt;

        private String customerName;
        private String customerEmail;
        private String handoffByName;
        private List<OrderItem> items = new ArrayList<>();
        private Payment latestPayment;

        public int getOrderId() { return orderId; }
        public void setOrderId(int orderId) { this.orderId = orderId; }

        public Integer getCustomerId() { return customerId; }
        public void setCustomerId(Integer customerId) { this.customerId = customerId; }

        public Integer getCreatedByUserId() { return createdByUserId; }
        public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }


        public String getOrderSource() { return orderSource; }
        public void setOrderSource(String orderSource) { this.orderSource = orderSource; }

        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public String getOrderStatus() { return orderStatus; }
        public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

        public LocalDateTime getPickupTime() { return pickupTime; }
        public void setPickupTime(LocalDateTime pickupTime) { this.pickupTime = pickupTime; }

        public LocalDateTime getKitchenReleaseAt() { return kitchenReleaseAt; }
        public void setKitchenReleaseAt(LocalDateTime kitchenReleaseAt) { this.kitchenReleaseAt = kitchenReleaseAt; }

        public LocalDateTime getReleasedToKdsAt() { return releasedToKdsAt; }
        public void setReleasedToKdsAt(LocalDateTime releasedToKdsAt) { this.releasedToKdsAt = releasedToKdsAt; }

        public String getPickupCode() { return pickupCode; }
        public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }

        public LocalDateTime getReadyAt() { return readyAt; }
        public void setReadyAt(LocalDateTime readyAt) { this.readyAt = readyAt; }

        public LocalDateTime getPickedUpAt() { return pickedUpAt; }
        public void setPickedUpAt(LocalDateTime pickedUpAt) { this.pickedUpAt = pickedUpAt; }

        public Integer getHandoffByUserId() { return handoffByUserId; }
        public void setHandoffByUserId(Integer handoffByUserId) { this.handoffByUserId = handoffByUserId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

        public LocalDateTime getCancelledAt() { return cancelledAt; }
        public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

        public LocalDateTime getExpiredAt() { return expiredAt; }
        public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

        public String getHandoffByName() { return handoffByName; }
        public void setHandoffByName(String handoffByName) { this.handoffByName = handoffByName; }

        public List<OrderItem> getItems() { return items; }
        public void setItems(List<OrderItem> items) { this.items = items; }

        public Payment getLatestPayment() { return latestPayment; }
        public void setLatestPayment(Payment latestPayment) { this.latestPayment = latestPayment; }

        public boolean isOnline() { return OrderSource.ONLINE_PREORDER.name().equals(orderSource); }

        public boolean isPos() { return OrderSource.POS.name().equals(orderSource); }

        public OrderStatus statusEnum() { return OrderStatus.valueOf(orderStatus); }

        public KdsReleaseState getReleaseState() {
            if (releasedToKdsAt != null) {
                return KdsReleaseState.RELEASED_TO_KDS;
            }
            if (OrderStatus.CONFIRMED.name().equals(orderStatus)) {
                return KdsReleaseState.SCHEDULED;
            }
            return KdsReleaseState.NOT_RELEASED;
        }

        public boolean isOverdue() {
            if (!isOnline() || pickupTime == null || !OrderStatus.READY.name().equals(orderStatus)) {
                return false;
            }
            return DateTimeUtil.now().isAfter(pickupTime.plusMinutes(AppConfig.pickupOverdueMinutes()));
        }

        public boolean isLateReady() {
            return isOnline() && readyAt != null && pickupTime != null && readyAt.isAfter(pickupTime);
        }

        public boolean isCancellable() {
            if (OrderStatus.PENDING_PAYMENT.name().equals(orderStatus)) {
                return true;
            }
            if (!OrderStatus.CONFIRMED.name().equals(orderStatus)) {
                return false;
            }
            return items.stream()
                    .allMatch(i -> OrderItemStatus.WAITING.name().equals(i.getItemStatus()));
        }

        public boolean isStaffCancellable() {
            return !statusEnum().isFinal();
        }

        public boolean isActiveForKitchen() {
            return OrderStatus.CONFIRMED.name().equals(orderStatus)
                || OrderStatus.PREPARING.name().equals(orderStatus);
        }

        public boolean isRefundPending() {
            return statusEnum().isFinal()
                && !OrderStatus.COMPLETED.name().equals(orderStatus)
                && latestPayment != null
                && PaymentStatus.PAID.name().equals(latestPayment.getPaymentStatus());
        }

        public boolean isPaid() {
            return latestPayment != null
                && PaymentStatus.PAID.name().equals(latestPayment.getPaymentStatus());
        }

        public int getTotalQuantity() {
            return items.stream().mapToInt(OrderItem::getQuantity).sum();
        }
    }

    public static class OrderItem {

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

        private LocalDateTime handedOverAt;
        private Integer handedOverBy;
        private LocalDateTime receivedAt;
        private Integer receivedBy;

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

        public boolean isAwaitingHandover() { return isReady() && handedOverAt == null; }

        public boolean isAwaitingCounter() { return handedOverAt != null && receivedAt == null; }
    }

    public static class OrderNote {

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

    public static class OrderItemNote {

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

        public boolean isEdited() { return updatedAt != null; }
    }

    public static class OrderTemplate {

        private int templateId;
        private int customerId;
        private String name;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private List<OrderTemplateItem> items = new ArrayList<>();

        public int getTemplateId() { return templateId; }
        public void setTemplateId(int templateId) { this.templateId = templateId; }

        public int getCustomerId() { return customerId; }
        public void setCustomerId(int customerId) { this.customerId = customerId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        public List<OrderTemplateItem> getItems() { return items; }
        public void setItems(List<OrderTemplateItem> items) {
            this.items = items == null ? new ArrayList<>() : items;
        }

        public boolean isEdited() { return updatedAt != null; }

        public int getLineCount() { return items.size(); }

        public BigDecimal getEstimatedTotal() {
            BigDecimal sum = BigDecimal.ZERO;
            for (OrderTemplateItem item : items) {
                sum = sum.add(item.getLineTotal());
            }
            return sum;
        }

        public boolean isAnyUnavailable() {
            for (OrderTemplateItem item : items) {
                if (!item.isOrderable()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class OrderTemplateItem {

        private int templateItemId;
        private int templateId;
        private int productId;
        private int quantity;

        private String productName;
        private BigDecimal unitPrice;
        private boolean available;
        private String productStatus;

        public int getTemplateItemId() { return templateItemId; }
        public void setTemplateItemId(int templateItemId) { this.templateItemId = templateItemId; }

        public int getTemplateId() { return templateId; }
        public void setTemplateId(int templateId) { this.templateId = templateId; }

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public String getProductStatus() { return productStatus; }
        public void setProductStatus(String productStatus) { this.productStatus = productStatus; }

        public boolean isOrderable() {
            return "ACTIVE".equals(productStatus) && available;
        }

        public BigDecimal getLineTotal() {
            return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static class Payment {

        private int paymentId;
        private int orderId;
        private String method;
        private BigDecimal amount;
        private String paymentStatus;
        private int attemptNo;
        private LocalDateTime createdAt;
        private LocalDateTime paidAt;
        private LocalDateTime refundedAt;

        private String orderSource;

        public int getPaymentId() { return paymentId; }
        public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

        public int getOrderId() { return orderId; }
        public void setOrderId(int orderId) { this.orderId = orderId; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getPaymentStatus() { return paymentStatus; }
        public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

        public int getAttemptNo() { return attemptNo; }
        public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getPaidAt() { return paidAt; }
        public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

        public LocalDateTime getRefundedAt() { return refundedAt; }
        public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }

        public String getOrderSource() { return orderSource; }
        public void setOrderSource(String orderSource) { this.orderSource = orderSource; }

        public PaymentStatus statusEnum() { return PaymentStatus.valueOf(paymentStatus); }
        public PaymentMethod methodEnum() { return PaymentMethod.valueOf(method); }

        public boolean isPaid()     { return PaymentStatus.PAID.name().equals(paymentStatus); }
        public boolean isPending()  { return PaymentStatus.PENDING.name().equals(paymentStatus); }
        public boolean isRefunded() { return PaymentStatus.REFUNDED.name().equals(paymentStatus); }
        public boolean isCash()     { return PaymentMethod.CASH.name().equals(method); }
    }

    public static class Transaction {

        private int transactionId;
        private int paymentId;
        private String gateway;
        private String externalTransactionId;
        private String status;
        private String rawReference;
        private LocalDateTime createdAt;

        public int getTransactionId() { return transactionId; }
        public void setTransactionId(int transactionId) { this.transactionId = transactionId; }

        public int getPaymentId() { return paymentId; }
        public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

        public String getGateway() { return gateway; }
        public void setGateway(String gateway) { this.gateway = gateway; }

        public String getExternalTransactionId() { return externalTransactionId; }
        public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getRawReference() { return rawReference; }
        public void setRawReference(String rawReference) { this.rawReference = rawReference; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }

}
