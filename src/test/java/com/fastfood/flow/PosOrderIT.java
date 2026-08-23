package com.fastfood.flow;

import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.model.dto.Dtos.PosCartLine;
import com.fastfood.model.dto.Dtos.PosLine;
import com.fastfood.model.entity.OrderEntities.Order;
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

@DisplayName("Đơn tại quầy và sự thật về tiền")
class PosOrderIT extends IntegrationTestBase {

    private final StaffOrderService staffOrders = new StaffOrderService();

    private List<PosLine> oneItem() {
        return List.of(new PosLine(anyOrderableProductId(), 2));
    }

    @Test
    @DisplayName("Thu tiền mặt: lập đơn, thu tiền và đưa xuống bếp trong một nhịp")
    void cashOrderIsConfirmedAndReleasedImmediately() {
        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem());

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
    @DisplayName("Phiếu trống thì không lập đơn")
    void emptyCartIsRejected() {
        assertThrows(ValidationException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), List.of()));
    }

    @Test
    @DisplayName("Số lượng vô lý bị chặn ở máy chủ, không tin ô nhập trên trình duyệt")
    void quantityIsValidatedServerSide() {
        List<PosLine> bad = List.of(new PosLine(anyOrderableProductId(), 0));
        assertThrows(ValidationException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1), bad));
    }

    @Test
    @DisplayName("Món đã ngừng bán thì không lập đơn được")
    void unavailableProductIsRejected() {
        Integer offMenu = scalar(Integer.class,
                "SELECT TOP 1 product_id FROM dbo.Product WHERE is_available = 0 OR status = 'INACTIVE'");
        org.junit.jupiter.api.Assumptions.assumeTrue(offMenu != null, "Du lieu mau khong co mon ngoai menu");

        assertThrows(BusinessException.class,
                () -> staffOrders.createPosOrder(userId(CASHIER_1),
                        List.of(new PosLine(offMenu, 1))));
    }

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
        Order order = staffOrders.createPosOrder(userId(CASHIER_1), oneItem());

        assertEquals(1, count("SELECT COUNT(*) FROM dbo.OrderItem " +
                        "WHERE order_id = ? AND product_name_snapshot IS NOT NULL AND unit_price > 0",
                order.getOrderId()),
                "Thiếu bản sao thì quản trị viên đổi giá sẽ làm đổi luôn hoá đơn cũ");
    }
}
