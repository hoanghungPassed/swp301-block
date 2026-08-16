package com.fastfood.model.dto;

import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.model.entity.OrderItem;

import java.time.LocalDateTime;

/**
 * Một việc trên màn hình bếp: dòng món kèm thông tin về mức độ gấp.
 * Đơn đặt trước có giờ hẹn nên phải làm đúng thứ tự thời gian, không phải cứ đơn nào
 * vào trước thì làm trước.
 */
public class KdsItemView {

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

    /** Còn bao nhiêu phút tới giờ hẹn; số âm nghĩa là đã trễ. */
    public long getMinutesToPickup() {
        return pickupTime == null ? Long.MAX_VALUE : DateTimeUtil.minutesBetween(DateTimeUtil.now(), pickupTime);
    }

    /** Sắp tới giờ hẹn mà món chưa xong — cần ưu tiên làm trước. */
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
