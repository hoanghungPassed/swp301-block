package com.fastfood.model.entity;

import java.math.BigDecimal;

/**
 * Một dòng trong giỏ hàng.
 * Giá lấy trực tiếp từ bảng Product chứ không lưu lại — giỏ chỉ là bản nháp,
 * giá thật được chốt và sao chép sang OrderItem lúc đặt hàng.
 */
public class CartItem {
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

    /** Món trong giỏ có còn đặt được không — kiểm tra lại ngay trước khi thanh toán. */
    public boolean isOrderable() {
        return available && "ACTIVE".equals(productStatus) && "ACTIVE".equals(categoryStatus);
    }
}
