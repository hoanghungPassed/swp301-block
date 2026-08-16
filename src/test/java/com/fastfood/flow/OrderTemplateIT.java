package com.fastfood.flow;

import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.TemplateApplyResult;
import com.fastfood.model.entity.OrderTemplate;
import com.fastfood.service.customer.OrderTemplateService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mẫu đặt nhanh của khách, trên trang lịch sử đơn.
 * <p>
 * Dùng {@code customer2} cho phần lớn bài test vì dữ liệu mẫu đã lưu sẵn hai mẫu cho
 * {@code customer1}, và một trong hai cố ý chứa món đã ngừng bán.
 */
@DisplayName("Mẫu đặt nhanh của khách")
class OrderTemplateIT extends IntegrationTestBase {

    private final OrderTemplateService templateService = new OrderTemplateService();

    /** Một đơn đã đặt của chính khách này, lấy từ dữ liệu mẫu. */
    private int donCuaKhach(int customerId) {
        Integer id = scalar(Integer.class,
                "SELECT TOP 1 o.order_id FROM dbo.Orders o " +
                "WHERE o.customer_id = ? AND EXISTS " +
                "  (SELECT 1 FROM dbo.OrderItem oi WHERE oi.order_id = o.order_id) " +
                "ORDER BY o.order_id", customerId);
        if (id == null) {
            throw new IllegalStateException("Khach nay khong co don nao trong du lieu mau");
        }
        return id;
    }

    @Nested
    @DisplayName("Vòng đời một mẫu")
    class Crud {

        @Test
        @DisplayName("Lưu từ đơn cũ, đổi tên, sửa số lượng, rồi xoá")
        void fullCycle() {
            int khach = userId(CUSTOMER_2);
            int don = donCuaKhach(khach);

            OrderTemplate mau = templateService.saveFromOrder(khach, don, "  Mẫu vòng đời  ");
            int id = mau.getTemplateId();
            assertEquals("Mẫu vòng đời", mau.getName(), "Phải cắt khoảng trắng thừa");
            assertTrue(mau.getLineCount() > 0, "Mẫu lưu từ đơn phải mang theo món của đơn đó");

            templateService.rename(id, khach, "Mẫu đã đổi tên");
            OrderTemplate sau = templateService.findOwn(id, khach);
            assertEquals("Mẫu đã đổi tên", sau.getName());
            assertTrue(sau.isEdited());

            int mon = sau.getItems().get(0).getProductId();
            templateService.setQuantity(id, khach, mon, 7);
            assertEquals(7, templateService.findOwn(id, khach).getItems().stream()
                    .filter(i -> i.getProductId() == mon).findFirst().orElseThrow().getQuantity());

            templateService.delete(id, khach);
            assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderTemplate WHERE template_id = ?", id));
        }

        @Test
        @DisplayName("Xoá mẫu thì dòng món đi theo, không để lại dòng mồ côi")
        void deleteCascadesToItems() {
            int khach = userId(CUSTOMER_2);
            int id = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu cascade")
                    .getTemplateId();
            assertTrue(count("SELECT COUNT(*) FROM dbo.OrderTemplateItem WHERE template_id = ?", id) > 0);

            templateService.delete(id, khach);

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderTemplateItem WHERE template_id = ?", id));
        }

        @Test
        @DisplayName("Bỏ nốt món cuối cùng thì mẫu tự biến mất")
        void emptyingRemovesTemplate() {
            int khach = userId(CUSTOMER_2);
            OrderTemplate mau = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu dọn sạch");
            int id = mau.getTemplateId();

            for (var item : mau.getItems()) {
                templateService.setQuantity(id, khach, item.getProductId(), 0);
            }

            assertEquals(0, count("SELECT COUNT(*) FROM dbo.OrderTemplate WHERE template_id = ?", id),
                    "Mẫu không còn món nào chỉ là một cái tên chiếm chỗ");
        }

