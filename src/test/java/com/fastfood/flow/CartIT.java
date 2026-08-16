package com.fastfood.flow;

import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.CartView;
import com.fastfood.model.entity.CartItem;
import com.fastfood.service.customer.CartService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Giỏ hàng của khách đặt trước.
 * <p>
 * Giỏ là màn hình duy nhất của khách mà mọi con số trên đó đều đi thẳng vào đơn hàng, nên ba
 * nhóm chốt chặn dưới đây đều dẫn tới tiền: <b>số lượng</b> quyết định thành tiền, <b>tình trạng
 * món</b> quyết định đơn có đặt được không, và <b>quyền sở hữu</b> quyết định ai sửa được giỏ
 * của ai. Bài test này kiểm cả ba ở tầng dịch vụ, tức là ở đúng chỗ mà một yêu cầu HTTP tự dựng
 * tay cũng phải đi qua.
 * <p>
 * Mỗi bài tự dọn giỏ trước khi chạy, vì dữ liệu mẫu cố ý để sẵn hai giỏ có hàng.
 */
@DisplayName("Giỏ hàng của khách")
class CartIT extends IntegrationTestBase {

    private final CartService cartService = new CartService();

    private int khach;

    @BeforeEach
    void donGio() {
        khach = userId(CUSTOMER_1);
        exec("DELETE FROM dbo.CartItem WHERE cart_id IN " +
             "(SELECT cart_id FROM dbo.Cart WHERE user_id = ?)", khach);
    }

    private int soLuongCua(int userId, int productId) {
        return count("SELECT ISNULL(SUM(ci.quantity), 0) FROM dbo.CartItem ci " +
                "JOIN dbo.Cart c ON c.cart_id = ci.cart_id " +
                "WHERE c.user_id = ? AND ci.product_id = ?", userId, productId);
    }

    @Nested
    @DisplayName("Thêm, sửa, bỏ món")
    class Crud {

