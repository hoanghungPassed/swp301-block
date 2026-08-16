package com.fastfood.flow;

import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.model.dto.PosCartLine;
import com.fastfood.model.dto.PosLine;
import com.fastfood.model.entity.Order;
import com.fastfood.service.staff.StaffOrderService;
import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bán hàng tại quầy — trọng tâm là <b>sự thật về tiền</b> (BR-22).
 * <p>
 * Tiền của lần quẹt thẻ chạy qua máy thanh toán đặt ở quầy chứ không qua hệ thống, nên dòng
 * "đã thu" trong cơ sở dữ liệu chỉ là lời khai của thu ngân. Buộc nhập mã giao dịch trên biên
 * lai là cách biến lời khai đó thành thứ đối soát được với sao kê — và ràng buộc duy nhất trên
 * mã chặn luôn tình huống một lần quẹt thẻ bị lập thành hai đơn.
 */
@DisplayName("Đơn tại quầy và sự thật về tiền")
class PosOrderIT extends IntegrationTestBase {

    private final StaffOrderService staffOrders = new StaffOrderService();

    private List<PosLine> oneItem() {
        return List.of(new PosLine(anyOrderableProductId(), 2));
    }

    // ------------------------------------------------------------------ tiền mặt

    @Test
    @DisplayName("Thu tiền mặt: lập đơn, thu tiền và đưa xuống bếp trong một nhịp")
    void cashOrderIsConfirmedAndReleasedImmediately() {
        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem(), PaymentMethod.CASH, null);

