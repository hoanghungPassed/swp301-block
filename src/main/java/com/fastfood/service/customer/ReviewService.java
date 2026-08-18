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

    public List<Review> reviewsOf(int productId) {
        return Tx.read(con -> reviewDAO.findByProduct(con, productId));
    }

    public ReviewSummary summaryOf(int productId) {
        return Tx.read(con -> reviewDAO.summaryOf(con, productId));
    }

    public Review myReview(int productId, Integer customerId) {
        if (customerId == null) {
            return null;
        }
        return Tx.read(con -> reviewDAO.findMine(con, productId, customerId));
    }

    public boolean canReview(int productId, Integer customerId) {
        if (customerId == null) {
            return false;
        }
        return Tx.read(con -> reviewDAO.hasCompletedPurchase(con, customerId, productId));
    }

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

    public void update(int reviewId, int customerId, int rating, String comment) {
        int diem = requireRating(rating);
        String text = optionalComment(comment);
        LocalDateTime now = DateTimeUtil.now();

        Tx.writeVoid(con -> {
            requireOwn(con, reviewId, customerId);
            reviewDAO.update(con, reviewId, customerId, diem, text, now);
        });
    }

    public void delete(int reviewId, int customerId) {
        Tx.writeVoid(con -> {
            requireOwn(con, reviewId, customerId);
            reviewDAO.delete(con, reviewId, customerId);
        });
    }

    private void requirePurchase(Connection con, int customerId, int productId) throws SQLException {
        if (!reviewDAO.hasCompletedPurchase(con, customerId, productId)) {
            throw new BusinessException("Chỉ khách đã nhận món này mới đánh giá được. "
                    + "Đơn phải ở trạng thái đã giao.");
        }
    }

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

    private int requireRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException("Vui lòng chọn số sao từ 1 tới 5.");
        }
        return rating;
    }

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
