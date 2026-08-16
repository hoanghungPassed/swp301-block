package com.fastfood.schema;

import com.fastfood.testsupport.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cơ sở dữ liệu tự bảo vệ được tới đâu.
 * <p>
 * Mọi bài ở đây đều ghi thẳng bằng SQL, <b>cố tình bỏ qua</b> tầng Service. Đó chính là điều
 * cần chứng minh: nếu chỉ tầng ứng dụng chặn thì một câu lệnh chạy tay, một lần sửa dữ liệu
 * lúc gấp, hay một đoạn mã mới quên kiểm tra là dữ liệu hỏng ngay. Các quy tắc liên quan tới
 * tiền và tới toàn vẹn lịch sử phải có lớp chặn nằm trong chính cơ sở dữ liệu.
 */
@DisplayName("Ràng buộc và trigger trong cơ sở dữ liệu")
class SchemaConstraintIT extends IntegrationTestBase {

    @Nested
    @DisplayName("Ràng buộc trên bảng Orders")
    class OrderConstraints {

        @Test
        @DisplayName("Đơn đặt trước không có chủ thì bị chặn (BR-21)")
        void onlineOrderMustHaveCustomer() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "pickup_time, created_at) VALUES (NULL, 'ONLINE_PREORDER', 50000, 'PENDING_PAYMENT', ?, ?)",
                    LocalDateTime.now().plusHours(2), LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_onlineCustomer"),
                    "Phải bị chặn bởi CK_Orders_onlineCustomer, nhưng lỗi là: " + error);
        }

        @Test
        @DisplayName("Kênh bán ngoài hai kênh đã khoá thì bị chặn (BR-03)")
        void deliveryChannelIsRejected() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, created_at) " +
                    "VALUES (?, 'DELIVERY', 50000, 'CONFIRMED', ?)",
                    userId(CUSTOMER_1), LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_source"),
                    "Giao hàng tận nơi nằm ngoài phạm vi, phải bị CK_Orders_source chặn. Lỗi: " + error);
        }

        @Test
        @DisplayName("Đơn tại quầy bị gán giờ hẹn thì bị chặn")
        void posOrderCannotHavePickupTime() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "pickup_time, created_at) VALUES (NULL, 'POS', 50000, 'CONFIRMED', ?, ?)",
                    LocalDateTime.now().plusHours(2), LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_pickupTime"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Đơn đặt trước thiếu giờ hẹn thì bị chặn (BR-05)")
        void onlineOrderMustHavePickupTime() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, created_at) " +
                    "VALUES (?, 'ONLINE_PREORDER', 50000, 'PENDING_PAYMENT', ?)",
                    userId(CUSTOMER_1), LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_pickupTime"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Đơn tại quầy không được ở trạng thái chờ thanh toán")
        void posOrderCannotBePendingPayment() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, created_at) " +
                    "VALUES (NULL, 'POS', 50000, 'PENDING_PAYMENT', ?)", LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_pendingOnlineOnly"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Kế hoạch vào bếp muộn hơn giờ hẹn thì bị chặn (BR-08)")
        void kitchenReleaseMustPrecedePickup() {
            LocalDateTime pickup = LocalDateTime.now().plusHours(2);
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "pickup_time, kitchen_release_at, created_at) " +
                    "VALUES (?, 'ONLINE_PREORDER', 50000, 'CONFIRMED', ?, ?, ?)",
                    userId(CUSTOMER_1), pickup, pickup.plusMinutes(10), LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_releaseBeforePickup"),
                    "Tính sai lead time phải lộ ra ngay tại cơ sở dữ liệu. Lỗi: " + error);
        }

        @Test
        @DisplayName("Đơn tại quầy không được có mã nhận hàng")
        void posOrderCannotHavePickupCode() {
            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "pickup_code, created_at) VALUES (NULL, 'POS', 50000, 'CONFIRMED', 'ABC123', ?)",
                    LocalDateTime.now());

            assertTrue(error.contains("CK_Orders_pickupCodeOnline"), "Lỗi: " + error);
        }
    }

    @Nested
    @DisplayName("Ràng buộc về tiền")
    class MoneyConstraints {

        @Test
        @DisplayName("Đơn đặt trước không được thu tiền mặt (BR-04)")
        void onlineOrderCannotBePaidByCash() {
            int orderId = firstOnlineOrderId();

            String error = expectSqlFailure(
                    "INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, created_at) " +
                    "VALUES (?, 'CASH', 50000, 'PAID', 99, ?)", orderId, LocalDateTime.now());

            assertTrue(error.contains("khong thu tien mat") || error.contains("50004"),
                    "Trigger TR_Payment_OnlineGatewayOnly phải chặn. Lỗi: " + error);
        }

        @Test
        @DisplayName("Hai giao dịch cùng mã bên ngoài thì lần hai bị chặn (BR-14, BR-22)")
        void externalTransactionIdIsUnique() {
            int paymentId = firstPaymentId();
            String externalId = "TEST-DUP-" + paymentId;

            exec("INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, " +
                 "status, created_at) VALUES (?, 'TEST', ?, 'SUCCESS', ?)",
                 paymentId, externalId, LocalDateTime.now());

            String error = expectSqlFailure(
                    "INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, " +
                    "status, created_at) VALUES (?, 'TEST', ?, 'SUCCESS', ?)",
                    paymentId, externalId, LocalDateTime.now());

            assertTrue(error.contains("UQ_Transaction_extId") || error.contains("duplicate"),
                    "Đây là chốt chặn quan trọng nhất về tiền: cùng một mã giao dịch không được "
                    + "ghi nhận hai lần. Lỗi: " + error);
        }

        @Test
        @DisplayName("Hai lần thanh toán cùng số thứ tự trong một đơn thì bị chặn")
        void paymentAttemptNumberIsUniquePerOrder() {
            int orderId = firstPosOrderId();

            exec("INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, created_at) " +
                 "VALUES (?, 'CASH', 1000, 'PAID', 77, ?)", orderId, LocalDateTime.now());

            String error = expectSqlFailure(
                    "INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, created_at) " +
                    "VALUES (?, 'CASH', 1000, 'PAID', 77, ?)", orderId, LocalDateTime.now());

            assertTrue(error.contains("UQ_Payment_attempt") || error.contains("duplicate"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Hai đơn cùng khoá chống trùng thì lần hai bị chặn (NFR-07)")
        void idempotencyKeyIsUnique() {
            String key = "test-idem-" + System.nanoTime();
            LocalDateTime pickup = LocalDateTime.now().plusHours(3);

            exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                 "idempotency_key, pickup_time, created_at) " +
                 "VALUES (?, 'ONLINE_PREORDER', 1000, 'PENDING_PAYMENT', ?, ?, ?)",
                 userId(CUSTOMER_1), key, pickup, LocalDateTime.now());

            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "idempotency_key, pickup_time, created_at) " +
                    "VALUES (?, 'ONLINE_PREORDER', 1000, 'PENDING_PAYMENT', ?, ?, ?)",
                    userId(CUSTOMER_1), key, pickup, LocalDateTime.now());

            assertTrue(error.contains("UX_Orders_idempotency") || error.contains("duplicate"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Hai đơn cùng mã nhận hàng thì bị chặn")
        void pickupCodeIsUnique() {
            String code = "TSTC" + (System.nanoTime() % 100000);
            LocalDateTime pickup = LocalDateTime.now().plusHours(3);

            exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                 "pickup_code, pickup_time, created_at) " +
                 "VALUES (?, 'ONLINE_PREORDER', 1000, 'CONFIRMED', ?, ?, ?)",
                 userId(CUSTOMER_1), code, pickup, LocalDateTime.now());

            String error = expectSqlFailure(
                    "INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
                    "pickup_code, pickup_time, created_at) " +
                    "VALUES (?, 'ONLINE_PREORDER', 1000, 'CONFIRMED', ?, ?, ?)",
                    userId(CUSTOMER_2), code, pickup, LocalDateTime.now());

            assertTrue(error.contains("UX_Orders_pickupCode") || error.contains("duplicate"), "Lỗi: " + error);
        }
    }

    @Nested
    @DisplayName("Thứ tự bàn giao món từ bếp ra quầy (BR-25)")
    class HandoverOrder {

        @Test
        @DisplayName("Nhận món chưa được bàn giao thì bị chặn ngay tại cơ sở dữ liệu")
        void cannotReceiveAnItemThatWasNeverHandedOver() {
            int itemId = anyItemStillInKitchen();

            String error = expectSqlFailure(
                    "UPDATE dbo.OrderItem SET received_at = ?, received_by = ? WHERE order_item_id = ?",
                    LocalDateTime.now(), userId(CASHIER_1), itemId);

            assertTrue(error.contains("CK_OrderItem_handover"),
                    "Món nhảy thẳng sang \"quầy đã nhận\" mà không ai đưa ra là chuyện vô nghĩa, "
                    + "và nó khiến điều kiện chặn giao thiếu món cho khách mất tác dụng. Lỗi: " + error);
        }

        @Test
        @DisplayName("Bỏ mốc bàn giao trong khi món đã được nhận thì bị chặn")
        void cannotEraseHandoverAfterReceiving() {
            int itemId = anyItemStillInKitchen();
            exec("UPDATE dbo.OrderItem SET handed_over_at = ?, handed_over_by = ?, " +
                 "received_at = ?, received_by = ? WHERE order_item_id = ?",
                 LocalDateTime.now(), userId(KITCHEN_1), LocalDateTime.now(), userId(CASHIER_1), itemId);

            String error = expectSqlFailure(
                    "UPDATE dbo.OrderItem SET handed_over_at = NULL WHERE order_item_id = ?", itemId);

            assertTrue(error.contains("CK_OrderItem_handover"),
                    "Ràng buộc phải giữ cả khi sửa ngược, không chỉ lúc ghi mới. Lỗi: " + error);
        }

        @Test
        @DisplayName("Bàn giao mà quầy chưa nhận là hợp lệ — đó chính là hàng chờ trên quầy")
        void handedOverWithoutReceiptIsAllowed() {
            int itemId = anyItemStillInKitchen();

            int changed = exec("UPDATE dbo.OrderItem SET handed_over_at = ?, handed_over_by = ? " +
                               "WHERE order_item_id = ?", LocalDateTime.now(), userId(KITCHEN_1), itemId);

            assertTrue(changed == 1,
                    "Chặn chiều này là chặn nhầm: khoảng giữa hai mốc chính là lúc món nằm chờ "
                    + "trên quầy, và đó là toàn bộ nội dung màn hình quầy giao nhận");
        }

        /** Một món mới, chưa qua bàn giao — tạo riêng để không đụng dữ liệu mẫu. */
        private int anyItemStillInKitchen() {
            exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, created_at) " +
                 "VALUES (NULL, 'POS', 25000, 'READY', ?)", LocalDateTime.now());
            int orderId = scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");

            exec("INSERT INTO dbo.OrderItem (order_id, product_id, product_name_snapshot, unit_price, " +
                 "quantity, item_status, assigned_to_user_id, ready_at) " +
                 "VALUES (?, ?, N'Mon test ban giao', 25000, 1, 'READY', ?, ?)",
                 orderId, anyOrderableProductId(), userId(KITCHEN_1), LocalDateTime.now());
            return scalar(Integer.class, "SELECT MAX(order_item_id) FROM dbo.OrderItem");
        }
    }

    @Nested
    @DisplayName("Không xoá vĩnh viễn dữ liệu giao dịch (BR-20)")
    class NoHardDelete {

        @Test
        @DisplayName("Xoá đơn hàng bị chặn")
        void ordersCannotBeDeleted() {
            String error = expectSqlFailure("DELETE FROM dbo.Orders WHERE order_id = ?", firstPosOrderId());
            assertTrue(error.contains("Khong duoc xoa Orders"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Xoá món trong đơn bị chặn — vì làm sai tổng tiền mà không ai biết")
        void orderItemsCannotBeDeleted() {
            Integer itemId = scalar(Integer.class, "SELECT MIN(order_item_id) FROM dbo.OrderItem");
            String error = expectSqlFailure("DELETE FROM dbo.OrderItem WHERE order_item_id = ?", itemId);
            assertTrue(error.contains("Khong duoc xoa OrderItem"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Xoá bản ghi thanh toán bị chặn")
        void paymentsCannotBeDeleted() {
            String error = expectSqlFailure("DELETE FROM dbo.Payment WHERE payment_id = ?", firstPaymentId());
            assertTrue(error.contains("Khong duoc xoa Payment"), "Lỗi: " + error);
        }

        @Test
        @DisplayName("Xoá nhật ký thao tác bị chặn — nhật ký phải không sửa được")
        void auditLogCannotBeDeleted() {
            Long auditId = scalar(Long.class, "SELECT MIN(audit_id) FROM dbo.AuditLog");
            String error = expectSqlFailure("DELETE FROM dbo.AuditLog WHERE audit_id = ?", auditId);
            assertTrue(error.contains("Khong duoc xoa AuditLog"), "Lỗi: " + error);
        }
    }

    @Nested
    @DisplayName("Chống trùng bằng câu lệnh có điều kiện")
    class ConditionalUpdates {

        @Test
        @DisplayName("Đưa đơn xuống bếp lần hai không ăn thua (BR-09, NFR-05)")
        void releaseToKitchenHappensOnce() {
            int orderId = scheduledOrderId();

            int first = exec("UPDATE dbo.Orders SET released_to_kds_at = ? " +
                             "WHERE order_id = ? AND released_to_kds_at IS NULL AND order_status = 'CONFIRMED'",
                             LocalDateTime.now(), orderId);
            int second = exec("UPDATE dbo.Orders SET released_to_kds_at = ? " +
                              "WHERE order_id = ? AND released_to_kds_at IS NULL AND order_status = 'CONFIRMED'",
                              LocalDateTime.now(), orderId);

            assertTrue(first == 1, "Lần đầu phải đưa xuống bếp được, nhưng đổi " + first + " dòng");
            assertTrue(second == 0,
                    "Bộ hẹn giờ chạy lại không được sinh thêm việc cho bếp, nhưng đổi " + second + " dòng");
        }

        @Test
        @DisplayName("Hai đầu bếp nhận cùng một món thì chỉ một người được (UC-11)")
        void onlyOneCookCanClaimAnItem() {
            Integer itemId = scalar(Integer.class,
                    "SELECT MIN(order_item_id) FROM dbo.OrderItem WHERE item_status = 'WAITING'");
            org.junit.jupiter.api.Assumptions.assumeTrue(itemId != null, "Du lieu mau khong con mon nao cho");

            int first = exec("UPDATE dbo.OrderItem SET assigned_to_user_id = ?, item_status = 'PREPARING', " +
                             "started_at = ? WHERE order_item_id = ? AND item_status = 'WAITING' " +
                             "AND assigned_to_user_id IS NULL",
                             userId(KITCHEN_1), LocalDateTime.now(), itemId);
            int second = exec("UPDATE dbo.OrderItem SET assigned_to_user_id = ?, item_status = 'PREPARING', " +
                              "started_at = ? WHERE order_item_id = ? AND item_status = 'WAITING' " +
                              "AND assigned_to_user_id IS NULL",
                              userId(KITCHEN_2), LocalDateTime.now(), itemId);

            assertTrue(first == 1, "Người nhận trước phải nhận được");
            assertTrue(second == 0, "Người thứ hai không được ghi đè phân công của người thứ nhất");

            String assignee = text("SELECT u.email FROM dbo.OrderItem oi JOIN dbo.Users u " +
                                   "ON u.user_id = oi.assigned_to_user_id WHERE oi.order_item_id = ?", itemId);
            assertTrue(KITCHEN_1.equals(assignee),
                    "Món phải thuộc về người nhận trước, nhưng đang là " + assignee);
        }
    }

    // ------------------------------------------------------------------ dữ liệu dùng chung

    private static int firstOnlineOrderId() {
        return scalar(Integer.class,
                "SELECT MIN(order_id) FROM dbo.Orders WHERE order_source = 'ONLINE_PREORDER'");
    }

    private static int firstPosOrderId() {
        return scalar(Integer.class, "SELECT MIN(order_id) FROM dbo.Orders WHERE order_source = 'POS'");
    }

    private static int firstPaymentId() {
        return scalar(Integer.class, "SELECT MIN(payment_id) FROM dbo.Payment");
    }

    /** Một đơn đặt trước đã xác nhận nhưng bếp chưa nhận — tạo riêng để không đụng dữ liệu mẫu. */
    private static int scheduledOrderId() {
        LocalDateTime pickup = LocalDateTime.now().plusHours(4);
        exec("INSERT INTO dbo.Orders (customer_id, order_source, total_amount, order_status, " +
             "pickup_time, kitchen_release_at, created_at) " +
             "VALUES (?, 'ONLINE_PREORDER', 50000, 'CONFIRMED', ?, ?, ?)",
             userId(CUSTOMER_1), pickup, pickup.minusMinutes(20), LocalDateTime.now());
        return scalar(Integer.class, "SELECT MAX(order_id) FROM dbo.Orders");
    }
}
