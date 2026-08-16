package com.fastfood.flow;

import com.fastfood.model.entity.Product;
import com.fastfood.service.shared.MenuService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lọc và sắp xếp thực đơn — những gì trang đầu tiên khách nhìn thấy thật sự làm được.
 * <p>
 * <b>Vì sao phần sắp xếp cần test riêng.</b> Mệnh đề {@code ORDER BY} không nhận tham số
 * {@code ?}, nên thứ tự bắt buộc phải ghép chữ vào câu lệnh. Chỗ ghép chữ duy nhất trong cả
 * lớp DAO ấy là chỗ dễ mở ra lỗ tiêm SQL nhất, và cũng là chỗ mà một mã sắp xếp gõ sai sẽ
 * lặng lẽ trả về thứ tự khác chứ không báo lỗi gì.
 */
@DisplayName("Duyệt thực đơn")
class MenuBrowseIT extends IntegrationTestBase {

    private final MenuService menuService = new MenuService();

    private List<BigDecimal> giaTheoThuTu(String sort) {
        return menuService.browse(null, null, sort).stream().map(Product::getPrice).toList();
    }

    @Nested
    @DisplayName("Sắp xếp")
    class Sorting {

        @Test
        @DisplayName("Giá thấp → cao và giá cao → thấp là hai chiều ngược nhau của cùng một danh sách")
        void priceSortsRunBothWays() {
            List<BigDecimal> tang = giaTheoThuTu("PRICE_ASC");
            List<BigDecimal> giam = giaTheoThuTu("PRICE_DESC");

            assertFalse(tang.isEmpty(), "Du lieu mau khong con mon nao dang ban");
            assertEquals(tang.size(), giam.size(), "Đổi thứ tự không được làm mất hay thêm món");
            for (int i = 1; i < tang.size(); i++) {
                assertTrue(tang.get(i - 1).compareTo(tang.get(i)) <= 0,
                        "Giá tăng dần bị đứt ở vị trí " + i);
                assertTrue(giam.get(i - 1).compareTo(giam.get(i)) >= 0,
                        "Giá giảm dần bị đứt ở vị trí " + i);
            }
        }

        @Test
        @DisplayName("Xếp theo đánh giá thì món chưa ai chấm nằm cuối, không phải đầu")
        void unratedProductsSinkToTheBottom() {
            List<Product> theoDiem = menuService.browse(null, null, "RATING");

            int monCoDiemCuoiCung = -1;
            int monChuaChamDauTien = Integer.MAX_VALUE;
            for (int i = 0; i < theoDiem.size(); i++) {
                if (theoDiem.get(i).isRated()) {
                    monCoDiemCuoiCung = i;
                } else if (i < monChuaChamDauTien) {
                    monChuaChamDauTien = i;
                }
            }
            assertTrue(monCoDiemCuoiCung >= 0, "Du lieu mau khong con mon nao co danh gia");
            assertTrue(monChuaChamDauTien < Integer.MAX_VALUE,
                    "Du lieu mau khong con mon nao chua duoc cham — bai test nay khong chung minh duoc gi");
            assertTrue(monCoDiemCuoiCung < monChuaChamDauTien,
                    "SQL Server xếp NULL lên trước ở thứ tự giảm dần, nên thiếu khoá phụ thì "
                            + "\"đánh giá cao nhất\" lại mở đầu bằng một dãy món chưa ai chấm");

            for (int i = 1; i <= monCoDiemCuoiCung; i++) {
                assertTrue(theoDiem.get(i - 1).getRatingAverage()
                                .compareTo(theoDiem.get(i).getRatingAverage()) >= 0,
                        "Điểm giảm dần bị đứt ở vị trí " + i);
            }
        }

        @Test
        @DisplayName("Mã sắp xếp lạ rơi về thứ tự mặc định chứ không lọt vào câu lệnh")
        void unknownSortCodeFallsBackInsteadOfInjecting() {
            List<BigDecimal> macDinh = giaTheoThuTu("DEFAULT");

            /* Nếu tham số được ghép thẳng vào ORDER BY thì hai dòng dưới đây làm hỏng câu
               lệnh và ném SQLException. Rơi về mặc định mới là hành vi đúng. */
            assertEquals(macDinh, giaTheoThuTu("p.price; DROP TABLE dbo.Product--"));
            assertEquals(macDinh, giaTheoThuTu("khong-co-ma-nay"));
            assertEquals(macDinh, giaTheoThuTu(null),
                    "Địa chỉ không có tham số sort cũng phải cho đúng thứ tự mặc định");

            assertEquals("DEFAULT", menuService.sortOrDefault("khong-co-ma-nay"),
                    "Trang tô ô đang chọn theo giá trị này, nên nó phải là mã thật sự được dùng");
            assertEquals("PRICE_ASC", menuService.sortOrDefault("PRICE_ASC"));
        }

        @Test
        @DisplayName("Đổi thứ tự không đổi tập món trả về")
        void sortingNeverChangesWhichProductsAppear() {
            List<Integer> macDinh = menuService.browse(null, null, "DEFAULT").stream()
                    .map(Product::getProductId).sorted().toList();

            for (String sort : List.of("PRICE_ASC", "PRICE_DESC", "RATING")) {
                List<Integer> khac = menuService.browse(null, null, sort).stream()
                        .map(Product::getProductId).sorted().toList();
                assertEquals(macDinh, khac,
                        "Thứ tự " + sort + " làm mất hoặc thêm món — nhiều khả năng do phép nối "
                                + "bảng điểm bị đổi từ LEFT JOIN thành JOIN");
            }
        }
    }

    @Nested
    @DisplayName("Lọc")
    class Filtering {

        @Test
        @DisplayName("Lọc theo nhóm món và tìm theo từ khoá dùng chung được với nhau")
        void categoryAndKeywordCombine() {
            Product mau = menuService.browse(null, null).get(0);
            String motPhanTen = mau.getName().substring(0, Math.min(3, mau.getName().length()));

            List<Product> ket_qua = menuService.browse(mau.getCategoryId(), motPhanTen, "PRICE_ASC");

            assertFalse(ket_qua.isEmpty(), "Ít nhất chính món lấy làm mẫu phải còn trong kết quả");
            assertTrue(ket_qua.stream().anyMatch(p -> p.getProductId() == mau.getProductId()));
            for (Product p : ket_qua) {
                assertEquals(mau.getCategoryId(), p.getCategoryId(),
                        "Nhãn nhóm món trên trang mang theo cả từ khoá, nên hai điều kiện phải "
                                + "cùng có hiệu lực chứ không phải cái sau đè cái trước");
                assertTrue(p.getName().toLowerCase().contains(motPhanTen.toLowerCase()));
            }
        }
    }
}
