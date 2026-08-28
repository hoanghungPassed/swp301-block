package com.fastfood.model.entity;

import com.fastfood.common.util.StarRating;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public final class MenuEntities {

    private MenuEntities() {
    }

    public static class Category {
        private int categoryId;
        private String name;
        private String status;
        private int displayOrder;

        private int productCount;

        public int getCategoryId() { return categoryId; }
        public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

        public int getProductCount() { return productCount; }
        public void setProductCount(int productCount) { this.productCount = productCount; }

        /** Danh mục chỉ xuất hiện trên menu Customer khi đang ACTIVE. */
        public boolean isActive() { return "ACTIVE".equals(status); }
    }

    public static class Product {
        private int productId;
        private int categoryId;
        private String name;
        private String description;
        private BigDecimal price;
        private String imageUrl;
        private boolean available;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String categoryName;
        private BigDecimal ratingAverage;
        private int ratingCount;

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }

        public int getCategoryId() { return categoryId; }
        public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        /** Sản phẩm đặt được khi vừa ACTIVE vừa được bật available. */
        public boolean isOrderable() { return "ACTIVE".equals(status) && available; }

        public BigDecimal getRatingAverage() { return ratingAverage; }
        public void setRatingAverage(BigDecimal ratingAverage) { this.ratingAverage = ratingAverage; }

        public int getRatingCount() { return ratingCount; }
        public void setRatingCount(int ratingCount) { this.ratingCount = ratingCount; }

        /** Cho JSP phân biệt chưa có đánh giá với điểm trung bình bằng 0. */
        public boolean isRated() { return ratingCount > 0 && ratingAverage != null; }

        /** Làm tròn điểm trung bình một chữ số để hiển thị nhất quán. */
        public BigDecimal getRatingRounded() {
            return ratingAverage == null
                    ? BigDecimal.ZERO
                    : ratingAverage.setScale(1, RoundingMode.HALF_UP);
        }

        /** Chuyển điểm trung bình thành chuỗi sao cho thẻ sản phẩm. */
        public String getRatingStars() { return StarRating.of(ratingAverage); }
    }

    public static class Review {

        private int reviewId;
        private int productId;
        private int customerId;
        private int rating;
        private String comment;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private String customerName;
        private String productName;

        public int getReviewId() { return reviewId; }
        public void setReviewId(int reviewId) { this.reviewId = reviewId; }

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }

        public int getCustomerId() { return customerId; }
        public void setCustomerId(int customerId) { this.customerId = customerId; }

        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        /** Cho giao diện gắn nhãn đánh giá đã chỉnh sửa. */
        public boolean isEdited() { return updatedAt != null; }

        /** Chuyển số sao khách chọn thành biểu tượng năm sao. */
        public String getStars() {
            return StarRating.of(rating);
        }
    }

    public static class Favourite {

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

        /** Cho giao diện biết ghi chú yêu thích đã được chỉnh sửa. */
        public boolean isEdited() { return updatedAt != null; }

        /** Kiểm tra món yêu thích còn có thể đưa trở lại giỏ hay không. */
        public boolean isOrderable() {
            return "ACTIVE".equals(productStatus) && available;
        }
    }

}