        @Test
        @DisplayName("Mẫu không lưu giá — đổi giá món thì tạm tính đổi theo")
        void priceIsReadLive() {
            int khach = userId(CUSTOMER_2);
            OrderTemplate mau = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu đổi giá");
            int id = mau.getTemplateId();
            int mon = mau.getItems().get(0).getProductId();
            java.math.BigDecimal gia_cu = money("SELECT price FROM dbo.Product WHERE product_id = ?", mon);
            java.math.BigDecimal truoc = templateService.findOwn(id, khach).getEstimatedTotal();

            try {
                exec("UPDATE dbo.Product SET price = ? WHERE product_id = ?",
                        gia_cu.add(java.math.BigDecimal.valueOf(1000)), mon);

                assertTrue(templateService.findOwn(id, khach).getEstimatedTotal().compareTo(truoc) > 0,
                        "Bảng OrderTemplateItem cố ý không có cột giá — giá đọc mới từ bảng món");
            } finally {
                exec("UPDATE dbo.Product SET price = ? WHERE product_id = ?", gia_cu, mon);
                templateService.delete(id, khach);
            }
        }
    }

    @Nested
    @DisplayName("Nạp mẫu vào giỏ")
    class Apply {

        @Test
        @DisplayName("Nạp xong mẫu vẫn còn — mẫu là thứ dùng đi dùng lại")
        void applyKeepsTemplate() {
            int khach = userId(CUSTOMER_2);
            int id = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu nạp lại")
                    .getTemplateId();

            TemplateApplyResult result = templateService.applyToCart(id, khach);

            assertTrue(result.isAnythingAdded());
            assertNotNull(templateService.findOwn(id, khach),
                    "Khác phiếu treo ở quầy: xoá sau lần nạp đầu thì đây chỉ là nút đặt lại rườm rà");
            templateService.delete(id, khach);
        }

        @Test
        @DisplayName("Món đã ngừng bán bị bỏ qua và được gọi tên, phần còn lại vẫn nạp")
        void skippedItemsAreNamed() {
            int khach = userId(CUSTOMER_2);
            int id = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu có món hết")
                    .getTemplateId();
            int het_hang = unavailableProductId();
            String ten_mon_het = text("SELECT name FROM dbo.Product WHERE product_id = ?", het_hang);
            templateService.addItem(id, khach, het_hang, 2);

            TemplateApplyResult result = templateService.applyToCart(id, khach);

            assertTrue(result.isAnythingAdded(), "Món còn bán vẫn phải vào giỏ");
            assertTrue(result.isAnythingSkipped());
            assertTrue(result.getSkippedText().contains(ten_mon_het),
                    "Phải gọi tên món bị bỏ. Nhận được: " + result.getSkippedText());
            templateService.delete(id, khach);
        }

        @Test
        @DisplayName("Mẫu không còn món nào bán được thì báo lỗi thay vì nạp rỗng trong im lặng")
        void allUnavailableIsAnError() {
            int khach = userId(CUSTOMER_2);
            int don = donCuaKhach(khach);
            OrderTemplate mau = templateService.saveFromOrder(khach, don, "Mẫu toàn món hết");
            int id = mau.getTemplateId();
            // Bỏ hết món còn bán, chỉ chừa lại một món đã ngừng phục vụ.
            templateService.addItem(id, khach, unavailableProductId(), 1);
            for (var item : mau.getItems()) {
                templateService.setQuantity(id, khach, item.getProductId(), 0);
            }

            BusinessException e = assertThrows(BusinessException.class,
                    () -> templateService.applyToCart(id, khach));

            assertTrue(e.getMessage().contains("không còn món nào"), "Nhận được: " + e.getMessage());
            templateService.delete(id, khach);
        }

        @Test
        @DisplayName("Nạp hai lần thì cộng dồn số lượng trong giỏ, như bấm thêm món hai lần")
        void applyingTwiceAccumulates() {
            int khach = userId(CUSTOMER_2);
            int id = templateService.saveFromOrder(khach, donCuaKhach(khach), "Mẫu cộng dồn")
                    .getTemplateId();
            int mon = templateService.findOwn(id, khach).getItems().get(0).getProductId();
            templateService.applyToCart(id, khach);
            int sau_lan_1 = count("SELECT ISNULL(SUM(ci.quantity),0) FROM dbo.CartItem ci " +
                    "JOIN dbo.Cart c ON c.cart_id = ci.cart_id " +
                    "WHERE c.user_id = ? AND ci.product_id = ?", khach, mon);
            templateService.applyToCart(id, khach);
            int sau_lan_2 = count("SELECT ISNULL(SUM(ci.quantity),0) FROM dbo.CartItem ci " +
                    "JOIN dbo.Cart c ON c.cart_id = ci.cart_id " +
                    "WHERE c.user_id = ? AND ci.product_id = ?", khach, mon);

            assertTrue(sau_lan_2 > sau_lan_1, "Nhận được " + sau_lan_1 + " rồi " + sau_lan_2);
            templateService.delete(id, khach);
        }
    }

