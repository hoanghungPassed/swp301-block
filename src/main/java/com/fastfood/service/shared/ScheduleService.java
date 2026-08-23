package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.common.constant.Constants.PaymentStatus;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.service.Tx;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduleService {

    private static final Logger LOG = Logger.getLogger(ScheduleService.class.getName());

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();

    public int releaseDueOrders() {
        LocalDateTime now = DateTimeUtil.now();
        List<Order> due = Tx.read(con -> orderDAO.findDueForRelease(con, now));
        if (due.isEmpty()) {
            return 0;
        }

        int released = 0;
        for (Order order : due) {
            try {
                boolean ok = Tx.write(con -> {
                    int changed = orderDAO.markReleasedToKds(con, order.getOrderId(), now);
                    if (changed == 0) {
                        return false;
                    }
                    auditService.logSystem(con, "ORDER", order.getOrderId(),
                            AuditAction.KDS_RELEASE, "RELEASED");
                    return true;
                });
                if (ok) {
                    released++;
                    LOG.info(() -> "Da dua don #" + order.getOrderId() + " xuong bep (gio hen "
                            + DateTimeUtil.format(order.getPickupTime()) + ")");
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Khong dua duoc don #" + order.getOrderId() + " xuong bep", e);
            }
        }
        return released;
    }

    public int expireStalePayments() {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime deadline = now.minusMinutes(AppConfig.paymentExpiryMinutes());
        List<Order> stale = Tx.read(con -> orderDAO.findExpiredCandidates(con, deadline));
        if (stale.isEmpty()) {
            return 0;
        }

        int expired = 0;
        for (Order order : stale) {
            try {
                boolean ok = Tx.write(con -> {
                    int changed = orderDAO.markExpired(con, order.getOrderId(), now);
                    if (changed == 0) {
                        return false;
                    }
                    auditService.logSystem(con, "ORDER", order.getOrderId(),
                            AuditAction.ORDER_EXPIRED, OrderStatus.EXPIRED.name());
                    notificationService.notifyOrderExpired(con, order);
                    return true;
                });
                if (ok) {
                    expired++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Khong huy hieu luc duoc don #" + order.getOrderId(), e);
            }
        }
        if (expired > 0) {
            LOG.info("Da huy hieu luc " + expired + " don qua han thanh toan.");
        }
        return expired;
    }

    /**
     * Dọn những đơn tại quầy chờ khách quét mã QR mà không ai trả tiền.
     *
     * <p>Đơn loại này được lập trước khi có tiền để sinh được mã QR, và nằm ngoài luồng hết hạn
     * của đơn đặt trước vì trạng thái của nó là CONFIRMED chứ không phải PENDING_PAYMENT (ràng
     * buộc CK_Orders_pendingOnlineOnly không cho đơn tại quầy chờ thanh toán). Không dọn thì
     * khách bỏ đi giữa chừng để lại một đơn treo mãi trong danh sách bán tại quầy, mà bếp thì
     * không bao giờ thấy.
     */
    public int expireAbandonedCounterOrders() {
        LocalDateTime now = DateTimeUtil.now();
        LocalDateTime deadline = now.minusMinutes(AppConfig.paymentExpiryMinutes());
        List<Order> stale = Tx.read(con -> orderDAO.findAbandonedCounterOrders(con, deadline));
        if (stale.isEmpty()) {
            return 0;
        }

        int expired = 0;
        for (Order order : stale) {
            try {
                boolean ok = Tx.write(con -> {
                    /* Khoá rồi đọc lại: danh sách trên đã cũ vài phần nghìn giây, mà đúng lúc
                       này thu ngân có thể đang bấm Xong cho chính đơn đó. */
                    orderDAO.lockForUpdate(con, order.getOrderId());
                    Order fresh = orderDAO.findById(con, order.getOrderId());
                    if (fresh == null || fresh.getReleasedToKdsAt() != null
                            || !OrderStatus.CONFIRMED.name().equals(fresh.getOrderStatus())) {
                        return false;
                    }
                    Payment payment = paymentDAO.findLatestByOrder(con, order.getOrderId());
                    if (payment != null && payment.isPaid()) {
                        return false;
                    }
                    if (orderDAO.markCounterExpired(con, order.getOrderId(), now) == 0) {
                        return false;
                    }
                    if (payment != null && payment.isPending()) {
                        paymentDAO.markFailed(con, payment.getPaymentId());
                        auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                                AuditAction.PAYMENT_FAILED, PaymentStatus.FAILED.name());
                    }
                    auditService.logSystem(con, "ORDER", order.getOrderId(),
                            AuditAction.ORDER_EXPIRED, OrderStatus.EXPIRED.name()
                                    + ": khach khong hoan tat thanh toan bang ma QR");
                    return true;
                });
                if (ok) {
                    expired++;
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Khong dong duoc don quay #" + order.getOrderId(), e);
            }
        }
        if (expired > 0) {
            LOG.info("Da dong " + expired + " don tai quay khong ai tra tien qua ma QR.");
        }
        return expired;
    }
}
