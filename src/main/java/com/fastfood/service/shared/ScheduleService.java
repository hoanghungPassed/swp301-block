package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.service.Tx;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduleService {

    private static final Logger LOG = Logger.getLogger(ScheduleService.class.getName());

    private final OrderDAO orderDAO = new OrderDAO();
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
}
