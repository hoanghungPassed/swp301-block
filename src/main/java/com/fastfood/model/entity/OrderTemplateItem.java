package com.fastfood.model.entity;

import java.math.BigDecimal;

/**
 * Một dòng trong mẫu đặt nhanh.
 * <p>
 * Chỉ lưu mã món và số lượng — <b>không lưu giá</b>. Giá đọc mới mỗi lần mở mẫu, cùng nguyên tắc
 * với giỏ hàng, nên mẫu lưu từ tháng trước không bao giờ đưa giá cũ vào đơn mới.
 */
public class OrderTemplateItem {

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

    /** Cùng định nghĩa với {@link Product#isOrderable()}. */
    public boolean isOrderable() {
        return "ACTIVE".equals(productStatus) && available;
    }

    public BigDecimal getLineTotal() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
