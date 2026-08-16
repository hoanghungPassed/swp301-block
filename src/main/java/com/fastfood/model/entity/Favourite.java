package com.fastfood.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Món quen của một khách — đánh dấu yêu thích kèm ghi chú riêng.
 * <p>
 * Ghi chú mới là phần đáng giá: chỉ đánh dấu thì bảng này chỉ có thêm và bỏ, còn "ít cay",
 * "không hành", "nhiều đá" mới là thứ khách muốn lưu lại và sửa đi sửa lại.
 */
public class Favourite {

    private int favouriteId;
    private int customerId;
    private int productId;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String productName;
    private String categoryName;
    private String imageUrl;
    private BigDecimal price;
    private boolean available;
    private String productStatus;

    public int getFavouriteId() { return favouriteId; }
    public void setFavouriteId(int favouriteId) { this.favouriteId = favouriteId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getProductStatus() { return productStatus; }
    public void setProductStatus(String productStatus) { this.productStatus = productStatus; }

    public boolean isEdited() { return updatedAt != null; }

    /** Cùng định nghĩa với {@link Product#isOrderable()}. */
    public boolean isOrderable() {
        return "ACTIVE".equals(productStatus) && available;
    }
}
