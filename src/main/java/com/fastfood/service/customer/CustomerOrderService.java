package com.fastfood.service.customer;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.OrderItemStatus;
import com.fastfood.common.constant.Constants.OrderSource;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.entity.OrderEntities.CartItem;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.UserEntities.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.NotificationService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class CustomerOrderService {

    private static final String KITCHEN_ALREADY_STARTED =
            "Bếp đã bắt đầu chuẩn bị đơn này nên không thể huỷ. Vui lòng liên hệ nhân viên tại quầy.";

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CartDAO cartDAO = new CartDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();
    private final OrderCoreService orderCore = new OrderCoreService();

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

    public Order findForCustomer(int orderId, int customerId) {
        Order order = Tx.read(con -> orderCore.loadFull(con, orderId));
        if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
            throw new NotFoundException("Không tìm thấy đơn hàng.");
        }
        return order;
    }

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

    public List<Order> activeOrdersOfCustomer(int customerId) {
        return Tx.read(con -> orderDAO.findActiveByCustomer(con, customerId));
    }

    public boolean cancelByCustomer(int orderId, int customerId) {
        return Tx.write(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            String before = order.getOrderStatus();
            boolean unpaid = OrderStatus.PENDING_PAYMENT.name().equals(before);
            if (!unpaid && !OrderStatus.CONFIRMED.name().equals(before)) {
                throw new BusinessException(cannotCancelReason(before));
            }

            orderDAO.lockForUpdate(con, orderId);
            if (orderItemDAO.countInProgress(con, orderId) > 0) {
                throw new BusinessException(KITCHEN_ALREADY_STARTED);
            }

            int changed = orderDAO.markCancelled(con, orderId, DateTimeUtil.now());
            if (changed == 0) {
                throw new BusinessException("Đơn vừa được xử lý bởi người khác. Vui lòng tải lại.");
            }
            auditService.log(con, customerId, "ORDER", orderId, AuditAction.ORDER_CANCELLED,
                    before, OrderStatus.CANCELLED.name());
            boolean refunded = orderCore.refundIfPaid(con, orderId, customerId);

            if (!unpaid) {
                notificationService.notifyOrderCancelled(con, order, "khách tự huỷ", refunded);
            }
            return refunded;
        });
    }

    private static String cannotCancelReason(String status) {
        OrderStatus current = OrderStatus.valueOf(status);
        return switch (current) {
            case PREPARING, READY -> KITCHEN_ALREADY_STARTED;
            case COMPLETED -> "Đơn này đã được giao cho khách nên không huỷ được nữa.";
            case CANCELLED -> "Đơn này đã được huỷ trước đó rồi.";
            case EXPIRED -> "Đơn này đã hết hiệu lực vì quá hạn thanh toán. Vui lòng đặt lại.";
            default -> "Đơn này không còn ở trạng thái huỷ được.";
        };
    }
}
