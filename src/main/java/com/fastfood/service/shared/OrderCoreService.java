package com.fastfood.service.shared;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.constant.PaymentStatus;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.PickupCodeGenerator;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.Payment;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Phần nghiệp vụ đơn hàng mà <b>nhiều vai trò cùng dùng</b> — không thuộc riêng khách hàng
 * hay thu ngân, nên không nằm trong {@code service.customer} hay {@code service.staff}.
 * <p>
 * Ba nhóm việc ở đây:
 * <ul>
 *   <li><b>Nạp đơn</b> — {@link #findById} và {@link #loadFull}, mọi màn hình đều cần.</li>
 *   <li><b>Xác nhận và hoàn tiền</b> — {@link #confirmOnlineAfterPaid} và {@link #refundIfPaid}
 *       chạy <i>bên trong</i> giao dịch của lớp gọi nên nhận sẵn {@code Connection}.</li>
 *   <li><b>Suy ra trạng thái đơn</b> — {@link #lockOrder} và {@link #recalculateStatus}, do bếp
 *       kích hoạt mỗi khi một món đổi trạng thái.</li>
 * </ul>
 * Ai gọi lớp này: {@code CustomerOrderService}, {@code StaffOrderService},
 * {@link PaymentService}, {@code KitchenService}, {@link ScheduleService}.
 * <p>
 * Trạng thái "đang chế biến" và "sẵn sàng" của đơn không do ai bấm trực tiếp mà được suy ra
 * từ trạng thái các món — xem {@link #recalculateStatus}.
 */
public class OrderCoreService {

    /** Số lần sinh lại mã nhận hàng khi gặp mã đã có. Xem {@link #confirmOnlineAfterPaid}. */
    private static final int PICKUP_CODE_ATTEMPTS = 5;

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final CartDAO cartDAO = new CartDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();

    // ============================================================ đọc

    public Order findById(int orderId) {
        Order order = Tx.read(con -> loadFull(con, orderId));
        if (order == null) {
            throw new NotFoundException("Không tìm thấy đơn hàng.");
        }
        return order;
    }

    /**
     * Nạp đơn kèm danh sách món và lần thanh toán gần nhất.
     * Nhận sẵn {@code Connection} để lớp gọi dùng lại được trong giao dịch của mình.
     */
    public Order loadFull(Connection con, int orderId) throws SQLException {
        return fill(con, orderDAO.findById(con, orderId));
    }

    /** Như trên nhưng tra theo khoá chống trùng — dùng khi khách bấm đặt hàng hai lần. */
    public Order loadFullByKey(Connection con, String idempotencyKey) throws SQLException {
        return fill(con, orderDAO.findByIdempotencyKey(con, idempotencyKey));
    }

    private Order fill(Connection con, Order order) throws SQLException {
        if (order == null) {
            return null;
        }
        order.setItems(orderItemDAO.findByOrder(con, order.getOrderId()));
        order.setLatestPayment(paymentDAO.findLatestByOrder(con, order.getOrderId()));
        return order;
    }

    // ============================================================ xác nhận sau thanh toán

    /**
     * Xác nhận đơn đặt trước sau khi tiền đã về.
     * <p>
     * Gọi từ {@link PaymentService} trong cùng giao dịch với việc ghi nhận thanh toán.
     * Ở bước này mới sinh mã nhận hàng và chốt giờ đưa xuống bếp, vì đơn chưa trả tiền
     * thì không cần cả hai thứ đó.
     * <p>
     * Trả về false nếu đơn đã được xác nhận trước đó — tình huống bình thường khi cổng
     * thanh toán gửi kết quả về nhiều lần.
     */
    public boolean confirmOnlineAfterPaid(Connection con, Order order, LocalDateTime now) throws SQLException {
        LocalDateTime releaseAt = order.getPickupTime().minusMinutes(AppConfig.kitchenPrepLeadMinutes());

        // Mã nhận hàng sinh ngẫu nhiên và có ràng buộc duy nhất trong cơ sở dữ liệu. Trùng mã
        // là hiếm nhưng không phải không thể, và nếu để lỗi lọt ra thì cả giao dịch thanh toán
        // bị huỷ — khách mất tiền mà đơn không được xác nhận. Sinh lại mã khác vài lần
        // rẻ hơn nhiều so với hậu quả đó.
        String pickupCode = null;
        int changed = 0;
        for (int attempt = 1; attempt <= PICKUP_CODE_ATTEMPTS; attempt++) {
            pickupCode = PickupCodeGenerator.generate();
            try {
                changed = orderDAO.confirmOnlineAfterPaid(con, order.getOrderId(), pickupCode, releaseAt);
                break;
            } catch (SQLException e) {
                if (!JdbcSupport.isUniqueViolation(e) || attempt == PICKUP_CODE_ATTEMPTS) {
                    throw e;
                }
            }
        }
        if (changed == 0) {
            return false;
        }

        order.setOrderStatus(OrderStatus.CONFIRMED.name());
        order.setPickupCode(pickupCode);
        order.setKitchenReleaseAt(releaseAt);

        // Giờ mới dọn giỏ hàng — đơn đã trả tiền xong thì số hàng trong giỏ mới thật sự
        // trở thành đơn. Dọn từ lúc tạo đơn thì khách trả tiền hỏng là mất giỏ oan.
        if (order.getCustomerId() != null) {
            cartDAO.clear(con, cartDAO.getOrCreateCartId(con, order.getCustomerId(), now));
        }

        auditService.logSystem(con, "ORDER", order.getOrderId(),
                AuditAction.AUTO_CONFIRM, OrderStatus.CONFIRMED.name());
        notificationService.notifyOrderConfirmed(con, order);
        return true;
    }

    /**
     * Hoàn lại tiền của đơn nếu đã thu. Hệ thống không có hoàn một phần nên mỗi đơn
     * hoặc hoàn hết hoặc không hoàn.
     * <p>
     * Dùng chung cho cả hai đường huỷ đơn — khách tự huỷ và thu ngân đóng đơn — nên nằm ở đây
     * chứ không ở một trong hai lớp kia.
     *
     * @return true nếu thật sự có một khoản vừa được hoàn — dùng để viết đúng nội dung
     *         tin nhắn báo cho khách
     */
    public boolean refundIfPaid(Connection con, int orderId, int actorId) throws SQLException {
        Payment paid = paymentDAO.findPaidByOrder(con, orderId);
        if (paid == null) {
            return false;
        }
        paymentDAO.markRefunded(con, paid.getPaymentId(), DateTimeUtil.now());
        auditService.log(con, actorId, "PAYMENT", paid.getPaymentId(),
                AuditAction.PAYMENT_REFUNDED, PaymentStatus.PAID.name(), PaymentStatus.REFUNDED.name());
        return true;
    }

    // ============================================================ tổng hợp trạng thái

    /**
     * Khoá dòng đơn trong giao dịch hiện tại.
     * <p>
     * Mọi thao tác vừa đọc trạng thái các món vừa quyết định số phận của cả đơn đều phải gọi
     * cái này trước: nhận việc trong bếp, khách huỷ đơn, tổng hợp lại trạng thái. Cùng lấy một
     * khoá nghĩa là chúng xếp hàng chứ không chạy chồng lên nhau — cần thiết vì cơ sở dữ liệu
     * bật chế độ đọc ảnh chụp, các lệnh đọc không tự chặn nhau.
     */
    public void lockOrder(Connection con, int orderId) throws SQLException {
        orderDAO.lockForUpdate(con, orderId);
    }

    /**
     * Cập nhật lại trạng thái đơn sau khi một món đổi trạng thái.
     * <p>
     * Trạng thái đơn không do ai bấm mà được suy ra: còn món chưa xong thì đơn đang chế biến,
     * hết món chưa xong thì đơn sẵn sàng.
     * <p>
     * Dòng đơn được khoá trước khi đếm. Không khoá thì hai đầu bếp hoàn thành hai món cuối
     * cùng gần như đồng thời sẽ cùng đếm thấy "vẫn còn món chưa xong", và đơn kẹt vĩnh viễn
     * ở trạng thái đang chế biến dù bếp đã làm xong hết.
     *
     * @return true nếu đơn vừa chuyển sang sẵn sàng
     */
    public boolean recalculateStatus(Connection con, int orderId, LocalDateTime now) throws SQLException {
        orderDAO.lockForUpdate(con, orderId);

        int unready = orderItemDAO.countUnready(con, orderId);
        if (unready > 0) {
            orderDAO.markPreparing(con, orderId);
            return false;
        }

        int changed = orderDAO.markReady(con, orderId, now);
        if (changed == 0) {
            return false;   // đơn đã sẵn sàng từ trước
        }

        Order order = orderDAO.findById(con, orderId);
        auditService.logSystem(con, "ORDER", orderId, AuditAction.ORDER_READY, OrderStatus.READY.name());
        notificationService.notifyOrderReady(con, order);
        return true;
    }
}
