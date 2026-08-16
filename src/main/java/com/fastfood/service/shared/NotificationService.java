package com.fastfood.service.shared;

import com.fastfood.common.constant.NotificationEvent;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.common.util.MoneyUtil;
import com.fastfood.dao.shared.NotificationDAO;
import com.fastfood.integration.notification.NotificationSender;
import com.fastfood.integration.notification.NotificationSenders;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.Notification;
import com.fastfood.model.entity.Order;
import com.fastfood.service.Tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Gửi tin cho khách và lưu lại nhật ký đã gửi.
 * <p>
 * Tin báo món sẵn sàng bắt buộc phải kèm giờ hẹn và mã nhận hàng — thiếu mã thì khách
 * tới quầy không nhận được món, và nhân viên phải tra tay từng đơn.
 * <p>
 * Bảng tin còn là <b>hộp thông báo trong ứng dụng</b>, không chỉ là nhật ký của việc gửi đi.
 * Bản chạy thử gửi qua kênh giả lập nên không có bức thư nào thật sự tới hộp thư của khách;
 * màn hình {@code /notifications} vì thế là nơi duy nhất khách đọc được những tin này. Kể cả
 * khi sau này nối cổng gửi thư thật, hộp trong ứng dụng vẫn cần: thư có thể rơi vào mục rác
 * hoặc gửi hỏng, và {@code status = 'FAILED'} chỉ có ý nghĩa nếu có chỗ để khách đọc bù.
 */
public class NotificationService {

    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final NotificationSender sender = NotificationSenders.fromConfig();

    public void notifyOrderConfirmed(Connection con, Order order) throws SQLException {
        if (!order.isOnline()) {
            return;   // Khách mua tại quầy đứng ngay đó, không cần nhắn tin
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

    /**
     * Báo khách đơn đã bị huỷ.
     * <p>
     * Nói rõ tiền có được hoàn hay không, ngay trong tin: đây là câu đầu tiên khách sẽ hỏi,
     * và bắt khách gọi lên cửa hàng để biết là cách chắc chắn nhất biến một sự cố nhỏ
     * thành một khiếu nại.
     */
    public void notifyOrderCancelled(Connection con, Order order, String reason, boolean refunded)
            throws SQLException {
        if (!order.isOnline() || order.getCustomerId() == null) {
            return;   // khách tại quầy đứng ngay đó, không có gì để báo
        }
        String subject = "Đơn hàng #" + order.getOrderId() + " đã bị huỷ";
        String content = String.format("Đơn #%d đã bị huỷ. Lý do: %s.%s",
                order.getOrderId(),
                reason == null || reason.isBlank() ? "không ghi rõ" : reason,
                refunded
                        ? " Toàn bộ " + MoneyUtil.format(order.getTotalAmount())
                          + " đã được hoàn lại về phương thức thanh toán ban đầu."
                        : "");
        record(con, order, NotificationEvent.ORDER_CANCELLED, subject, content);
    }

    /**
     * Báo khách đơn đã hết hiệu lực vì quá hạn thanh toán.
     * Đơn hết hiệu lực chưa từng được trả tiền nên không kèm chuyện hoàn tiền.
     */
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

    /**
     * Báo khách khoản tiền vừa trả đã được hoàn lại vì đơn không còn hiệu lực.
     * <p>
     * Đây là tin quan trọng nhất trong cả nhóm. Khách bấm trả tiền khi đơn vừa hết hạn thì
     * tiền thật sự đã đi, và nếu họ đóng trang thanh toán trước khi thấy thông báo trên màn
     * hình thì không còn cách nào biết chuyện gì đã xảy ra.
     */
    public void notifyRefundedOrderGone(Connection con, Order order) throws SQLException {
        if (!order.isOnline() || order.getCustomerId() == null) {
            return;
        }
        String subject = "Đã hoàn tiền cho đơn hàng #" + order.getOrderId();
        String content = String.format(
                "Khoản thanh toán %s cho đơn #%d đã được hoàn lại. Đơn hết hiệu lực trước khi "
                + "giao dịch hoàn tất nên cửa hàng không giữ lại khoản tiền này. "
                + "Tiền về tài khoản theo thời gian xử lý của ngân hàng.",
                MoneyUtil.format(order.getTotalAmount()), order.getOrderId());
        record(con, order, NotificationEvent.ORDER_EXPIRED, subject, content);
    }

    public List<Notification> findByOrder(int orderId) {
        return Tx.read(con -> notificationDAO.findByOrder(con, orderId));
    }

    // ============================================================ hộp thông báo của khách

    /**
     * Một trang trong hộp thông báo của khách.
     * <p>
     * Có phân trang chứ không cắt cứng vài tin gần nhất: mỗi đơn sinh ra một tới hai tin, nên
     * một khách quen tích lại hàng chục tin rất nhanh, và tin cần tìm lại thường là tin cũ —
     * "đơn đó bị huỷ vì lý do gì" là câu hỏi được đặt ra sau đó vài ngày.
     */
    public Page<Notification> pageOfUser(int userId, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                notificationDAO.findByUser(con, userId, offset, Page.SIZE),
                page, Page.SIZE,
                notificationDAO.countByUser(con, userId)));
    }

    /** Số tin chưa đọc, cho huy hiệu trên thanh điều hướng. */
    public int unreadCount(int userId) {
        return Tx.read(con -> notificationDAO.countUnread(con, userId));
    }

    public int markAllRead(int userId) {
        LocalDateTime now = DateTimeUtil.now();
        return Tx.write(con -> notificationDAO.markAllRead(con, userId, now));
    }

    /**
     * Đánh dấu đã đọc tin của một đơn — gọi khi khách mở trang theo dõi đơn đó.
     * <p>
     * Ghi trong một thao tác đọc trang là đúng ở đây: khách nhìn thấy tin thì tin đã được đọc,
     * không cần thêm một cú bấm để nói lại điều đó.
     */
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
