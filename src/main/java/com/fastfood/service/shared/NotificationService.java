package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.NotificationEvent;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.MoneyUtil;
import com.fastfood.dao.shared.NotificationDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.UserEntities.Notification;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final NotificationSender sender = NotificationSenders.fromConfig();

    public void notifyOrderConfirmed(Connection con, Order order) throws SQLException {
        if (!order.isOnline()) {
            return;
        }
        String subject = "Đơn hàng #" + order.getOrderId() + " đã được xác nhận";
        String content = String.format(
                "Đơn #%d đã thanh toán thành công %s. Giờ nhận hàng: %s. Mã nhận hàng: %s.",
                order.getOrderId(),
                MoneyUtil.format(order.getTotalAmount()),
                DateTimeUtil.format(order.getPickupTime()),
                order.getPickupCode());
        record(con, order, NotificationEvent.ORDER_CONFIRMED, subject, content);
    }

    public void notifyOrderReady(Connection con, Order order) throws SQLException {
        if (!order.isOnline()) {
            return;
        }
        String subject = "Đơn hàng #" + order.getOrderId() + " đã sẵn sàng";
        String content = String.format(
                "Món của bạn đã sẵn sàng. Vui lòng đến quầy trước %s và đưa mã %s để nhận hàng.",
                DateTimeUtil.format(order.getPickupTime()),
                order.getPickupCode());
        record(con, order, NotificationEvent.ORDER_READY, subject, content);
    }

    public void notifyOrderExpired(Connection con, Order order) throws SQLException {
        if (!order.isOnline() || order.getCustomerId() == null) {
            return;
        }
        String subject = "Đơn hàng #" + order.getOrderId() + " đã hết hiệu lực";
        String content = String.format(
                "Đơn #%d không được thanh toán trong thời gian giữ chỗ nên đã hết hiệu lực. "
                + "Bạn không bị trừ tiền. Vui lòng đặt lại nếu vẫn muốn dùng bữa.",
                order.getOrderId());
        record(con, order, NotificationEvent.ORDER_EXPIRED, subject, content);
    }

    /* Tiền vào tài khoản cửa hàng nhưng đơn đã hết hiệu lực từ trước. Không hứa hoàn tự động
       — hệ thống không làm được việc đó — mà nói thẳng số tiền và mã đơn để khách cầm đúng
       thông tin đi hỏi, và mời khách liên hệ. Im lặng ở đây là tệ nhất: khách vừa mất suất
       ăn vừa thấy tài khoản bị trừ. */
    public void notifyPaymentOrphaned(Connection con, Order order) throws SQLException {
        if (!order.isOnline() || order.getCustomerId() == null) {
            return;
        }
        String subject = "Cần đối chiếu khoản thanh toán cho đơn hàng #" + order.getOrderId();
        String content = String.format(
                "Khoản thanh toán %s đã được ghi nhận, nhưng đơn #%d hết hiệu lực trước khi giao "
                + "dịch hoàn tất nên đơn không được giữ. Vui lòng liên hệ cửa hàng kèm mã đơn "
                + "#%d để được đối chiếu và xử lý khoản tiền này.",
                MoneyUtil.format(order.getTotalAmount()), order.getOrderId(), order.getOrderId());
        record(con, order, NotificationEvent.ORDER_EXPIRED, subject, content);
    }

    public List<Notification> findByOrder(int orderId) {
        return Tx.read(con -> notificationDAO.findByOrder(con, orderId));
    }

    public Page<Notification> pageOfUser(int userId, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                notificationDAO.findByUser(con, userId, offset, Page.SIZE),
                page, Page.SIZE,
                notificationDAO.countByUser(con, userId)));
    }

    public int unreadCount(int userId) {
        return Tx.read(con -> notificationDAO.countUnread(con, userId));
    }

    public int markAllRead(int userId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> notificationDAO.markAllRead(con, userId, now));
    }

    public void markReadByOrder(int userId, int orderId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> notificationDAO.markReadByOrder(con, userId, orderId, now));
    }

    private void record(Connection con, Order order, NotificationEvent event,
                        String subject, String content) throws SQLException {
        boolean sent = sender.send(order.getCustomerEmail(), subject, content);

        Notification n = new Notification();
        n.setUserId(order.getCustomerId());
        n.setOrderId(order.getOrderId());
        n.setChannel(sender.getChannel());
        n.setEventType(event.name());
        n.setContent(content);
        n.setStatus(sent ? "SENT" : "FAILED");
        n.setSentAt(sent ? DateTimeUtil.now() : null);
        notificationDAO.insert(con, n);
    }
}
