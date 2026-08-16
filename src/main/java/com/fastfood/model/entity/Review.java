package com.fastfood.model.entity;

import com.fastfood.common.util.StarRating;

import java.time.LocalDateTime;

/**
 * Đánh giá một món của một khách.
 * <p>
 * Chỉ khách <b>đã mua và đã nhận</b> món đó mới đánh giá được. Điều kiện ấy không chặn được ở
 * tầng dữ liệu vì nó cần ghép qua bảng đơn hàng, nên chốt chặn nằm ở
 * {@code ReviewService} — xem ghi chú ở bảng {@code dbo.Review} trong tệp lược đồ.
 */
public class Review {

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

    public boolean isEdited() { return updatedAt != null; }

    /**
     * Chuỗi sao đặc và sao rỗng, ví dụ {@code ★★★★☆}.
     * <p>
     * Dựng ở đây chứ không lặp một vòng {@code c:forEach} trong JSP: điểm số là dữ liệu, còn
     * vòng lặp đếm tới 5 trong trang hiển thị là chỗ dễ lệch mà không bài test nào chạm tới.
     */
    public String getStars() {
        return StarRating.of(rating);
    }
}
