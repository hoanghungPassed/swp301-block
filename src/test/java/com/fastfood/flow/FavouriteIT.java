package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.entity.Favourite;
import com.fastfood.service.customer.FavouriteService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Món quen của khách — khối quản lý nằm ngay trên trang thực đơn. */
@DisplayName("Món quen của khách")
class FavouriteIT extends IntegrationTestBase {

    private final FavouriteService favouriteService = new FavouriteService();

    /** Một món chưa nằm trong danh sách món quen của khách này — tránh vấp ràng buộc duy nhất. */
    private int monChuaDanhDau(int customerId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 p.product_id FROM dbo.Product p " +
                "WHERE NOT EXISTS (SELECT 1 FROM dbo.Favourite f " +
                "                  WHERE f.product_id = p.product_id AND f.customer_id = ?) " +
                "ORDER BY p.product_id", customerId);
        if (id == null) {
            throw new IllegalStateException("Khach nay da danh dau het moi mon");
        }
        return id;
    }

    @Nested
    @DisplayName("Vòng đời một món quen")
    class Crud {

        @Test
        @DisplayName("Đánh dấu, sửa ghi chú, rồi bỏ — và bỏ là xoá hẳn")
        void fullCycle() {
            int khach = userId(CUSTOMER_2);
            int mon = monChuaDanhDau(khach);

            Favourite fav = favouriteService.add(khach, mon, "  ít cay  ");
            int id = fav.getFavouriteId();
            assertEquals("ít cay", fav.getNote(), "Phải cắt khoảng trắng thừa");
            assertFalse(fav.isEdited());

            favouriteService.updateNote(id, khach, "ít cay, thêm một gói tương");
            Favourite sau = favouriteService.findOwn(id, khach);
            assertEquals("ít cay, thêm một gói tương", sau.getNote());
            assertTrue(sau.isEdited());

            favouriteService.remove(id, khach);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Favourite WHERE favourite_id = ?", id));
        }

        @Test
        @DisplayName("Ghi chú để trống thì lưu thành rỗng chứ không thành chuỗi trắng")
        void blankNoteBecomesNull() {
            int khach = userId(CUSTOMER_2);
            int mon = monChuaDanhDau(khach);

            Favourite fav = favouriteService.add(khach, mon, "   ");

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.Favourite " +
                    "WHERE favourite_id = ? AND note IS NOT NULL", fav.getFavouriteId()));
            favouriteService.remove(fav.getFavouriteId(), khach);
        }

        @Test
        @DisplayName("Bỏ đánh dấu ngay từ lưới thực đơn, nơi chỉ biết mã món")
        void removeByProductFromGrid() {
            int khach = userId(CUSTOMER_2);
            int mon = monChuaDanhDau(khach);
            favouriteService.add(khach, mon, null);
            assertTrue(favouriteService.favouriteProductIds(khach).contains(mon));

            favouriteService.removeByProduct(khach, mon);

            assertFalse(favouriteService.favouriteProductIds(khach).contains(mon));
        }

        @Test
        @DisplayName("Bỏ một món chưa từng đánh dấu không bị coi là lỗi")
        void removingUnmarkedIsNotAnError() {
            int khach = userId(CUSTOMER_2);
            int mon = monChuaDanhDau(khach);

            favouriteService.removeByProduct(khach, mon);

            assertFalse(favouriteService.favouriteProductIds(khach).contains(mon),
                    "Người bấm nút bỏ muốn món đó không còn trong danh sách — và sau lệnh này "
                            + "thì đúng như vậy, nên không có gì để báo lỗi");
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Mỗi khách một dấu cho một món")
        void oneMarkPerCustomerPerProduct() {
            int khach = userId(CUSTOMER_2);
            int mon = monChuaDanhDau(khach);
            Favourite fav = favouriteService.add(khach, mon, null);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> favouriteService.add(khach, mon, "bấm đúp"));

            assertTrue(e.getMessage().contains("đã có trong danh sách"), "Nhận được: " + e.getMessage());
            favouriteService.remove(fav.getFavouriteId(), khach);
        }

        @Test
        @DisplayName("Không đụng được vào món quen của tài khoản khác")
        void cannotTouchAnotherCustomerFavourite() {
            int chu = userId(CUSTOMER_2);
            int nguoi_la = userId(CUSTOMER_1);
            int mon = monChuaDanhDau(chu);
            int id = favouriteService.add(chu, mon, null).getFavouriteId();

            assertThrows(BusinessException.class, () -> favouriteService.findOwn(id, nguoi_la));
            assertThrows(BusinessException.class, () -> favouriteService.remove(id, nguoi_la));
            assertThrows(BusinessException.class,
                    () -> favouriteService.updateNote(id, nguoi_la, "sửa trộm"));

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.Favourite WHERE favourite_id = ?", id));
            favouriteService.remove(id, chu);
        }

        @Test
        @DisplayName("Món không tồn tại và ghi chú quá dài đều bị từ chối")
        void invalidInput() {
            int khach = userId(CUSTOMER_2);

            assertThrows(NotFoundException.class, () -> favouriteService.add(khach, 999_999, null));

            StringBuilder qua_dai = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                qua_dai.append('a');
            }
            assertThrows(ValidationException.class,
                    () -> favouriteService.add(khach, monChuaDanhDau(khach), qua_dai.toString()));
        }
    }

    @Nested
    @DisplayName("Trang công khai")
    class PublicPage {

        @Test
        @DisplayName("Người chưa đăng nhập nhận về tập rỗng, không phải một lỗi")
        void guestGetsEmptySet() {
            Set<Integer> ids = favouriteService.favouriteProductIds(null);

            assertTrue(ids.isEmpty(), "Thực đơn là trang công khai — người xem không đăng nhập "
                    + "là chuyện bình thường chứ không phải lỗi");
        }

        @Test
        @DisplayName("Món đang không phục vụ vẫn đánh dấu được, và được nêu rõ trong danh sách")
        void unavailableProductStaysMarkable() {
            int khach = userId(CUSTOMER_2);
            int het_hang = unavailableProductId();
            // Dữ liệu mẫu có thể đã đánh dấu sẵn món này cho khách khác, nhưng không cho khách 2.
            Favourite fav = favouriteService.add(khach, het_hang, "khi nào có lại thì báo tôi");

            Favourite trong_danh_sach = favouriteService.listOf(khach).stream()
                    .filter(f -> f.getProductId() == het_hang).findFirst().orElseThrow();
            assertFalse(trong_danh_sach.isOrderable(),
                    "Danh sách phải nói ra món nào đang không phục vụ — lưới thực đơn không "
                            + "hiện món đó nên khách sẽ không tự nhận ra");

            favouriteService.remove(fav.getFavouriteId(), khach);
        }

        @Test
        @DisplayName("Dữ liệu mẫu có sẵn món quen để khối này không mở ra trống")
        void seedHasFavourites() {
            List<Favourite> cua_khach_1 = favouriteService.listOf(userId(CUSTOMER_1));

            assertTrue(cua_khach_1.size() >= 2, "Nhận được: " + cua_khach_1.size());
            assertTrue(cua_khach_1.stream().anyMatch(f -> f.getNote() != null),
                    "Ít nhất một món quen mẫu phải có ghi chú, vì ghi chú mới là phần "
                            + "làm nên chữ Sửa của màn hình này");
            assertTrue(cua_khach_1.stream().anyMatch(f -> !f.isOrderable()),
                    "Dữ liệu mẫu cố ý giữ một món quen đang hết hàng để dựng sẵn dải cảnh báo");
        }
    }
}