    @Nested
    @DisplayName("Chốt chặn")
    class Guards {

        @Test
        @DisplayName("Không lưu được đơn của người khác thành mẫu")
        void cannotSaveAnotherCustomerOrder() {
            int khach = userId(CUSTOMER_2);
            int don_nguoi_khac = donCuaKhach(userId(CUSTOMER_1));

            assertThrows(NotFoundException.class,
                    () -> templateService.saveFromOrder(khach, don_nguoi_khac, "Mẫu ăn trộm"));
        }

        @Test
        @DisplayName("Không đụng được vào mẫu của tài khoản khác")
        void cannotTouchAnotherCustomerTemplate() {
            int chu = userId(CUSTOMER_2);
            int nguoi_la = userId(CUSTOMER_1);
            int id = templateService.saveFromOrder(chu, donCuaKhach(chu), "Mẫu của tôi").getTemplateId();

            assertThrows(BusinessException.class, () -> templateService.findOwn(id, nguoi_la));
            assertThrows(BusinessException.class, () -> templateService.delete(id, nguoi_la));
            assertThrows(BusinessException.class, () -> templateService.rename(id, nguoi_la, "cướp"));
            assertThrows(BusinessException.class, () -> templateService.applyToCart(id, nguoi_la));

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.OrderTemplate WHERE template_id = ?", id));
            templateService.delete(id, chu);
        }

        @Test
        @DisplayName("Trùng tên trong cùng một khách bị chặn, khác khách thì không")
        void duplicateNameIsPerCustomer() {
            int khach = userId(CUSTOMER_2);
            int khach_khac = userId(CUSTOMER_1);
            int id = templateService.saveFromOrder(khach, donCuaKhach(khach), "Tên dùng chung")
                    .getTemplateId();

            BusinessException e = assertThrows(BusinessException.class,
                    () -> templateService.saveFromOrder(khach, donCuaKhach(khach), "Tên dùng chung"));
            assertTrue(e.getMessage().contains("đặt tên khác"), "Nhận được: " + e.getMessage());

            int id_khac = templateService.saveFromOrder(khach_khac, donCuaKhach(khach_khac),
                    "Tên dùng chung").getTemplateId();
            assertNotNull(templateService.findOwn(id_khac, khach_khac));

            templateService.delete(id, khach);
            templateService.delete(id_khac, khach_khac);
        }

        @Test
        @DisplayName("Tên trống bị từ chối, và mẫu không được tạo dở dang")
        void blankNameIsRejected() {
            int khach = userId(CUSTOMER_2);
            int truoc = templateService.listOf(khach).size();

            assertThrows(ValidationException.class,
                    () -> templateService.saveFromOrder(khach, donCuaKhach(khach), "   "));

            assertEquals(truoc, templateService.listOf(khach).size());
        }
    }

    @Nested
    @DisplayName("Dữ liệu mẫu")
    class Seed {

        @Test
        @DisplayName("Khách mẫu có sẵn mẫu đặt nhanh, và một mẫu cố ý chứa món đã ngừng bán")
        void seedHasTemplates() {
            List<OrderTemplate> cua_khach_1 = templateService.listOf(userId(CUSTOMER_1));

            assertEquals(2, cua_khach_1.size());
            assertTrue(cua_khach_1.stream().anyMatch(OrderTemplate::isAnyUnavailable),
                    "Toàn món còn bán thì nhánh cảnh báo khi nạp mẫu không có gì để xem, "
                            + "và lỗi ở đó chỉ lộ ra khi có khách thật gặp phải");
            assertFalse(cua_khach_1.stream().anyMatch(t -> t.getItems().isEmpty()),
                    "Mẫu rỗng nghĩa là phép ghép theo tên món đã lặng lẽ bỏ dòng");
        }
    }
}
