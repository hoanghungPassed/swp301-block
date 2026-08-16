package com.fastfood.service.customer;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.customer.ReviewDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.model.dto.ReviewSummary;
import com.fastfood.model.entity.Review;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Đánh giá món của khách, hiện trên trang chi tiết món.
 * <p>
 * <b>Chỉ khách đã mua và đã nhận mới đánh giá được.</b> Đây là chốt chặn đáng giá nhất của cả
 * tính năng: không có nó thì bảng đánh giá chỉ là một ô nhập chữ mà bất kỳ tài khoản nào cũng
 * gõ vào được, và điểm trung bình của món không nói lên điều gì. Điều kiện là đơn ở trạng thái
 * đã giao — đơn đang nấu hay đơn đã huỷ đều chưa cho ai cơ sở để nói món ngon hay dở.
 * <p>
 * <b>Mỗi khách một đánh giá cho một món</b>, chốt bằng {@code UQ_Review_cust_product}. Không có
 * nó, một khách bấm gửi hai lần là tự đẩy điểm trung bình của món lên.
 * <p>
 * <b>Điểm trung bình chỉ hiện ở trang chi tiết, cố ý chưa đưa lên thực đơn.</b> Thêm một phép
 * tính trung bình vào {@code ProductDAO.findMenu} là thêm gánh nặng cho truy vấn chạy nhiều nhất
 * của trang công khai. Đây là việc để lại, không phải việc quên.
 * <p>
 * Xoá hẳn khỏi bảng: đây là nội dung của chính khách, không dính tiền, và không bản ghi nào trỏ
 * tới ngoài phép tính trung bình — vốn tự tính lại sau khi dòng biến mất.
 */
public class ReviewService {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ReviewDAO reviewDAO = new ReviewDAO();
    private final ProductDAO productDAO = new ProductDAO();

    // ============================================================ đọc

    public List<Review> reviewsOf(int productId) {
        return Tx.read(con -> reviewDAO.findByProduct(con, productId));
    }

    public ReviewSummary summaryOf(int productId) {
        return Tx.read(con -> reviewDAO.summaryOf(con, productId));
    }

    /**
     * Đánh giá của chính khách này cho món này, hoặc null.
     * <p>
     * Khách chưa đăng nhập trả về null chứ không ném lỗi: chi tiết món là trang công khai, và
     * người xem không đăng nhập là chuyện bình thường.
     */
    public Review myReview(int productId, Integer customerId) {
        if (customerId == null) {
            return null;
        }
        return Tx.read(con -> reviewDAO.findMine(con, productId, customerId));
    }

    /** Khách này có đủ điều kiện đánh giá món này không — dùng để quyết định hiện ô nhập hay không. */
    public boolean canReview(int productId, Integer customerId) {
        if (customerId == null) {
            return false;
        }
        return Tx.read(con -> reviewDAO.hasCompletedPurchase(con, customerId, productId));
    }

    // ============================================================ ghi

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

    /**
     * Sửa lại đánh giá của mình.
     * <p>
     * Không giới hạn thời gian sửa: món có thể dở đi hoặc ngon lên theo ca bếp, và một đánh giá
     * bị khoá cứng sau vài ngày chỉ khiến khách bỏ luôn không sửa. Bản ghi mang cờ đã sửa để
     * người đọc biết nội dung không còn là ấn tượng lần đầu.
     */
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

    // ============================================================ dùng chung

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