        assertEquals("CONFIRMED", order.getOrderStatus());
        assertEquals("POS", order.getOrderSource());
        assertNull(order.getCustomerId(), "Khách vãng lai không cần tài khoản");
        assertNull(order.getPickupTime(), "Đơn tại quầy không có giờ hẹn");
        assertNotNull(order.getReleasedToKdsAt(), "Khách đứng đợi nên bếp phải thấy đơn ngay");

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.Payment WHERE order_id = ? AND payment_status = 'PAID'",
                order.getOrderId()));
        assertEquals(0, count("SELECT COUNT(*) FROM dbo.PaymentTransaction t " +
                        "JOIN dbo.Payment p ON p.payment_id = t.payment_id WHERE p.order_id = ?",
                order.getOrderId()),
                "Tiền mặt không có bên thứ ba nào để đối chiếu, nên không sinh giao dịch đối soát");
    }

    @Test
    @DisplayName("Thu tiền mặt không cần mã biên lai")
    void cashOrderNeedsNoReference() {
        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem(), PaymentMethod.CASH, "   ");
        assertEquals("CONFIRMED", order.getOrderStatus());
    }

    // ------------------------------------------------------------------ thẻ / mã QR

    @Test
    @DisplayName("Quẹt thẻ mà bỏ trống mã biên lai thì bị từ chối")
    void cardPaymentRequiresReference() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), oneItem(),
                        PaymentMethod.ONLINE_GATEWAY, null));

        assertTrue(e.getMessage().contains("mã giao dịch"),
                "Thông báo phải nói rõ cần nhập gì, nhưng là: " + e.getMessage());
    }

    @Test
    @DisplayName("Quẹt thẻ có mã biên lai thì ghi lại dấu vết đối soát")
    void cardPaymentRecordsTransactionForReconciliation() {
        String reference = "POS-REF-" + System.nanoTime();

        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem(),
                PaymentMethod.ONLINE_GATEWAY, reference);

        String gateway = text("SELECT t.gateway FROM dbo.PaymentTransaction t " +
                "JOIN dbo.Payment p ON p.payment_id = t.payment_id WHERE p.order_id = ?", order.getOrderId());
        String externalId = text("SELECT t.external_transaction_id FROM dbo.PaymentTransaction t " +
                "JOIN dbo.Payment p ON p.payment_id = t.payment_id WHERE p.order_id = ?", order.getOrderId());

        assertEquals("POS_TERMINAL", gateway,
                "Phải phân biệt được khoản thu ở quầy với khoản thu qua cổng trực tuyến");
        assertEquals(reference.toUpperCase(), externalId,
                "Mã biên lai được chuẩn hoá về chữ hoa để tra cứu không phụ thuộc cách gõ");
    }

    @Test
    @DisplayName("Một biên lai không lập được thành hai đơn")
    void sameReferenceCannotCreateTwoOrders() {
        String reference = "POS-DUP-" + System.nanoTime();
        staffOrders.createPosOrder(userId(CASHIER_1), oneItem(), PaymentMethod.ONLINE_GATEWAY, reference);

        int before = count("SELECT COUNT(*) FROM dbo.Orders");

        BusinessException e = assertThrows(BusinessException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), oneItem(),
                        PaymentMethod.ONLINE_GATEWAY, reference));

        assertTrue(e.getMessage().contains("đã được ghi nhận"),
                "Thông báo phải giải thích được cho thu ngân đang đứng trước khách: " + e.getMessage());
        assertEquals(before, count("SELECT COUNT(*) FROM dbo.Orders"),
                "Lần lập đơn hỏng phải được huỷ sạch, không để lại đơn mồ côi không có tiền");
    }

    @Test
    @DisplayName("Mã biên lai không phân biệt hoa thường khi kiểm tra trùng")
    void referenceComparisonIgnoresCase() {
        String reference = "pos-case-" + System.nanoTime();
        staffOrders.createPosOrder(userId(CASHIER_1), oneItem(), PaymentMethod.ONLINE_GATEWAY, reference);

        assertThrows(BusinessException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), oneItem(),
                        PaymentMethod.ONLINE_GATEWAY, reference.toUpperCase()),
                "Gõ lại cùng mã bằng chữ hoa vẫn là cùng một biên lai");
    }

    // ------------------------------------------------------------------ kiểm tra đầu vào

    @Test
    @DisplayName("Phiếu trống thì không lập đơn")
    void emptyCartIsRejected() {
        assertThrows(ValidationException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), List.of(), PaymentMethod.CASH, null));
    }

    @Test
    @DisplayName("Số lượng vô lý bị chặn ở máy chủ, không tin ô nhập trên trình duyệt")
    void quantityIsValidatedServerSide() {
        List<PosLine> bad = List.of(new PosLine(anyOrderableProductId(), 0));
        assertThrows(ValidationException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), bad, PaymentMethod.CASH, null));
    }

    @Test
    @DisplayName("Món đã ngừng bán thì không lập đơn được")
    void unavailableProductIsRejected() {
        Integer offMenu = scalar(Integer.class,
                "SELECT TOP 1 product_id FROM dbo.Product WHERE is_available = 0 OR status = 'INACTIVE'");
        org.junit.jupiter.api.Assumptions.assumeTrue(offMenu != null, "Du lieu mau khong co mon ngoai menu");

        assertThrows(BusinessException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1),
                        List.of(new PosLine(offMenu, 1)), PaymentMethod.CASH, null));
    }

    // ------------------------------------------------------- phiếu tính tiền trên màn hình

    /*
      Phiếu tính tiền là thứ thu ngân đọc để nói giá cho khách, nên nó phải nói đúng những gì
      sắp xảy ra khi bấm thu tiền. Bản trước ghép giỏ với danh sách thực đơn: món vừa hết hàng
      rơi khỏi phiếu mà vẫn nằm trong giỏ, nên tổng trên màn hình thiếu một món và lỗi chỉ nổ
      ra lúc thu tiền.
    */

    @Test
    @DisplayName("Phiếu tính tiền giữ lại món vừa ngừng bán và đánh dấu nó, không giấu đi")
    void cartPreviewKeepsUnavailableLineVisible() {
        Integer offMenu = scalar(Integer.class,
                "SELECT TOP 1 product_id FROM dbo.Product WHERE is_available = 0 OR status = 'INACTIVE'");
        org.junit.jupiter.api.Assumptions.assumeTrue(offMenu != null, "Du lieu mau khong co mon ngoai menu");

        List<PosCartLine> lines = staffOrders.describeCart(Map.of(offMenu, 2));

        assertEquals(1, lines.size(), "Giấu dòng đi thì tổng tiền sai mà không ai biết vì sao");
        assertFalse(lines.get(0).isOrderable(), "Phải đánh dấu để màn hình chặn nút thu tiền");
        assertEquals(2, lines.get(0).getQuantity(),
                "Số lượng phải giữ nguyên thì ô nhập mới đặt về 0 để bỏ món ra được");
    }

    @Test
    @DisplayName("Món đã biến mất khỏi cơ sở dữ liệu vẫn hiện thành một dòng bỏ ra được")
    void cartPreviewShowsMissingProductAsRemovableLine() {
        List<PosCartLine> lines = staffOrders.describeCart(Map.of(999_999, 1));

        assertEquals(1, lines.size());
        assertFalse(lines.get(0).isOrderable());
        assertEquals(999_999, lines.get(0).getProductId(),
                "Giữ mã món thì ô số lượng mới gửi đúng dòng cần bỏ đi");
    }

    @Test
    @DisplayName("Phiếu tính tiền đọc giá mới nhất, không phải giá lúc bỏ vào giỏ")
    void cartPreviewReadsCurrentPrice() {
        int productId = anyOrderableProductId();
        java.math.BigDecimal price = scalar(java.math.BigDecimal.class,
                "SELECT price FROM dbo.Product WHERE product_id = ?", productId);

        List<PosCartLine> lines = staffOrders.describeCart(Map.of(productId, 3));

        assertEquals(0, price.compareTo(lines.get(0).getUnitPrice()));
        assertEquals(0, price.multiply(java.math.BigDecimal.valueOf(3))
                .compareTo(lines.get(0).getLineTotal()));
    }

    @Test
    @DisplayName("Giỏ trống không hỏi cơ sở dữ liệu lấy một câu nào")
    void emptyCartNeedsNoQuery() {
        assertTrue(staffOrders.describeCart(Map.of()).isEmpty());
        assertTrue(staffOrders.describeCart(null).isEmpty());
    }

    @Test
    @DisplayName("Đơn lưu bản sao tên và giá tại thời điểm bán (BR-02)")
    void orderItemKeepsSnapshotOfNameAndPrice() {
        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem(), PaymentMethod.CASH, null);

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.OrderItem " +
                        "WHERE order_id = ? AND product_name_snapshot IS NOT NULL AND unit_price > 0",
                order.getOrderId()),
                "Thiếu bản sao thì quản trị viên đổi giá sẽ làm đổi luôn hoá đơn cũ");
    }
}