        @Test
        @DisplayName("Thêm cùng một món hai lần thì cộng dồn, không thành hai dòng")
        void addingTwiceMergesIntoOneLine() {
            int mon = anyOrderableProductId();

            cartService.addProduct(khach, mon, 2);
            cartService.addProduct(khach, mon, 3);

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.CartItem ci " +
                    "JOIN dbo.Cart c ON c.cart_id = ci.cart_id " +
                    "WHERE c.user_id = ? AND ci.product_id = ?", khach, mon),
                    "UQ_CartItem chốt mỗi món một dòng — hai dòng cùng món thì màn hình giỏ hiện "
                            + "cùng tên món hai lần và khách tưởng mình bấm nhầm");
            assertEquals(5, soLuongCua(khach, mon));
        }

        @Test
        @DisplayName("Đặt số lượng về 0 là bỏ món khỏi giỏ")
        void settingZeroRemovesTheLine() {
            int mon = anyOrderableProductId();
            cartService.addProduct(khach, mon, 4);
            int dong = cartService.getCart(khach).getItems().get(0).getCartItemId();

            cartService.updateQuantity(khach, dong, 0);

            assertTrue(cartService.getCart(khach).isEmptyCart(),
                    "Cột quantity có ràng buộc > 0, nên số 0 phải hiểu là bỏ dòng chứ không "
                            + "phải ghi số 0 xuống rồi vấp ràng buộc");
        }

        @Test
        @DisplayName("Tổng tiền và số món đọc theo giá hiện hành của bảng món")
        void totalsFollowTheCurrentPrice() {
            int mon = anyOrderableProductId();
            BigDecimal gia = money("SELECT price FROM dbo.Product WHERE product_id = ?", mon);
            cartService.addProduct(khach, mon, 3);

            CartView gio = cartService.getCart(khach);

            assertEquals(0, gia.multiply(BigDecimal.valueOf(3)).compareTo(gio.getTotalAmount()),
                    "Giỏ chỉ là bản nháp: giá luôn đọc mới chứ không lưu lại, nên khách để giỏ "
                            + "qua đêm vẫn thấy giá hiện hành");
            assertEquals(3, gio.getTotalQuantity());
            assertEquals(3, cartService.countItems(khach));
        }

        @Test
        @DisplayName("Bỏ hết món hết hàng trong một lần, không phải xoá từng dòng")
        void removeUnavailableClearsThemAtOnce() {
            int het_hang = unavailableProductId();
            int con_ban = anyOrderableProductId();
            // Món hết hàng không thêm qua tầng dịch vụ được — đó chính là bài test bên dưới.
            // Ở đây dựng thẳng tình huống "món còn bán lúc bỏ vào giỏ, sau đó mới hết".
            int cartId = cartService.getCart(khach).getCartId();
            exec("INSERT INTO dbo.CartItem (cart_id, product_id, quantity) VALUES (?, ?, 1)",
                    cartId, het_hang);
            cartService.addProduct(khach, con_ban, 2);
            assertTrue(cartService.getCart(khach).isHasUnavailable());
            assertFalse(cartService.getCart(khach).isCheckoutable(),
                    "Còn món không phục vụ thì nút đặt hàng phải tắt");

            int bo_di = cartService.removeUnavailable(khach);

            assertEquals(1, bo_di);
            assertTrue(cartService.getCart(khach).isCheckoutable());
            assertEquals(2, soLuongCua(khach, con_ban), "Món còn bán phải ở nguyên trong giỏ");
        }
    }

    @Nested
    @DisplayName("Chặn số lượng")
    class Quantity {

        @Test
        @DisplayName("Số lượng không dương thì bị từ chối ngay")
        void nonPositiveIsRejected() {
            int mon = anyOrderableProductId();

            assertThrows(ValidationException.class, () -> cartService.addProduct(khach, mon, 0));
            assertThrows(ValidationException.class, () -> cartService.addProduct(khach, mon, -5));
            assertEquals(0, soLuongCua(khach, mon));
        }

        @Test
        @DisplayName("Vượt trần trong một lần thêm thì bị từ chối")
        void aboveCapInOneGoIsRejected() {
            int mon = anyOrderableProductId();

            BusinessException e = assertThrows(BusinessException.class,
                    () -> cartService.addProduct(khach, mon, BusinessRule.MAX_QUANTITY_PER_LINE + 1));

            assertTrue(e.getMessage().contains(String.valueOf(BusinessRule.MAX_QUANTITY_PER_LINE)),
                    "Thông báo phải nói ra con số, để khách biết còn thêm được bao nhiêu. "
                            + "Nhận được: " + e.getMessage());
            assertEquals(0, soLuongCua(khach, mon));
        }

        /**
         * Chốt chặn thật sự của cả nhóm này. {@code addItem} cộng dồn vào dòng sẵn có, nên nếu
         * chỉ kiểm con số của riêng lần bấm thì mỗi yêu cầu đều mang số 1 và đều hợp lệ, trong
         * khi dòng trong giỏ lớn lên không có trần.
         */
        @Test
        @DisplayName("Cộng dồn nhiều lần cũng không vượt được trần")
        void repeatedAddsCannotClimbOverTheCap() {
            int mon = anyOrderableProductId();
            cartService.addProduct(khach, mon, BusinessRule.MAX_QUANTITY_PER_LINE);

            assertThrows(BusinessException.class, () -> cartService.addProduct(khach, mon, 1),
                    "Bấm \"thêm vào giỏ\" thêm một lần nữa khi đã chạm trần phải bị chặn — "
                            + "nếu không thì trần chỉ chặn được người gõ thẳng số lớn");

            assertEquals(BusinessRule.MAX_QUANTITY_PER_LINE, soLuongCua(khach, mon),
                    "Lần thêm bị từ chối không được để lại dấu vết nào trong giỏ");
        }

        @Test
        @DisplayName("Sửa số lượng vượt trần cũng bị từ chối")
        void updateAboveCapIsRejected() {
            int mon = anyOrderableProductId();
            cartService.addProduct(khach, mon, 1);
            int dong = cartService.getCart(khach).getItems().get(0).getCartItemId();

            assertThrows(BusinessException.class, () -> cartService.updateQuantity(khach, dong,
                    BusinessRule.MAX_QUANTITY_PER_LINE + 1));

            assertEquals(1, soLuongCua(khach, mon));
        }

        @Test
        @DisplayName("Đúng trần thì vẫn đặt được — chặn ở trên ngưỡng, không phải tại ngưỡng")
        void exactlyAtTheCapIsAllowed() {
            int mon = anyOrderableProductId();

            cartService.addProduct(khach, mon, BusinessRule.MAX_QUANTITY_PER_LINE);

            assertEquals(BusinessRule.MAX_QUANTITY_PER_LINE, soLuongCua(khach, mon));
        }
    }

    @Nested
    @DisplayName("Tình trạng món")
    class Availability {

        @Test
        @DisplayName("Món đã ngừng bán thì không bỏ vào giỏ được, và được gọi tên")
        void unavailableProductCannotEnterTheCart() {
            int het_hang = unavailableProductId();
            String ten = text("SELECT name FROM dbo.Product WHERE product_id = ?", het_hang);

            BusinessException e = assertThrows(BusinessException.class,
                    () -> cartService.addProduct(khach, het_hang, 1));

            assertTrue(e.getMessage().contains(ten),
                    "Thông báo phải nêu tên món. Khách thêm liền mấy món thì \"món này không còn "
                            + "phục vụ\" không nói được là món nào. Nhận được: " + e.getMessage());
            assertTrue(cartService.getCart(khach).isEmptyCart());
        }

        @Test
        @DisplayName("Món không tồn tại thì báo không tìm thấy, không nổ ở tầng dữ liệu")
        void unknownProductIsRejectedCleanly() {
            assertThrows(NotFoundException.class, () -> cartService.addProduct(khach, 999_999, 1),
                    "Mã món tới từ biểu mẫu nên không tin được; thiếu bước kiểm thì lỗi khoá "
                            + "ngoại nổ ra dưới tầng dữ liệu và khách nhận \"có lỗi xảy ra\"");
        }
    }

    @Nested
    @DisplayName("Quyền sở hữu")
    class Guards {

        @Test
        @DisplayName("Không sửa được dòng trong giỏ người khác, dù cầm đúng mã dòng")
        void cannotUpdateAnotherCustomerLine() {
            int nguoi_khac = userId(CUSTOMER_2);
            int mon = anyOrderableProductId();
            exec("DELETE FROM dbo.CartItem WHERE cart_id IN " +
                 "(SELECT cart_id FROM dbo.Cart WHERE user_id = ?)", nguoi_khac);
            cartService.addProduct(nguoi_khac, mon, 4);
            CartItem cua_ho = cartService.getCart(nguoi_khac).getItems().get(0);

            // Không ném lỗi: điều kiện cart_id nằm ngay trong câu lệnh nên câu UPDATE khớp 0 dòng.
            cartService.updateQuantity(khach, cua_ho.getCartItemId(), 9);

            assertEquals(4, soLuongCua(nguoi_khac, mon),
                    "Điều kiện cart_id trong câu UPDATE là thứ giữ cho một khách gửi lên mã dòng "
                            + "của người khác cũng không đổi được gì");
        }

        @Test
        @DisplayName("Không xoá được dòng trong giỏ người khác")
        void cannotRemoveAnotherCustomerLine() {
            int nguoi_khac = userId(CUSTOMER_2);
            int mon = anyOrderableProductId();
            exec("DELETE FROM dbo.CartItem WHERE cart_id IN " +
                 "(SELECT cart_id FROM dbo.Cart WHERE user_id = ?)", nguoi_khac);
            cartService.addProduct(nguoi_khac, mon, 2);
            CartItem cua_ho = cartService.getCart(nguoi_khac).getItems().get(0);

            cartService.removeItem(khach, cua_ho.getCartItemId());

            assertEquals(2, soLuongCua(nguoi_khac, mon));
        }

        @Test
        @DisplayName("Mỗi khách đúng một giỏ, mở lại nhiều lần không sinh thêm giỏ")
        void oneCartPerCustomer() {
            cartService.getCart(khach);
            cartService.getCart(khach);
            cartService.countItems(khach);

            assertEquals(1, count("SELECT COUNT(*) FROM dbo.Cart WHERE user_id = ?", khach),
                    "Ràng buộc duy nhất trên user_id là thứ chặn việc mỗi lượt mở trang lại sinh "
                            + "một giỏ mới, và khách thấy giỏ trống dù vừa thêm món");
        }
    }
}
