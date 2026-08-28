package com.fastfood.service.customer;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.customer.ReviewDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.Dtos.ReviewSummary;
import com.fastfood.model.entity.MenuEntities.Review;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final ProductDAO productDAO = new ProductDAO();

    /** Lấy toàn bộ đánh giá của một món theo thứ tự mới nhất. */
    public List<Review> reviewsOf(int productId) {
        return Tx.read(con -> reviewDAO.findByProduct(con, productId));
    }

    /** Tính số lượt và điểm trung bình của một món. */
    public ReviewSummary summaryOf(int productId) {
        return Tx.read(con -> reviewDAO.summaryOf(con, productId));
    }

    /** Lấy đánh giá của customer hiện tại cho món, hoặc null nếu chưa đăng nhập/chưa đánh giá. */
    public Review myReview(int productId, Integer customerId) {
        if (customerId == null) {
            return null;
        }
        return Tx.read(con -> reviewDAO.findMine(con, productId, customerId));
    }

    /** Kiểm tra customer đã có đơn COMPLETED chứa món nên đủ điều kiện đánh giá hay chưa. */
    public boolean canReview(int productId, Integer customerId) {
        if (customerId == null) {
            return false;
        }
        return Tx.read(con -> reviewDAO.hasCompletedPurchase(con, customerId, productId));
    }

    /**
     * Validate số sao và nội dung, yêu cầu đã mua món thành công rồi tạo duy nhất một đánh giá
     * cho cặp customer-product.
     */
    public Review add(int productId, int customerId, int rating, String comment) {
        int diem = requireRating(rating);
        String text = optionalComment(comment);
        LocalDateTime now = DateTimeUtil.now();

        try {
            return Tx.write(con -> {
                if (productDAO.findById(con, productId) == null) {
                    throw new NotFoundException("Không tìm thấy món ăn.");
                }
                requirePurchase(con, customerId, productId);

                Review review = new Review();
                review.setProductId(productId);
                review.setCustomerId(customerId);
                review.setRating(diem);
                review.setComment(text);
                review.setCreatedAt(now);
                reviewDAO.insert(con, review);
                return review;
            });
        } catch (RuntimeException e) {
            if (!JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            throw new BusinessException("Bạn đã đánh giá món này rồi. "
                    + "Hãy sửa lại đánh giá cũ thay vì gửi thêm một cái nữa.");
        }
    }

    /** Sửa đánh giá sau khi kiểm tra review thuộc đúng người đang đăng nhập. */
    public void update(int reviewId, int customerId, int rating, String comment) {
        int diem = requireRating(rating);
        String text = optionalComment(comment);
        LocalDateTime now = DateTimeUtil.now();

        Tx.writeVoid(con -> {
            requireOwn(con, reviewId, customerId);
            reviewDAO.update(con, reviewId, customerId, diem, text, now);
        });
    }

    /** Xóa đánh giá sau khi kiểm tra review thuộc đúng người đang đăng nhập. */
    public void delete(int reviewId, int customerId) {
        Tx.writeVoid(con -> {
            requireOwn(con, reviewId, customerId);
            reviewDAO.delete(con, reviewId, customerId);
        });
    }

    /** Chặn đánh giá nếu customer chưa từng nhận món trong một đơn COMPLETED. */
    private void requirePurchase(Connection con, int customerId, int productId) throws SQLException {
        if (!reviewDAO.hasCompletedPurchase(con, customerId, productId)) {
            throw new BusinessException("Chỉ khách đã nhận món này mới đánh giá được. "
                    + "Đơn phải ở trạng thái đã giao.");
        }
    }

    /** Tải review và đối chiếu customerId để bảo vệ thao tác sửa/xóa. */
    private Review requireOwn(Connection con, int reviewId, int customerId) throws SQLException {
        Review review = reviewDAO.findById(con, reviewId);
        if (review == null) {
            throw new NotFoundException("Không tìm thấy đánh giá.");
        }
        if (review.getCustomerId() != customerId) {
            throw new BusinessException("Chỉ người viết mới sửa hoặc xoá được đánh giá này.");
        }
        return review;
    }

    /** Chỉ chấp nhận điểm đánh giá từ 1 đến 5 sao. */
    private int requireRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException("Vui lòng chọn số sao từ 1 tới 5.");
        }
        return rating;
    }

    /** Chuẩn hóa nhận xét rỗng thành null và giới hạn tối đa 1000 ký tự. */
    private String optionalComment(String comment) {
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw new ValidationException("Nhận xét tối đa " + MAX_COMMENT_LENGTH + " ký tự.");
        }
        return text;
    }
}
