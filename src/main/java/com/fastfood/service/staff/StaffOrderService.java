package com.fastfood.service.staff;

import com.fastfood.common.constant.Constants.AuditAction;
import com.fastfood.common.constant.Constants.BusinessRule;
import com.fastfood.common.constant.Constants.OrderItemStatus;
import com.fastfood.common.constant.Constants.OrderSource;
import com.fastfood.common.constant.Constants.OrderStatus;
import com.fastfood.common.constant.Constants.PaymentMethod;
import com.fastfood.common.constant.Constants.PaymentStatus;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.TransactionDAO;
import com.fastfood.dao.staff.ShiftDAO;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.dto.Dtos.PosCartLine;
import com.fastfood.model.dto.Dtos.PosLine;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.OperationEntities.Shift;
import com.fastfood.model.entity.OrderEntities.Transaction;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.NotificationService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StaffOrderService {

    private static final String POS_TERMINAL = "POS_TERMINAL";

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();
    private final OrderCoreService orderCore = new OrderCoreService();

    public Order createPosOrder(int cashierId, List<PosLine> lines, PaymentMethod method,
                                String terminalReference) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Chưa chọn món nào.");
        }
        if (method == null) {
            throw new ValidationException("Chưa chọn hình thức thanh toán.");
        }
        String reference = terminalReference == null ? "" : terminalReference.trim().toUpperCase();
        if (method == PaymentMethod.ONLINE_GATEWAY && reference.isEmpty()) {
            throw new ValidationException(
                    "Vui lòng nhập mã giao dịch in trên biên lai của máy thanh toán.");
        }
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            Order order = new Order();
            order.setCustomerId(null);
            order.setCreatedByUserId(cashierId);
            Shift openShift = shiftDAO.findOpenOf(con, cashierId);
            order.setShiftId(openShift == null ? null : openShift.getShiftId());
            order.setOrderSource(OrderSource.POS.name());
            order.setOrderStatus(OrderStatus.CONFIRMED.name());
            order.setCreatedAt(now);
            order.setReleasedToKdsAt(now);
            order.setTotalAmount(BigDecimal.ZERO);
            orderDAO.insert(con, order);

            BigDecimal total = BigDecimal.ZERO;
            for (PosLine line : lines) {
                if (line.getQuantity() <= 0 || line.getQuantity() > BusinessRule.MAX_QUANTITY_PER_LINE) {
                    throw new ValidationException("Số lượng mỗi món phải từ 1 đến "
                            + BusinessRule.MAX_QUANTITY_PER_LINE + ".");
                }
                Product product = productDAO.findForCheckout(con, line.getProductId());
                if (product == null) {
                    throw new BusinessException("Trong phiếu có món không còn trong hệ thống. "
                            + "Hãy bỏ món đó ra rồi thu tiền lại.");
                }
                if (!product.isOrderable()) {
                    throw new BusinessException("Món \"" + product.getName() + "\" hiện không còn "
                            + "phục vụ. Hãy bỏ món đó ra khỏi phiếu rồi thu tiền lại.");
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

            Payment payment = new Payment();
            payment.setOrderId(order.getOrderId());
            payment.setMethod(method.name());
            payment.setAmount(total);
            payment.setPaymentStatus(PaymentStatus.PAID.name());
            payment.setAttemptNo(1);
            payment.setCreatedAt(now);
            payment.setPaidAt(now);
            paymentDAO.insert(con, payment);
            order.setLatestPayment(payment);

            if (method == PaymentMethod.ONLINE_GATEWAY) {
                Transaction txn = transactionDAO.newTransaction(payment.getPaymentId(), POS_TERMINAL,
                        reference, "SUCCESS", "Thu tại quầy, mã biên lai do thu ngân nhập.", now);
                if (!transactionDAO.insertIfNew(con, txn)) {
                    throw new BusinessException("Mã giao dịch \"" + reference
                            + "\" đã được ghi nhận cho một đơn khác. Vui lòng kiểm tra lại biên lai — "
                            + "một lần thanh toán chỉ dùng được cho một đơn.");
                }
            }

            auditService.log(con, cashierId, "ORDER", order.getOrderId(),
                    AuditAction.ORDER_CREATED, null, OrderStatus.CONFIRMED.name());
            auditService.log(con, cashierId, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_PAID, null, method.name());
            auditService.log(con, cashierId, "ORDER", order.getOrderId(),
                    AuditAction.KDS_RELEASE, null, "RELEASED");
            return order;
        });
    }

    public List<PosCartLine> describeCart(Map<Integer, Integer> cart) {
        if (cart == null || cart.isEmpty()) {
            return List.of();
        }
        return Tx.read(con -> {
            List<PosCartLine> lines = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                Product product = productDAO.findForCheckout(con, entry.getKey());
                lines.add(product == null
                        ? PosCartLine.missing(entry.getKey(), entry.getValue())
                        : new PosCartLine(product.getProductId(), product.getName(),
                                product.getPrice(), entry.getValue(), product.isOrderable()));
            }
            return lines;
        });
    }

    public Order findById(int orderId) {
        return orderCore.findById(orderId);
    }

    public List<Order> dashboard(String tab) {
        return Tx.read(con -> orderDAO.findForDashboard(con, tab, DateTimeUtil.now(),
                AppConfig.pickupOverdueMinutes()));
    }

    public Page<Order> search(String source, String status,
                              LocalDateTime from, LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                orderDAO.search(con, source, status, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                orderDAO.countSearch(con, source, status, from, to)));
    }

    public Order findByPickupCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new ValidationException("Vui lòng nhập mã nhận hàng.");
        }
        Order order = Tx.read(con -> {
            Order o = orderDAO.findByPickupCode(con, normalized);
            if (o != null) {
                o.setItems(orderItemDAO.findByOrder(con, o.getOrderId()));
                o.setLatestPayment(paymentDAO.findLatestByOrder(con, o.getOrderId()));
            }
            return o;
        });
        if (order == null) {
            throw new NotFoundException("Không tìm thấy đơn hàng với mã này.");
        }
        return order;
    }

    public void cancelByStaff(int orderId, int staffId, String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty()) {
            throw new ValidationException("Vui lòng nhập lý do huỷ đơn.");
        }
        Tx.writeVoid(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (order.statusEnum().isFinal()) {
                throw new BusinessException("Đơn đã kết thúc, không huỷ được nữa.");
            }

            int changed = orderDAO.markCancelledByStaff(con, orderId, DateTimeUtil.now());
            if (changed == 0) {
                throw new BusinessException("Đơn vừa được xử lý bởi người khác. Vui lòng tải lại.");
            }
            auditService.log(con, staffId, "ORDER", orderId, AuditAction.ORDER_CANCELLED,
                    order.getOrderStatus(), OrderStatus.CANCELLED.name() + ": " + note);
            boolean refunded = orderCore.refundIfPaid(con, orderId, staffId);
            notificationService.notifyOrderCancelled(con, order, note, refunded);
        });
    }

    public List<OrderItem> awaitingCounter() {
        return Tx.read(orderItemDAO::findAwaitingCounter);
    }

    public int countAwaitingCounter() {
        return Tx.read(orderItemDAO::countAwaitingCounter);
    }

    public List<Order> readyOrdersForCounter() {
        return Tx.read(con -> {
            List<Order> orders = orderDAO.findForDashboard(con, "READY", DateTimeUtil.now(),
                    AppConfig.pickupOverdueMinutes());
            for (Order order : orders) {
                order.setItems(orderItemDAO.findByOrder(con, order.getOrderId()));
            }
            return orders;
        });
    }

    public void receiveAtCounter(int orderItemId, int cashierId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.receiveAtCounter(con, orderItemId, cashierId, now);
            if (changed == 0) {
                if (item.isReceived()) {
                    throw new BusinessException("Món này đã được nhận rồi.");
                }
                throw new BusinessException("Bếp chưa bàn giao món này ra quầy.");
            }
            auditService.log(con, cashierId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_RECEIVED, "AT_COUNTER", "RECEIVED");
        });
    }

    public Order handoff(int orderId, int cashierId, String presentedCode) {
        LocalDateTime now = DateTimeUtil.now();
        try {
            return doHandoff(orderId, cashierId, presentedCode, now);
        } catch (PickupCodeMismatch e) {
            auditService.logRejected(cashierId, "ORDER", orderId,
                    AuditAction.PICKUP_VERIFY_FAILED, e.expected, e.given);
            throw new BusinessException("Mã nhận hàng không khớp với đơn này.");
        }
    }

    private static final class PickupCodeMismatch extends RuntimeException {
        private final transient String expected;
        private final transient String given;

        PickupCodeMismatch(String expected, String given) {
            super(null, null, false, false);
            this.expected = expected;
            this.given = given;
        }
    }

    private Order doHandoff(int orderId, int cashierId, String presentedCode, LocalDateTime now) {
        return Tx.write(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (!OrderStatus.READY.name().equals(order.getOrderStatus())) {
                throw new BusinessException("Đơn chưa sẵn sàng để giao.");
            }
            int notReceived = orderItemDAO.countNotReceived(con, orderId);
            if (notReceived > 0) {
                throw new BusinessException("Còn " + notReceived + " món chưa được nhận tại quầy. "
                        + "Vào màn hình Quầy giao nhận để nhận món từ bếp trước khi giao cho khách.");
            }
            Payment paid = paymentDAO.findPaidByOrder(con, orderId);
            if (paid == null) {
                Payment latest = paymentDAO.findLatestByOrder(con, orderId);
                if (latest != null && PaymentStatus.REFUNDED.name().equals(latest.getPaymentStatus())) {
                    throw new BusinessException("Đơn này đã được hoàn tiền nên không giao được. "
                            + "Nếu khách vẫn muốn nhận, vui lòng lập đơn mới tại quầy.");
                }
                throw new BusinessException("Đơn chưa được thanh toán.");
            }
            if (order.isOnline()) {
                String expected = order.getPickupCode();
                String given = presentedCode == null ? "" : presentedCode.trim().toUpperCase();
                if (expected == null || !expected.equals(given)) {
                    throw new PickupCodeMismatch(expected, given);
                }
                auditService.log(con, cashierId, "ORDER", orderId,
                        AuditAction.PICKUP_VERIFY_OK, null, expected);
            }

            int changed = orderDAO.markCompleted(con, orderId, cashierId, now);
            if (changed == 0) {
                throw new BusinessException("Đơn vừa được xử lý bởi người khác. Vui lòng tải lại.");
            }
            auditService.log(con, cashierId, "ORDER", orderId,
                    AuditAction.HANDOFF, OrderStatus.READY.name(), OrderStatus.COMPLETED.name());

            order.setOrderStatus(OrderStatus.COMPLETED.name());
            order.setCompletedAt(now);
            order.setPickedUpAt(now);
            return order;
        });
    }
}
