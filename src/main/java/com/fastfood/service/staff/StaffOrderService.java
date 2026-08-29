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
import com.fastfood.model.dto.Dtos.KdsOrderView;
import com.fastfood.model.dto.Dtos.Page;
import com.fastfood.model.dto.Dtos.PosCartLine;
import com.fastfood.model.dto.Dtos.PosLine;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.OrderItem;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.MenuEntities.Product;
import com.fastfood.model.entity.OrderEntities.Transaction;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StaffOrderService {

    private static final String POS_TERMINAL = "POS_TERMINAL";

    /* Mã đối soát của lần thu ngân tự xác nhận tiền QR. Gắn theo mã khoản thu nên mỗi
       khoản chỉ ghi được một lần — ràng buộc duy nhất trên bảng giao dịch chặn lần thứ hai. */
    private static final String POS_QR_REFERENCE = "POS-QR-";

    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderItemDAO orderItemDAO = new OrderItemDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final AuditService auditService = new AuditService();
    private final OrderCoreService orderCore = new OrderCoreService();

    /**
     * Đơn tại quầy trả bằng tiền mặt: lập đơn, ghi nhận tiền và đẩy xuống bếp trong một nhịp.
     *
     * <p>Quầy chỉ còn hai đường thu tiền — tiền mặt ở đây và mã QR ở {@link #createPosQrOrder}.
     * Tiền mặt là đường duy nhất mà tiền chắc chắn đã nằm trong két ngay lúc bấm, nên đơn được
     * ghi PAID và mở đường xuống bếp cùng lúc.
     */
    public Order createPosOrder(int cashierId, List<PosLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Chưa chọn món nào.");
        }
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            Order order = insertOrderWithItems(con, cashierId, lines, now, true);
            Payment payment = insertPayment(con, order, PaymentMethod.CASH, PaymentStatus.PAID, now);

            auditService.log(con, cashierId, "ORDER", order.getOrderId(),
                    AuditAction.ORDER_CREATED, null, OrderStatus.CONFIRMED.name());
            auditService.log(con, cashierId, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_PAID, null, PaymentMethod.CASH.name());
            auditService.log(con, cashierId, "ORDER", order.getOrderId(),
                    AuditAction.KDS_RELEASE, null, "RELEASED");
            return order;
        });
    }

    /**
     * Đơn tại quầy trả bằng mã QR: lập đơn và khoản thu ở trạng thái CHỜ, chưa đẩy xuống bếp.
     *
     * <p>Khác {@link #createPosOrder} ở đúng một chỗ: lúc này tiền chưa về. Đơn vẫn phải nằm
     * trong cơ sở dữ liệu trước khi sinh mã QR, vì mã QR trỏ tới một khoản thu cụ thể của cổng
     * thanh toán — chưa có khoản thu thì chưa có gì để ký. Bù lại {@code released_to_kds_at}
     * để trống nên bếp chưa thấy đơn: chỉ khi thu ngân bấm Xong, {@link #confirmQrPayment} mới
     * mở đường xuống bếp. Nhờ vậy khách bỏ đi giữa chừng cũng không ai làm món cho một đơn
     * chưa trả tiền.
     */
    public Order createPosQrOrder(int cashierId, List<PosLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Chưa chọn món nào.");
        }
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            Order order = insertOrderWithItems(con, cashierId, lines, now, false);
            Payment payment = insertPayment(con, order, PaymentMethod.ONLINE_GATEWAY,
                    PaymentStatus.PENDING, now);

            auditService.log(con, cashierId, "ORDER", order.getOrderId(),
                    AuditAction.ORDER_CREATED, null, OrderStatus.CONFIRMED.name());
            auditService.log(con, cashierId, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_INITIATED, null, PaymentStatus.PENDING.name());
            return order;
        });
    }

    /**
     * Thu ngân xác nhận khách đã trả xong: ghi nhận tiền rồi đưa đơn xuống bếp.
     *
     * <p>Có hai đường tiền về và cả hai đều dừng ở đây. Nếu cổng đã báo về (webhook, hoặc lượt
     * khách quay lại trình duyệt) thì khoản thu đã PAID sẵn và việc còn lại chỉ là mở đường
     * xuống bếp. Nếu chưa thấy báo về — thường vì máy chủ chạy trên máy cá nhân, không có địa
     * chỉ công khai cho webhook, mà khách thì trả trên điện thoại của họ — thì thu ngân nhìn
     * màn hình báo tiền về của khách rồi xác nhận bằng tay, đúng mức tin cậy đang dành cho
     * tiền mặt.
     * Lần xác nhận tay ấy để lại một giao dịch POS_TERMINAL để sau này đối soát còn phân biệt
     * được với khoản do cổng tự báo.
     */
    public Order confirmQrPayment(int orderId, int cashierId) {
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            orderCore.lockOrder(con, orderId);
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (order.isOnline()) {
                throw new BusinessException("Đơn đặt trước không xác nhận tiền ở màn hình bán tại quầy.");
            }
            if (!OrderStatus.CONFIRMED.name().equals(order.getOrderStatus())) {
                throw new BusinessException("Đơn #" + orderId + " đang ở trạng thái "
                        + order.getOrderStatus() + " nên không ghi nhận thanh toán được nữa.");
            }

            Payment payment = paymentDAO.findLatestByOrder(con, orderId);
            if (payment == null) {
                throw new NotFoundException("Đơn này chưa có khoản thu nào để xác nhận.");
            }

            if (!payment.isPaid()) {
                if (!payment.isPending()) {
                    throw new BusinessException("Khoản thu của đơn đang ở trạng thái "
                            + payment.getPaymentStatus() + " nên không xác nhận được. "
                            + "Hãy lập lại phiếu cho khách.");
                }
                if (paymentDAO.markPaid(con, payment.getPaymentId(), now) == 0) {
                    throw new BusinessException("Khoản thu vừa được ghi nhận ở nơi khác. "
                            + "Vui lòng tải lại trang.");
                }
                Transaction txn = transactionDAO.newTransaction(payment.getPaymentId(), POS_TERMINAL,
                        POS_QR_REFERENCE + payment.getPaymentId(), "SUCCESS",
                        "Thu ngân xác nhận khách đã quét mã QR trả tiền.", now);
                transactionDAO.insertIfNew(con, txn);
                auditService.log(con, cashierId, "PAYMENT", payment.getPaymentId(),
                        AuditAction.PAYMENT_PAID, PaymentStatus.PENDING.name(),
                        PaymentStatus.PAID.name());
            }

            if (orderDAO.markReleasedToKds(con, orderId, now) > 0) {
                auditService.log(con, cashierId, "ORDER", orderId,
                        AuditAction.KDS_RELEASE, null, "RELEASED");
                order.setReleasedToKdsAt(now);
            }
            order.setItems(orderItemDAO.findByOrder(con, orderId));
            order.setLatestPayment(paymentDAO.findLatestByOrder(con, orderId));
            return order;
        });
    }

    /**
     * Chèn order POS và chụp snapshot tên/giá từng món; đồng thời chặn số lượng sai hoặc món
     * đã ngừng bán trước khi cập nhật tổng tiền.
     */
    private Order insertOrderWithItems(Connection con, int cashierId, List<PosLine> lines,
                                       LocalDateTime now, boolean releaseToKitchen) throws SQLException {
        Order order = new Order();
        order.setCustomerId(null);
        order.setCreatedByUserId(cashierId);
        order.setOrderSource(OrderSource.POS.name());
        order.setOrderStatus(OrderStatus.CONFIRMED.name());
        order.setCreatedAt(now);
        if (releaseToKitchen) {
            order.setReleasedToKdsAt(now);
        }
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
        return order;
    }

    /** Tạo bản ghi Payment đầu tiên của đơn POS và gắn lại vào Order để trả về giao diện. */
    private Payment insertPayment(Connection con, Order order, PaymentMethod method,
                                  PaymentStatus status, LocalDateTime now) throws SQLException {
        Payment payment = new Payment();
        payment.setOrderId(order.getOrderId());
        payment.setMethod(method.name());
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentStatus(status.name());
        payment.setAttemptNo(1);
        payment.setCreatedAt(now);
        if (status == PaymentStatus.PAID) {
            payment.setPaidAt(now);
        }
        paymentDAO.insert(con, payment);
        order.setLatestPayment(payment);
        return payment;
    }

    /** Đọc lại sản phẩm từ DB để tính giá hiện tại và đánh dấu dòng giỏ không còn orderable. */
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

    /** Lấy đầy đủ một đơn qua OrderCoreService, gồm items và payment mới nhất. */
    public Order findById(int orderId) {
        return orderCore.findById(orderId);
    }

    /** Lấy danh sách dashboard theo tab POS/SCHEDULED/READY/OVERDUE và mốc quá hạn cấu hình. */
    public List<Order> dashboard(String tab) {
        return Tx.read(con -> orderDAO.findForDashboard(con, tab, DateTimeUtil.now(),
                AppConfig.pickupOverdueMinutes()));
    }

    /** Tìm lịch sử theo nguồn, trạng thái và khoảng ngày; DAO đếm tổng để dựng phân trang. */
    public Page<Order> search(String source, String status,
                              LocalDateTime from, LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                orderDAO.search(con, source, status, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                orderDAO.countSearch(con, source, status, from, to)));
    }

    /** Chuẩn hoá mã nhận hàng, tìm đúng đơn và nạp items/payment phục vụ bước giao khách. */
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

    /** Lấy các món bếp đã bàn giao nhưng quầy chưa xác nhận nhận. */
    public List<OrderItem> awaitingCounter() {
        return Tx.read(orderItemDAO::findAwaitingCounter);
    }

    /** Đếm món đang chờ quầy nhận để hiển thị badge cảnh báo trên dashboard. */
    public int countAwaitingCounter() {
        return Tx.read(orderItemDAO::countAwaitingCounter);
    }

    /** Lấy các đơn READY và nạp items để quầy kiểm tra trước khi giao khách. */
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

    /** Món trên quầy gom theo đơn, khớp với cách bếp bàn giao cả đơn. */
    public List<KdsOrderView> awaitingCounterOrders() {
        return KdsOrderView.group(Tx.read(orderItemDAO::findAwaitingCounterOrders));
    }

    /** Nhận trọn phần bếp vừa đưa ra của một đơn. */
    public void receiveOrder(int orderId, int cashierId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            List<OrderItem> items = orderItemDAO.findByOrder(con, orderId);
            if (items.isEmpty()) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            int received = 0;
            for (OrderItem item : items) {
                if (item.getHandedOverAt() == null || item.isReceived()) {
                    continue;
                }
                if (orderItemDAO.receiveAtCounter(con, item.getOrderItemId(), cashierId, now) == 1) {
                    auditService.log(con, cashierId, "ORDER_ITEM", item.getOrderItemId(),
                            AuditAction.ITEM_RECEIVED, "AT_COUNTER", "RECEIVED");
                    received++;
                }
            }
            if (received == 0) {
                throw new BusinessException("Đơn này không còn món nào chờ nhận trên quầy.");
            }
        });
    }

    /** Nhận một món tại quầy theo phép cập nhật có điều kiện và ghi audit người thực hiện. */
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

    /**
     * Bao ngoài giao dịch giao hàng để trường hợp nhập sai pickup code vẫn được ghi audit sau
     * khi giao dịch nghiệp vụ bị rollback.
     */
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

    /**
     * Chỉ hoàn tất khi đơn READY, mọi món đã nhận tại quầy, payment PAID và mã online khớp;
     * UPDATE có điều kiện ngăn hai thu ngân cùng giao một đơn.
     */
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
