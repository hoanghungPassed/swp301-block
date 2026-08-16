package com.fastfood.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Mẫu đặt nhanh của khách — một danh sách món có tên, nạp thẳng vào giỏ khi cần.
 * <p>
 * Khác giỏ hàng ở vòng đời: giỏ là <b>một</b> bản nháp đang sống, còn mẫu là <b>nhiều</b> danh
 * sách đã đặt tên nằm chờ. Khách có "Bữa trưa quen" và "Đặt cho cả phòng" cùng lúc, và cả hai
 * đều chỉ nạp vào giỏ chứ tự chúng không thành đơn.
 */
public class OrderTemplate {

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

    /**
     * Tạm tính theo giá <b>hiện hành</b>, không phải giá lúc lưu mẫu.
     * <p>
     * Món đã ngừng bán vẫn được cộng vào: giấu nó đi thì con số nhìn có vẻ đúng trong khi mẫu
     * thật ra không nạp được đủ, và khách chỉ phát hiện ra sau khi bấm.
     */
    public BigDecimal getEstimatedTotal() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderTemplateItem item : items) {
            sum = sum.add(item.getLineTotal());
        }
        return sum;
    }

    /** Mẫu có món không còn phục vụ — màn hình cảnh báo trước khi khách bấm nạp. */
    public boolean isAnyUnavailable() {
        for (OrderTemplateItem item : items) {
            if (!item.isOrderable()) {
                return true;
            }
        }
        return false;
    }
}
