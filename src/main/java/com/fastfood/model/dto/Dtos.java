package com.fastfood.model.dto;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.StarRating;
import com.fastfood.model.entity.OrderEntities.CartItem;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public static class Page<T> {

        public static final int SIZE = 20;

        public static final int MAX_SIZE = 200;

        private final List<T> items;
        private final int pageNo;
        private final int pageSize;
        private final long totalItems;

        public Page(List<T> items, int pageNo, int pageSize, long totalItems) {
            this.items = items;
            this.pageNo = pageNo;
            this.pageSize = pageSize;
            this.totalItems = totalItems;
        }

        public List<T> getItems() { return items; }
        public int getPageNo() { return pageNo; }
        public int getPageSize() { return pageSize; }
        public long getTotalItems() { return totalItems; }

        public boolean isEmptyPage() { return items == null || items.isEmpty(); }

        public int getTotalPages() {
            if (totalItems <= 0) {
                return 1;
            }
            return (int) ((totalItems + pageSize - 1) / pageSize);
        }

        public boolean isFirst() { return pageNo <= 1; }
        public boolean isLast() { return pageNo >= getTotalPages(); }

        public int getPrevPage() { return Math.max(1, pageNo - 1); }
        public int getNextPage() { return Math.min(getTotalPages(), pageNo + 1); }

        public long getFirstIndex() {
            return isEmptyPage() ? 0 : (long) (pageNo - 1) * pageSize + 1;
        }

        public long getLastIndex() {
            return isEmptyPage() ? 0 : getFirstIndex() + items.size() - 1;
        }

        public boolean isPaged() { return getTotalPages() > 1; }

        public static int safePage(int requested) {
            return Math.max(1, requested);
        }

        public static int safeSize(int requested) {
            if (requested <= 0) {
                return SIZE;
            }
            return Math.min(requested, MAX_SIZE);
        }

        public static int offset(int pageNo, int pageSize) {
            return (safePage(pageNo) - 1) * safeSize(pageSize);
        }
    }

    public static class CartView {

        private int cartId;
        private List<CartItem> items = new ArrayList<>();

        public int getCartId() { return cartId; }
        public void setCartId(int cartId) { this.cartId = cartId; }

        public List<CartItem> getItems() { return items; }
        public void setItems(List<CartItem> items) { this.items = items; }

        public BigDecimal getTotalAmount() {
            return items.stream()
                    .filter(CartItem::isOrderable)
                    .map(CartItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public int getTotalQuantity() {
            return items.stream().filter(CartItem::isOrderable).mapToInt(CartItem::getQuantity).sum();
        }

        public boolean isEmptyCart() { return items.isEmpty(); }

        public boolean isHasUnavailable() {
            return items.stream().anyMatch(i -> !i.isOrderable());
        }

        public boolean isCheckoutable() {
            return !isEmptyCart() && !isHasUnavailable();
        }
    }

    public static class TemplateApplyResult {

        private int addedCount;
        private final List<String> skippedNames = new ArrayList<>();

        public int getAddedCount() { return addedCount; }

        public void countAdded() { this.addedCount++; }

        public List<String> getSkippedNames() { return skippedNames; }

        public void skip(String productName) { this.skippedNames.add(productName); }

        public boolean isAnythingAdded() { return addedCount > 0; }

        public boolean isAnythingSkipped() { return !skippedNames.isEmpty(); }

        public String getSkippedText() {
            return String.join(", ", skippedNames);
        }
    }

    public static class PosLine {

        private int productId;
        private int quantity;

        public PosLine(int productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public int getProductId() { return productId; }
        public int getQuantity() { return quantity; }
    }

    public static class PosCartLine {

        private final int productId;
        private final String productName;
        private final BigDecimal unitPrice;
        private final int quantity;
        private final boolean orderable;

        public PosCartLine(int productId, String productName, BigDecimal unitPrice,
                           int quantity, boolean orderable) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.orderable = orderable;
        }

        public static PosCartLine missing(int productId, int quantity) {
            return new PosCartLine(productId, "Món không còn trong hệ thống",
                    BigDecimal.ZERO, quantity, false);
        }

        public int getProductId() { return productId; }

        public String getProductName() { return productName; }

        public BigDecimal getUnitPrice() { return unitPrice; }

        public int getQuantity() { return quantity; }

        public boolean isOrderable() { return orderable; }

        public BigDecimal getLineTotal() {
            return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static class KdsItemView {

        private OrderItem item;
        private String orderSource;
        private LocalDateTime pickupTime;
        private int openIssueCount;

        public KdsItemView(OrderItem item) {
            this.item = item;
            this.orderSource = item.getOrderSource();
            this.pickupTime = item.getPickupTime();
            this.openIssueCount = item.getOpenIssueCount();
        }

        public OrderItem getItem() { return item; }
        public String getOrderSource() { return orderSource; }
        public LocalDateTime getPickupTime() { return pickupTime; }
        public int getOpenIssueCount() { return openIssueCount; }

        public boolean isOnline() { return "ONLINE_PREORDER".equals(orderSource); }

        public long getMinutesToPickup() {
            return pickupTime == null ? Long.MAX_VALUE : DateTimeUtil.minutesBetween(DateTimeUtil.now(), pickupTime);
        }

        public boolean isUrgent() {
            return pickupTime != null && getMinutesToPickup() <= 10;
        }

        public boolean isLate() {
            return pickupTime != null && getMinutesToPickup() < 0;
        }

        public String getPickupLabel() {
            return pickupTime == null ? "Khách đang đợi tại quầy" : DateTimeUtil.humanize(pickupTime);
        }
    }

    public static class DashboardKpi {

        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal netRevenue = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;
        private int onlineOrderCount;
        private int posOrderCount;
        private int completedOrderCount;
        private int cancelledOrderCount;
        private int expiredOrderCount;
        private int readyOrderCount;
        private int overduePickupCount;
        private int onTimeReadyCount;
        private int totalReadyMeasured;
        private Double avgPrepLeadMinutes;

        public BigDecimal getGrossRevenue() { return grossRevenue; }
        public void setGrossRevenue(BigDecimal grossRevenue) { this.grossRevenue = grossRevenue; }

        public BigDecimal getNetRevenue() { return netRevenue; }
        public void setNetRevenue(BigDecimal netRevenue) { this.netRevenue = netRevenue; }

        public BigDecimal getRefundedAmount() { return refundedAmount; }
        public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

        public int getOnlineOrderCount() { return onlineOrderCount; }
        public void setOnlineOrderCount(int onlineOrderCount) { this.onlineOrderCount = onlineOrderCount; }

        public int getPosOrderCount() { return posOrderCount; }
        public void setPosOrderCount(int posOrderCount) { this.posOrderCount = posOrderCount; }

        public int getCompletedOrderCount() { return completedOrderCount; }
        public void setCompletedOrderCount(int completedOrderCount) { this.completedOrderCount = completedOrderCount; }

        public int getCancelledOrderCount() { return cancelledOrderCount; }
        public void setCancelledOrderCount(int cancelledOrderCount) { this.cancelledOrderCount = cancelledOrderCount; }

        public int getExpiredOrderCount() { return expiredOrderCount; }
        public void setExpiredOrderCount(int expiredOrderCount) { this.expiredOrderCount = expiredOrderCount; }

        public int getReadyOrderCount() { return readyOrderCount; }
        public void setReadyOrderCount(int readyOrderCount) { this.readyOrderCount = readyOrderCount; }

        public int getOverduePickupCount() { return overduePickupCount; }
        public void setOverduePickupCount(int overduePickupCount) { this.overduePickupCount = overduePickupCount; }

        public int getOnTimeReadyCount() { return onTimeReadyCount; }
        public void setOnTimeReadyCount(int onTimeReadyCount) { this.onTimeReadyCount = onTimeReadyCount; }

        public int getTotalReadyMeasured() { return totalReadyMeasured; }
        public void setTotalReadyMeasured(int totalReadyMeasured) { this.totalReadyMeasured = totalReadyMeasured; }

        public Double getAvgPrepLeadMinutes() { return avgPrepLeadMinutes; }
        public void setAvgPrepLeadMinutes(Double avgPrepLeadMinutes) { this.avgPrepLeadMinutes = avgPrepLeadMinutes; }

        public int getTotalOrderCount() { return onlineOrderCount + posOrderCount; }

        public double getOnTimeReadyRate() {
            return totalReadyMeasured == 0 ? 0 : (100.0 * onTimeReadyCount / totalReadyMeasured);
        }
    }

    public static class ReportRow {

        private String label;
        private String subLabel;
        private long quantity;
        private BigDecimal amount = BigDecimal.ZERO;

        public ReportRow() {
        }

        public ReportRow(String label, long quantity, BigDecimal amount) {
            this.label = label;
            this.quantity = quantity;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getSubLabel() { return subLabel; }
        public void setSubLabel(String subLabel) { this.subLabel = subLabel; }

        public long getQuantity() { return quantity; }
        public void setQuantity(long quantity) { this.quantity = quantity; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class ReviewSummary {

        private BigDecimal average = BigDecimal.ZERO;
        private int count;

        public BigDecimal getAverage() { return average; }
        public void setAverage(BigDecimal average) {
            this.average = average == null ? BigDecimal.ZERO : average;
        }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public boolean isEmptySummary() { return count == 0; }

        public BigDecimal getAverageRounded() {
            return average.setScale(1, RoundingMode.HALF_UP);
        }

        public String getStars() {
            return StarRating.of(average);
        }
    }

}
