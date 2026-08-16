package com.fastfood.model.entity;

import java.math.BigDecimal;

/**
 * Một dòng món trong phiếu treo tại quầy.
 * <p>
 * Giá <b>không</b> lưu trong bảng: nó đọc mới từ bảng món mỗi lần mở phiếu ra, cùng nguyên tắc
 * với {@link CartItem}. Phiếu treo từ sáng mà trưa cửa hàng đổi giá thì thu ngân phải nhìn thấy
 * giá mới, chứ không phải giá lúc treo.
 */
public class PosHoldItem {

    private int holdItemId;
    private int holdId;
    private int productId;
    private int quantity;

    private String productName;
    private BigDecimal unitPrice;
    private boolean available;
    private String productStatus;

    public int getHoldItemId() { return holdItemId; }
    public void setHoldItemId(int holdItemId) { this.holdItemId = holdItemId; }

    public int getHoldId() { return holdId; }
    public void setHoldId(int holdId) { this.holdId = holdId; }

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

    /** Cùng định nghĩa với {@link Product#isOrderable()} — món ngừng bán hoặc hết hàng đều không bán được. */
    public boolean isOrderable() {
        return "ACTIVE".equals(productStatus) && available;
    }

    public BigDecimal getLineTotal() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
