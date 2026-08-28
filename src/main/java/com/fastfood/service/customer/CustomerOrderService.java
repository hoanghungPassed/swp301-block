package com.fastfood.service.customer;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.OrderItemStatus;
import com.fastfood.common.constant.Constants.OrderSource;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.constant.Constants.PaymentStatus;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.OrderEntities.CartItem;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class CustomerOrderService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CartDAO cartDAO = new CartDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();
    private final OrderCoreService orderCore = new OrderCoreService();

    /**
     * Validate giờ nhận và tạo đơn online; nếu request bị gửi lại với cùng idempotency key thì
     * trả đúng đơn đã tạo thay vì sinh thêm đơn trùng.
     */
    public Order createOnlineOrder(int customerId, LocalDateTime pickupTime, String idempotencyKey) {
        LocalDateTime now = DateTimeUtil.now();
        validatePickupTime(pickupTime, now);

        try {
            return doCreateOnlineOrder(customerId, pickupTime, idempotencyKey, now);
        } catch (RuntimeException e) {
            if (idempotencyKey == null || !JdbcSupport.isUniqueViolation(e)) {
                throw e;
            }
            Order existing = Tx.read(con -> orderCore.loadFullByKey(con, idempotencyKey));
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    /**
     * Trong một transaction, kiểm tra email/giỏ/đơn chờ, tạo Orders và sao chép từng dòng giỏ
     * sang OrderItem với tên, giá tại thời điểm đặt rồi tính tổng tiền phía server.
     */
    private Order doCreateOnlineOrder(int customerId, LocalDateTime pickupTime,
                                      String idempotencyKey, LocalDateTime now) {
        return Tx.write(con -> {
            requireVerifiedEmail(con, customerId);

            if (idempotencyKey != null) {
                Order existing = orderDAO.findByIdempotencyKey(con, idempotencyKey);
                if (existing != null) {
                    existing.setItems(orderItemDAO.findByOrder(con, existing.getOrderId()));
                    return existing;
                }
            }

            Order pending = orderDAO.findPendingByCustomer(con, customerId);
            if (pending != null) {
                throw new BusinessException("Bạn đang có đơn #" + pending.getOrderId()
                        + " chờ thanh toán. Mở đơn đó ở mục \"Đơn của tôi\" để thanh toán nốt,"
                        + " hoặc huỷ đi rồi đặt lại — đơn chưa thanh toán huỷ được ngay.");
            }

            int cartId = cartDAO.getOrCreateCartId(con, customerId, now);
            List<CartItem> cartItems = cartDAO.findItems(con, cartId);
            if (cartItems.isEmpty()) {
                throw new BusinessException("Giỏ hàng đang trống.");
            }

            Order order = new Order();
            order.setCustomerId(customerId);
            order.setCreatedByUserId(customerId);
            order.setOrderSource(OrderSource.ONLINE_PREORDER.name());
            order.setOrderStatus(OrderStatus.PENDING_PAYMENT.name());
            order.setPickupTime(pickupTime);
            order.setIdempotencyKey(idempotencyKey);
            order.setCreatedAt(now);
            order.setTotalAmount(BigDecimal.ZERO);
            orderDAO.insert(con, order);

            BigDecimal total = BigDecimal.ZERO;
            for (CartItem line : cartItems) {
                Product product = productDAO.findForCheckout(con, line.getProductId());
                if (product == null || !product.isOrderable()) {
                    throw new BusinessException("Món \"" + line.getProductName()
                            + "\" vừa hết hàng. Vui lòng cập nhật lại giỏ hàng.");
                }
                OrderItem item = new OrderItem();
                item.setOrderId(order.getOrderId());
                item.setProductId(product.getProductId());
                item.setProductNameSnapshot(product.getName());
                item.setUnitPrice(product.getPrice());
                item.setQuantity(line.getQuantity());
                item.setItemStatus(OrderItemStatus.WAITING.name());
                orderItemDAO.insert(con, item);
                order.getItems().add(item);
                total = total.add(item.getLineTotal());
            }

            order.setTotalAmount(total);
            orderDAO.updateTotal(con, order.getOrderId(), total);

            auditService.log(con, customerId, "ORDER", order.getOrderId(),
                    AuditAction.ORDER_CREATED, null, OrderStatus.PENDING_PAYMENT.name());
            return order;
        });
    }

    /** Chặn đặt đơn online nếu tài khoản không tồn tại hoặc email chưa được xác thực. */
    private void requireVerifiedEmail(java.sql.Connection con, int customerId) throws java.sql.SQLException {
        User customer = userDAO.findById(con, customerId);
        if (customer == null) {
            throw new NotFoundException("Không tìm thấy tài khoản.");
        }
        if (!customer.isEmailVerified()) {
            throw new BusinessException("Bạn cần xác thực địa chỉ email " + customer.getEmail()
                    + " trước khi đặt đơn online. Mở thư chúng tôi đã gửi và bấm liên kết trong đó,"
                    + " hoặc bấm \"Gửi lại thư xác thực\" ở dải nhắc trên đầu trang.");
        }
    }

    /**
     * Kiểm tra giờ nhận: bắt buộc, đủ thời gian chuẩn bị, không quá 7 ngày và nằm trong giờ mở
     * cửa đã cấu hình.
     */
    public void validatePickupTime(LocalDateTime pickupTime, LocalDateTime now) {
        if (pickupTime == null) {
            throw new ValidationException("Vui lòng chọn giờ đến lấy hàng.");
        }
        int minLead = AppConfig.pickupMinLeadMinutes();
        if (pickupTime.isBefore(now.plusMinutes(minLead))) {
            throw new ValidationException("Giờ nhận hàng phải cách hiện tại ít nhất "
                    + minLead + " phút.");
        }
        if (pickupTime.isAfter(now.plusDays(7))) {
            throw new ValidationException("Chỉ nhận đặt trước tối đa 7 ngày.");
        }

        int openHour = AppConfig.storeOpenHour();
        int closeHour = AppConfig.storeCloseHour();
        LocalDateTime earliestThatDay = pickupTime.toLocalDate().atTime(openHour, 0)
                .plusMinutes(AppConfig.kitchenPrepLeadMinutes());
        if (pickupTime.isBefore(earliestThatDay) || pickupTime.getHour() >= closeHour) {
            throw new ValidationException(String.format(
                    "Cửa hàng mở cửa %02d:00–%02d:00. Giờ nhận hàng sớm nhất trong ngày là %s.",
                    openHour, closeHour, DateTimeUtil.formatTime(earliestThatDay)));
        }
    }

    /** Tính mốc nhận hàng hợp lệ sớm nhất để làm min/suggested value cho form checkout. */
    public LocalDateTime earliestPickupTime() {
        LocalDateTime candidate = DateTimeUtil.now().plusMinutes(AppConfig.pickupMinLeadMinutes());
        int openHour = AppConfig.storeOpenHour();
        int closeHour = AppConfig.storeCloseHour();
        LocalDateTime openThatDay = candidate.toLocalDate().atTime(openHour, 0)
                .plusMinutes(AppConfig.kitchenPrepLeadMinutes());

        if (candidate.isBefore(openThatDay)) {
            return openThatDay;
        }
        if (candidate.getHour() >= closeHour) {
            return openThatDay.plusDays(1);
        }
        return candidate;
    }

    /**
     * Khách tự bỏ đơn của mình khi chưa trả tiền, để đặt lại đơn khác ngay.
     *
     * <p>Không có trạng thái "đã huỷ" riêng: đơn đi vào đúng EXPIRED như khi hết 15 phút giữ
     * chỗ. Thêm một trạng thái nữa chỉ để phân biệt "khách bấm bỏ" với "hết giờ" là bắt mọi
     * câu truy vấn, mọi bộ lọc và mọi màn hình về sau phải nhớ thêm một nhánh, trong khi hai
     * việc ấy kết thúc y hệt nhau: đơn không còn hiệu lực và không ai bị trừ tiền. Ai bấm thì
     * đã nằm ở cột actor_id của dòng nhật ký.
     *
     * <p>Khoá đơn rồi mới đọc lại, và từ chối nếu đã có khoản thu PAID: khách có thể vừa bấm
     * bỏ ở tab này đúng lúc tiền từ tab thanh toán kia về tới. Trường hợp tiền về sau khi đơn
     * đã đóng thì đã có đường xử lý riêng — xem PAYMENT_ORPHANED trong PaymentService.
     *
     * <p>Giỏ hàng giữ nguyên: món chỉ bị dọn khỏi giỏ lúc đơn được thanh toán xong, nên bỏ đơn
     * xong khách vào giỏ là đặt lại được ngay, không phải chọn lại từ đầu.
     */
    /**
     * Khóa và kiểm tra đơn thuộc customer, chỉ cho bỏ đơn PENDING_PAYMENT chưa nhận tiền rồi
     * chuyển đơn sang EXPIRED và đóng payment còn chờ.
     */
    public void cancelPendingOrder(int orderId, int customerId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            orderDAO.lockForUpdate(con, orderId);
            Order order = orderDAO.findById(con, orderId);
            if (order == null || order.getCustomerId() == null
                    || order.getCustomerId() != customerId) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
                throw new BusinessException("Đơn #" + orderId + " không còn ở trạng thái chờ "
                        + "thanh toán nên không bỏ được. Trạng thái hiện tại: "
                        + order.getOrderStatus() + ".");
            }

            Payment payment = paymentDAO.findLatestByOrder(con, orderId);
            if (payment != null && payment.isPaid()) {
                throw new BusinessException("Đơn #" + orderId + " vừa nhận được thanh toán nên "
                        + "không bỏ được nữa. Tải lại trang để xem trạng thái mới.");
            }
            if (orderDAO.markExpired(con, orderId, now) == 0) {
                throw new BusinessException("Đơn #" + orderId + " vừa đổi trạng thái. "
                        + "Tải lại trang rồi thử lại.");
            }
            if (payment != null && payment.isPending()) {
                paymentDAO.markFailed(con, payment.getPaymentId());
                auditService.log(con, customerId, "PAYMENT", payment.getPaymentId(),
                        AuditAction.PAYMENT_FAILED, payment.getPaymentStatus(),
                        PaymentStatus.FAILED.name());
            }
            auditService.log(con, customerId, "ORDER", orderId, AuditAction.ORDER_EXPIRED,
                    OrderStatus.PENDING_PAYMENT.name(),
                    OrderStatus.EXPIRED.name() + ": khach tu bo don truoc khi thanh toan");
        });
    }

    /** Tải đầy đủ đơn và chỉ trả về khi customer hiện tại là chủ đơn. */
    public Order findForCustomer(int orderId, int customerId) {
        Order order = Tx.read(con -> orderCore.loadFull(con, orderId));
        if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
            throw new NotFoundException("Không tìm thấy đơn hàng.");
        }
        return order;
    }

    /** Lọc và phân trang lịch sử đơn của customer theo trạng thái và khoảng ngày. */
    public Page<Order> historyOfCustomer(int customerId, String status,
                                         LocalDate fromDate, LocalDate toDate, int pageNo) {
        String safeStatus = validStatus(status);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.atTime(LocalTime.MAX).withNano(0);

        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                orderDAO.findByCustomer(con, customerId, safeStatus, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                orderDAO.countByCustomer(con, customerId, safeStatus, from, to)));
    }

    /** Chỉ giữ giá trị status có trong OrderStatus; giá trị lạ được xem là không lọc. */
    private static String validStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (OrderStatus known : OrderStatus.values()) {
            if (known.name().equals(status)) {
                return status;
            }
        }
        return null;
    }

    /** Lấy các đơn chưa kết thúc của customer để hiển thị khu vực theo dõi nhanh. */
    public List<Order> activeOrdersOfCustomer(int customerId) {
        return Tx.read(con -> orderDAO.findActiveByCustomer(con, customerId));
    }

}
