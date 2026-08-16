package com.fastfood.service.shared;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.model.entity.Order;
import com.fastfood.service.Tx;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hai công việc chạy nền của hệ thống.
 * <p>
 * Đây là chỗ thể hiện rõ nhất giá trị của mô hình đặt trước: đơn đã thanh toán vẫn được
 * giữ lại, chỉ tới sát giờ hẹn mới đẩy xuống bếp. Nhờ vậy món không bị làm quá sớm rồi
 * nguội, mà khách vẫn không phải chờ khi tới nơi.
 */
public class ScheduleService {

    private static final Logger LOG = Logger.getLogger(ScheduleService.class.getName());

    private final OrderDAO orderDAO = new OrderDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();

    /**
     * Đưa các đơn đã tới giờ xuống bếp.
     * <p>
     * Mỗi đơn được xử lý trong một giao dịch riêng: một đơn lỗi thì các đơn còn lại vẫn
     * được đưa xuống bình thường, thay vì cả lượt bị huỷ.
     * <p>
     * Việc đánh dấu dùng câu lệnh có điều kiện "chưa từng được đưa xuống", nên dù công việc
     * này chạy lại, hay chạy song song trên hai máy chủ, mỗi đơn cũng chỉ sinh ra một lượt
     * việc cho bếp.
     *
     * @return số đơn thực sự vừa được đưa xuống bếp
     */
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
                        return false;   // đơn đã được đưa xuống bởi lượt chạy khác
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

    /**
     * Huỷ hiệu lực các đơn quá hạn thanh toán.
     * <p>
     * Trạng thái hết hạn khác với huỷ: đơn hết hạn chưa từng được trả tiền và chưa từng
     * được cửa hàng chấp nhận, nên không phát sinh hoàn tiền.
     *
     * @return số đơn vừa bị huỷ hiệu lực
     */
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
                        return false;   // khách vừa kịp thanh toán
                    }
                    auditService.logSystem(con, "ORDER", order.getOrderId(),
                            AuditAction.ORDER_EXPIRED, OrderStatus.EXPIRED.name());
                    // Khách đang chờ một bữa ăn mà không biết đơn đã mất hiệu lực. Báo ngay
                    // để còn kịp đặt lại, thay vì tới nơi mới phát hiện.
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
