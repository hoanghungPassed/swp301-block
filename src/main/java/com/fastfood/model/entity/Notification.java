package com.fastfood.model.entity;

import java.time.LocalDateTime;

/** Tin báo gửi cho khách: đơn đã xác nhận, và món đã sẵn sàng. */
public class Notification {

    private int notificationId;
    private Integer userId;
    private int orderId;
    private String channel;
    private String eventType;
    private String content;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    /** Tin mới thì hiện đậm và được đếm vào huy hiệu trên thanh điều hướng. */
    public boolean isUnread() { return readAt == null; }

    /**
     * Gửi hỏng thì nói ra ngay trên dòng tin.
     * <p>
     * Kênh gửi ngoài ứng dụng có thể hỏng — địa chỉ thư sai, dịch vụ gửi chết. Không phân biệt
     * thì khách đọc được tin ở đây và đinh ninh mình cũng đã nhận được thư, rồi rời màn hình mà
     * không biết là từ giờ tin chỉ tới được đúng một nơi này.
     */
    public boolean isFailed() { return "FAILED".equals(status); }
}
