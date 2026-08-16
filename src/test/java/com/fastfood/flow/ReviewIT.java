package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.ReviewSummary;
import com.fastfood.model.entity.Product;
import com.fastfood.model.entity.Review;
import com.fastfood.service.shared.MenuService;
import com.fastfood.service.customer.ReviewService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đánh giá món trên trang chi tiết — trang công khai, viết thì phải đã mua và đã nhận.
 * <p>
 * Các bài ghi dữ liệu dùng {@code customer2}: dữ liệu mẫu sinh đánh giá từ ba cặp
 * (khách, món) đầu tiên, và cả ba đều rơi vào {@code customer1} — khách đó không còn món nào
 * đã nhận mà chưa đánh giá. {@code customer2} có hai món đã nhận và chưa đánh giá cái nào.
 */
@DisplayName("Đánh giá món")
class ReviewIT extends IntegrationTestBase {

    /** Khách dùng để viết đánh giá trong các bài dưới đây — xem ghi chú ở đầu lớp. */
    private static final String KHACH = CUSTOMER_2;
    /** Một tài khoản khác, để kiểm chốt chặn "chỉ người viết mới sửa được". */
    private static final String NGUOI_LA = CUSTOMER_1;

    private final ReviewService reviewService = new ReviewService();

    /** Một món khách này đã mua và đã nhận, mà chưa đánh giá. */
    private int monDaNhanChuaDanhGia(int customerId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 oi.product_id FROM dbo.Orders o " +
                "JOIN dbo.OrderItem oi ON oi.order_id = o.order_id " +
                "WHERE o.customer_id = ? AND o.order_status = 'COMPLETED' " +
                "  AND NOT EXISTS (SELECT 1 FROM dbo.Review r " +
                "                  WHERE r.customer_id = o.customer_id AND r.product_id = oi.product_id) " +
                "ORDER BY oi.product_id", customerId);
        if (id == null) {
            throw new IllegalStateException("Khach nay da danh gia het cac mon da nhan");
        }
        return id;
    }

    /** Một món khách này CHƯA từng nhận. */
    private int monChuaNhan(int customerId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 p.product_id FROM dbo.Product p " +
                "WHERE NOT EXISTS (SELECT 1 FROM dbo.Orders o " +
                "                  JOIN dbo.OrderItem oi ON oi.order_id = o.order_id " +
                "                  WHERE o.customer_id = ? AND oi.product_id = p.product_id " +
                "                    AND o.order_status = 'COMPLETED') " +
                "ORDER BY p.product_id", customerId);
        if (id == null) {
            throw new IllegalStateException("Khach nay da nhan moi mon trong thuc don");
        }
        return id;
    }

    @Nested
    @DisplayName("Vòng đời một đánh giá")
    class Crud {

        @Test
        @DisplayName("Gửi, sửa, xoá — và xoá là xoá hẳn")
        void fullCycle() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);

            Review review = reviewService.add(mon, khach, 5, "  món nóng giòn  ");
            int id = review.getReviewId();
            assertEquals("món nóng giòn", review.getComment(), "Phải cắt khoảng trắng thừa");
            assertEquals("★★★★★", review.getStars());
            assertFalse(review.isEdited());

            reviewService.update(id, khach, 3, "hôm nay nguội hơn lần trước");
            Review sau = reviewService.myReview(mon, khach);
            assertEquals(3, sau.getRating());
            assertEquals("★★★☆☆", sau.getStars());
            assertTrue(sau.isEdited(), "Người đọc cần biết nội dung không còn là ấn tượng lần đầu");

            reviewService.delete(id, khach);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Review WHERE review_id = ?", id));
        }

        @Test
        @DisplayName("Điểm trung bình tính lại ngay sau khi thêm và sau khi xoá")
        void summaryFollowsReviews() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);
            ReviewSummary truoc = reviewService.summaryOf(mon);

            Review review = reviewService.add(mon, khach, 4, null);
            ReviewSummary sau_them = reviewService.summaryOf(mon);
            assertEquals(truoc.getCount() + 1, sau_them.getCount());

            reviewService.delete(review.getReviewId(), khach);
            ReviewSummary sau_xoa = reviewService.summaryOf(mon);
            assertEquals(truoc.getCount(), sau_xoa.getCount(),
                    "Phép trung bình tự tính lại sau khi dòng biến mất — đó là lý do bảng này "
                            + "xoá hẳn được mà không để lại con số sai");
        }

        @Test
        @DisplayName("Nhận xét để trống vẫn gửi được — có người chỉ muốn chấm sao")
        void commentIsOptional() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);

            Review review = reviewService.add(mon, khach, 5, "   ");

            assertNull(review.getComment());
            reviewService.delete(review.getReviewId(), khach);
        }
    }

    @Nested
    @DisplayName("Chỉ khách đã nhận món mới đánh giá được")
    class VerifiedPurchase {

        @Test
        @DisplayName("Chưa từng nhận món thì không đánh giá được")
        void neverReceivedCannotReview() {
            int khach = userId(KHACH);
            int mon = monChuaNhan(khach);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reviewService.add(mon, khach, 5, "tôi đoán là ngon"));

            assertTrue(e.getMessage().contains("đã nhận món"), "Nhận được: " + e.getMessage());
            assertFalse(reviewService.canReview(mon, khach),
                    "Màn hình phải biết trước để không hiện ô nhập rồi mới từ chối");
        }

        @Test
        @DisplayName("Đơn chưa giao xong thì chưa đủ điều kiện — đặt hàng không phải là đã ăn")
        void orderMustBeCompleted() {
            int khach = userId(KHACH);
            Integer mon_dang_cho = scalar(Integer.class,
                    "SELECT TOP 1 oi.product_id FROM dbo.Orders o " +
                    "JOIN dbo.OrderItem oi ON oi.order_id = o.order_id " +
                    "WHERE o.customer_id = ? AND o.order_status <> 'COMPLETED' " +
                    "  AND NOT EXISTS (SELECT 1 FROM dbo.Orders o2 " +
                    "                  JOIN dbo.OrderItem oi2 ON oi2.order_id = o2.order_id " +
                    "                  WHERE o2.customer_id = o.customer_id " +
                    "                    AND oi2.product_id = oi.product_id " +
                    "                    AND o2.order_status = 'COMPLETED') " +
                    "ORDER BY oi.product_id", khach);
            org.junit.jupiter.api.Assumptions.assumeTrue(mon_dang_cho != null,
                    "Du lieu mau khong con don chua giao nao de kiem chung");

            assertFalse(reviewService.canReview(mon_dang_cho, khach));
            assertThrows(BusinessException.class,
                    () -> reviewService.add(mon_dang_cho, khach, 5, "chưa nhận nhưng cứ chấm"));
        }

        @Test
        @DisplayName("Mỗi khách một đánh giá cho một món")
        void oneReviewPerCustomerPerProduct() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);
            Review review = reviewService.add(mon, khach, 5, null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> reviewService.add(mon, khach, 1, "bấm gửi lần nữa"));

            assertTrue(e.getMessage().contains("đã đánh giá món này rồi"),
                    "Nhận được: " + e.getMessage());
            assertEquals(1, count("SELECT COUNT(*) FROM dbo.Review " +
                    "WHERE customer_id = ? AND product_id = ?", khach, mon),
                    "Không có ràng buộc này thì bấm hai lần là tự đẩy điểm trung bình lên");
            reviewService.delete(review.getReviewId(), khach);
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Chỉ người viết mới sửa hoặc xoá được")
        void onlyAuthorMayEdit() {
            int khach = userId(KHACH);
            int nguoi_la = userId(NGUOI_LA);
            int mon = monDaNhanChuaDanhGia(khach);
            int id = reviewService.add(mon, khach, 4, "của tôi").getReviewId();

            assertThrows(BusinessException.class,
                    () -> reviewService.update(id, nguoi_la, 1, "sửa trộm"));
            assertThrows(BusinessException.class, () -> reviewService.delete(id, nguoi_la));

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.Review WHERE review_id = ?", id));
            reviewService.delete(id, khach);
        }

        @Test
        @DisplayName("Số sao ngoài khoảng 1–5 và món không tồn tại đều bị từ chối")
        void invalidInput() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);

            assertThrows(ValidationException.class, () -> reviewService.add(mon, khach, 0, null));
            assertThrows(ValidationException.class, () -> reviewService.add(mon, khach, 6, null));
            assertThrows(ValidationException.class, () -> reviewService.add(mon, khach, -1, null));
            assertThrows(NotFoundException.class, () -> reviewService.add(999_999, khach, 5, null));

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Review " +
                    "WHERE customer_id = ? AND product_id = ?", khach, mon));
        }

        @Test
        @DisplayName("Nhận xét quá dài bị chặn ở tầng dịch vụ trước khi chạm cơ sở dữ liệu")
        void commentTooLong() {
            int khach = userId(KHACH);
            int mon = monDaNhanChuaDanhGia(khach);
            StringBuilder qua_dai = new StringBuilder();
            for (int i = 0; i < 1100; i++) {
                qua_dai.append('a');
            }

            assertThrows(ValidationException.class,
                    () -> reviewService.add(mon, khach, 5, qua_dai.toString()));
        }
    }

    @Nested
    @DisplayName("Trang công khai")
    class PublicPage {

        @Test
        @DisplayName("Người chưa đăng nhập đọc được đánh giá nhưng không viết được")
        void guestReadsButCannotWrite() {
            int mon = scalar(Integer.class,
                    "SELECT TOP 1 product_id FROM dbo.Review ORDER BY review_id");

            assertFalse(reviewService.reviewsOf(mon).isEmpty(),
                    "Đọc đánh giá là lúc người ta cần nhất — trước khi quyết định có đặt hay không");
            assertNull(reviewService.myReview(mon, null),
                    "Khách chưa đăng nhập trả về rỗng chứ không phải một lỗi");
            assertFalse(reviewService.canReview(mon, null));
        }

        @Test
        @DisplayName("Món chưa ai đánh giá cho ra bản tóm tắt rỗng, không phải chia cho 0")
        void productWithNoReviews() {
            int mon_khong_ai_danh_gia = scalar(Integer.class,
                    "SELECT TOP 1 p.product_id FROM dbo.Product p " +
                    "WHERE NOT EXISTS (SELECT 1 FROM dbo.Review r WHERE r.product_id = p.product_id) " +
                    "ORDER BY p.product_id");

            ReviewSummary summary = reviewService.summaryOf(mon_khong_ai_danh_gia);

            assertTrue(summary.isEmptySummary());
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getAverage()));
            assertEquals("☆☆☆☆☆", summary.getStars());
        }

        @Test
        @DisplayName("Thực đơn giữ đủ món kể cả khi phần lớn chưa ai đánh giá")
        void menuKeepsUnratedProducts() {
            int dung_ra_phai_co = count(
                    "SELECT COUNT(*) FROM dbo.Product p " +
                    "JOIN dbo.Category c ON c.category_id = p.category_id " +
                    "WHERE p.status = 'ACTIVE' AND p.is_available = 1 AND c.status = 'ACTIVE'");

            List<Product> menu = new MenuService().browse(null, null);

            assertEquals(dung_ra_phai_co, menu.size(),
                    "Nối bảng điểm bằng JOIN thay vì LEFT JOIN sẽ làm biến mất mọi món chưa ai "
                            + "chấm — tức là gần hết thực đơn của một cửa hàng mới mở");
            assertTrue(menu.stream().anyMatch(p -> !p.isRated()),
                    "Phải còn món chưa đánh giá thì bài test này mới chứng minh được điều gì");
        }

        @Test
        @DisplayName("Điểm trên thực đơn khớp với điểm ở trang chi tiết")
        void menuRatingMatchesDetail() {
            Product co_diem = new MenuService().browse(null, null).stream()
                    .filter(Product::isRated).findFirst().orElseThrow(
                            () -> new IllegalStateException("Du lieu mau khong con mon nao co danh gia"));

            ReviewSummary tu_trang_chi_tiet = reviewService.summaryOf(co_diem.getProductId());

            assertEquals(tu_trang_chi_tiet.getCount(), co_diem.getRatingCount());
            assertEquals(0, tu_trang_chi_tiet.getAverageRounded().compareTo(co_diem.getRatingRounded()),
                    "Hai con số cho cùng một món mà lệch nhau thì khách bấm vào sẽ thấy khác lúc "
                            + "đang lướt");
            assertEquals(tu_trang_chi_tiet.getStars(), co_diem.getRatingStars(),
                    "Chuỗi sao cùng đi qua StarRating nên không có cách nào lệch");
        }

        @Test
        @DisplayName("Món chưa ai chấm để trống điểm chứ không phải 0 sao")
        void unratedProductStaysBlank() {
            Product chua_ai_cham = new MenuService().browse(null, null).stream()
                    .filter(p -> !p.isRated()).findFirst().orElseThrow();

            assertEquals(0, chua_ai_cham.getRatingCount());
            assertNull(chua_ai_cham.getRatingAverage(),
                    "LEFT JOIN trả về rỗng, và màn hình dựa vào đó để im lặng thay vì trưng ra "
                            + "một hàng sao rỗng trông như món bị chê");
        }

        @Test
        @DisplayName("Dữ liệu mẫu có đánh giá, và mọi đánh giá mẫu đều là của khách đã nhận món")
        void seedReviewsAreAllVerified() {
            List<Review> tat_ca = reviewService.reviewsOf(scalar(Integer.class,
                    "SELECT TOP 1 product_id FROM dbo.Review ORDER BY review_id"));
            assertNotNull(tat_ca);

            int so_danh_gia_khong_hop_le = count(
                    "SELECT COUNT(*) FROM dbo.Review r WHERE NOT EXISTS (" +
                    "  SELECT 1 FROM dbo.Orders o JOIN dbo.OrderItem oi ON oi.order_id = o.order_id " +
                    "  WHERE o.customer_id = r.customer_id AND oi.product_id = r.product_id " +
                    "    AND o.order_status = 'COMPLETED')");

            assertEquals(0, so_danh_gia_khong_hop_le,
                    "Dữ liệu mẫu phải tuân đúng quy tắc mà chính hệ thống áp — nếu không, "
                            + "nó đang trưng ra một trạng thái người dùng thật không tạo được");
        }
    }
}
