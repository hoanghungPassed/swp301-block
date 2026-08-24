package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.util.PickupCodeGenerator;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class OrderCoreService {

    private static final int PICKUP_CODE_ATTEMPTS = 5;

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final CartDAO cartDAO = new CartDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();

    public Order findById(int orderId) {
        Order order = Tx.read(con -> loadFull(con, orderId));
        if (order == null) {
            throw new NotFoundException("Không tìm thấy đơn hàng.");
        }
        return order;
    }

    public Order loadFull(Connection con, int orderId) throws SQLException {
        return fill(con, orderDAO.findById(con, orderId));
    }

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

    public boolean confirmOnlineAfterPaid(Connection con, Order order, LocalDateTime now) throws SQLException {
        LocalDateTime releaseAt = order.getPickupTime().minusMinutes(AppConfig.kitchenPrepLeadMinutes());
        // Đặt thời gian release bếp chính là thời điểm hiện tại khi khách thanh toán thành công
//        LocalDateTime releaseAt = now;
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

        // Đẩy đơn xuống Bếp ngay lập tức (Cập nhật released_to_kds_at = now)
//        orderDAO.markReleasedToKds(con, order.getOrderId(), now);
//        order.setReleasedToKdsAt(now);
//        auditService.logSystem(con, "ORDER", order.getOrderId(), AuditAction.KDS_RELEASE, "RELEASED");

        if (order.getCustomerId() != null) {
            cartDAO.clear(con, cartDAO.getOrCreateCartId(con, order.getCustomerId(), now));
        }

        auditService.logSystem(con, "ORDER", order.getOrderId(),
                AuditAction.AUTO_CONFIRM, OrderStatus.CONFIRMED.name());
        notificationService.notifyOrderConfirmed(con, order);
        return true;
    }

    public void lockOrder(Connection con, int orderId) throws SQLException {
        orderDAO.lockForUpdate(con, orderId);
    }

    public boolean recalculateStatus(Connection con, int orderId, LocalDateTime now) throws SQLException {
        orderDAO.lockForUpdate(con, orderId);

        int unready = orderItemDAO.countUnready(con, orderId);
        if (unready > 0) {
            orderDAO.markPreparing(con, orderId);
            return false;
        }

        int changed = orderDAO.markReady(con, orderId, now);
        if (changed == 0) {
            return false;
        }

        Order order = orderDAO.findById(con, orderId);
        auditService.logSystem(con, "ORDER", orderId, AuditAction.ORDER_READY, OrderStatus.READY.name());
        notificationService.notifyOrderReady(con, order);
        return true;
    }
}
