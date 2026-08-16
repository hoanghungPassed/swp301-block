package com.fastfood.service.staff;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.BusinessRule;
import com.fastfood.common.constant.OrderItemStatus;
import com.fastfood.common.constant.OrderSource;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.constant.PaymentMethod;
import com.fastfood.common.constant.PaymentStatus;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.TransactionDAO;
import com.fastfood.dao.staff.ShiftDAO;
import com.fastfood.model.dto.Page;
import com.fastfood.model.dto.PosCartLine;
import com.fastfood.model.dto.PosLine;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.model.entity.Payment;
import com.fastfood.model.entity.Product;
import com.fastfood.model.entity.Shift;
import com.fastfood.model.entity.Transaction;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.NotificationService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Nghiệp vụ đơn hàng <b>phía thu ngân</b> — bán tại quầy, điều phối đơn, nhận món từ bếp,
 * giao món cho khách.
 * <p>
 * Bán tại quầy khác đặt trước ở chỗ tiền thu ngay tại chỗ, nên đặt hàng - thanh toán - xác nhận
 * - đưa xuống bếp diễn ra trong cùng một giao dịch; không có giờ hẹn và không có mã nhận hàng.
 * Đường đặt trước nằm ở {@code service.customer.CustomerOrderService}, phần dùng chung của hai
 * đường nằm ở {@link OrderCoreService}.
 * <p>
 * Servlet gọi lớp này: {@code PosServlet} · {@code OrderDashboardServlet} ·
 * {@code OrderDetailServlet} · {@code CounterServlet} · {@code StaffHistoryServlet}.
 */
public class StaffOrderService {

    /**
     * Tên "cổng thanh toán" ghi vào nhật ký đối soát cho khoản thu bằng thẻ hoặc mã QR tại quầy.
     * Không phải cổng trực tuyến: tiền chạy qua máy thanh toán đặt ở quầy, hệ thống chỉ lưu
     * lại mã giao dịch in trên biên lai để sau này đối chiếu với sao kê.
     */
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

    // ============================================================ bán tại quầy

    /**
     * Thu ngân lập đơn cho khách tại quầy.
     * <p>
     * Toàn bộ đặt hàng - thanh toán - xác nhận - đưa xuống bếp diễn ra trong cùng một giao dịch.
     * Khách đứng đợi nên không có giờ hẹn và không cần mã nhận hàng.
     * <p>
     * Hai hình thức thu tiền được xác nhận theo hai cách khác nhau:
     * <ul>
     *   <li><b>Tiền mặt</b> — thu ngân đếm tiền, không có gì để đối chiếu về sau ngoài chính
     *       bản ghi này.</li>
     *   <li><b>Thẻ hoặc mã QR</b> — tiền chạy qua máy thanh toán ở quầy chứ không qua hệ thống,
     *       nên bản ghi "đã thu" ở đây chỉ là lời khai của thu ngân. Vì vậy bắt buộc phải nhập
     *       mã giao dịch in trên biên lai, và mã đó được ghi vào nhật ký đối soát. Ràng buộc
     *       duy nhất trên mã khiến một biên lai không thể dùng cho hai đơn — chặn đúng tình
     *       huống thu ngân lỡ tay lập lại đơn cho một lần quẹt thẻ.</li>
     * </ul>
     *
     * @param terminalReference mã giao dịch trên biên lai máy thanh toán; bỏ trống khi trả tiền mặt
     */
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
            order.setCustomerId(null);              // khách vãng lai, không cần tài khoản
            order.setCreatedByUserId(cashierId);
            // Gắn đơn vào ca đang mở, nếu có. Chưa mở ca vẫn bán được — chặn lại chỉ làm thu
            // ngân kẹt giữa giờ cao điểm vì một thủ tục. Đơn không thuộc ca nào thì nằm ngoài
            // bảng đối soát cuối ca, và màn hình quầy có cảnh báo để không ai bỏ sót chuyện đó.
            Shift openShift = shiftDAO.findOpenOf(con, cashierId);
            order.setShiftId(openShift == null ? null : openShift.getShiftId());
            order.setOrderSource(OrderSource.POS.name());
            order.setOrderStatus(OrderStatus.CONFIRMED.name());
            order.setCreatedAt(now);
            order.setReleasedToKdsAt(now);          // bếp thấy đơn ngay lập tức
            order.setTotalAmount(BigDecimal.ZERO);
            orderDAO.insert(con, order);

            BigDecimal total = BigDecimal.ZERO;
            for (PosLine line : lines) {
                // Phiếu tạm nằm trong phiên của thu ngân nhưng số lượng vẫn do trình duyệt gửi lên,
                // nên vẫn phải kiểm ở đây chứ không tin vào giới hạn của ô nhập. Cùng ngưỡng với
                // giỏ hàng của khách — xem BusinessRule.MAX_QUANTITY_PER_LINE.
                if (line.getQuantity() <= 0 || line.getQuantity() > BusinessRule.MAX_QUANTITY_PER_LINE) {
                    throw new ValidationException("Số lượng mỗi món phải từ 1 đến "
                            + BusinessRule.MAX_QUANTITY_PER_LINE + ".");
                }
                Product product = productDAO.findForCheckout(con, line.getProductId());
                // Gọi tên món ra. Câu "món đã chọn hiện không còn phục vụ" đúng nhưng vô dụng:
                // phiếu mười dòng thì thu ngân phải thử bỏ từng dòng để tìm ra thủ phạm, trong
                // lúc khách đứng đợi ở quầy.
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

            // Khoản thu qua máy thanh toán phải để lại dấu vết đối soát. Ghi mã biên lai vào
            // cùng bảng với giao dịch của cổng trực tuyến, nên báo cáo đối soát chỉ cần đọc
            // một nơi, và ràng buộc duy nhất bảo vệ cả hai đường thu tiền như nhau.
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

    /**
     * Dựng phiếu tính tiền để hiển thị, từ giỏ tạm nằm trong phiên làm việc của thu ngân.
     * <p>
     * Đọc qua đúng câu truy vấn mà {@link #createPosOrder} dùng lúc thu tiền
     * ({@code findForCheckout}, có tính cả nhóm món đã tắt), nên những gì màn hình nói được về
     * một món là đúng thứ sẽ xảy ra khi bấm nút — không còn cảnh phiếu hiện bình thường rồi
     * thanh toán mới báo món không phục vụ nữa.
     * <p>
     * Mỗi dòng một lượt truy vấn. Giỏ của một khách đứng ở quầy chỉ vài dòng nên chấp nhận được,
     * và đổi lại là bỏ được lượt nạp toàn bộ thực đơn mà bản trước phải làm chỉ để tra tên món.
     */
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

    // ============================================================ đọc

    /** Đơn kèm món và lần thanh toán gần nhất — màn hình chi tiết đơn của thu ngân. */
    public Order findById(int orderId) {
        return orderCore.findById(orderId);
    }

    /** Bốn tab trên màn hình đơn hàng của thu ngân. */
    public List<Order> dashboard(String tab) {
        return Tx.read(con -> orderDAO.findForDashboard(con, tab, DateTimeUtil.now(),
                AppConfig.pickupOverdueMinutes()));
    }

    /** Một trang đơn khớp bộ lọc, dùng cho màn hình lịch sử của thu ngân. */
    public Page<Order> search(String source, String status,
                              LocalDateTime from, LocalDateTime to, int pageNo) {
        int page = Page.safePage(pageNo);
        int offset = Page.offset(page, Page.SIZE);
        return Tx.read(con -> new Page<>(
                orderDAO.search(con, source, status, from, to, offset, Page.SIZE),
                page, Page.SIZE,
                orderDAO.countSearch(con, source, status, from, to)));
    }

    /** Tra đơn theo mã nhận hàng khi khách tới quầy. */
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

    // ============================================================ huỷ đơn

    /**
     * Thu ngân đóng đơn.
     * <p>
     * Khác đường huỷ của khách ở chỗ đóng được cả đơn đang nấu dở và đơn đã sẵn sàng mà
     * khách không tới lấy. Đây là lối thoát duy nhất cho ba tình huống trước đây không có
     * cách xử lý: khách không đến, bếp báo hết nguyên liệu, và khách gọi điện xin huỷ muộn.
     * <p>
     * Lý do huỷ bắt buộc phải có và được ghi vào nhật ký thao tác — đây là thao tác duy nhất
     * làm mất doanh thu đã ghi nhận, nên phải truy được ai quyết định và vì sao.
     */
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

    // ============================================================ quầy nhận món từ bếp

    /** Món bếp đã đưa ra quầy mà chưa ai xác nhận cầm. */
    public List<OrderItem> awaitingCounter() {
        return Tx.read(orderItemDAO::findAwaitingCounter);
    }

    /** Số món đang nằm chờ trên quầy — hiện thành cảnh báo trên màn hình điều phối. */
    public int countAwaitingCounter() {
        return Tx.read(orderItemDAO::countAwaitingCounter);
    }

    /**
     * Đơn chờ khách tới lấy, kèm danh sách món, để màn hình quầy nói được đơn nào còn thiếu món.
     * <p>
     * {@link #dashboard} không nạp món vì bốn tab của nó chỉ hiện dòng tóm tắt. Ở đây thì cần,
     * nên phải có phương thức riêng — mỗi đơn một lượt truy vấn món. Danh sách này bị chặn bởi
     * số đơn thật sự đang nằm chờ ở một quầy, tức là vài đơn, nên chấp nhận được; nếu về sau
     * nó dài ra thì đây là chỗ cần gộp thành một câu truy vấn.
     */
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

    /**
     * Thu ngân xác nhận đã cầm món tại quầy.
     * <p>
     * Không đổi trạng thái đơn: đây là bàn giao nội bộ giữa hai vị trí trong cửa hàng, khách
     * chưa nhận được gì. Trạng thái đơn chỉ đổi khi món thật sự ra khỏi cửa hàng — xem
     * {@link #handoff}.
     */
    public void receiveAtCounter(int orderItemId, int cashierId) {
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            OrderItem item = orderItemDAO.findById(con, orderItemId);
            if (item == null) {
                throw new NotFoundException("Không tìm thấy món.");
            }
            int changed = orderItemDAO.receiveAtCounter(con, orderItemId, cashierId, now);
            if (changed == 0) {
                // Đọc lại để nói đúng lý do thay vì một câu chung chung khiến thu ngân
                // bấm đi bấm lại một nút không bao giờ ăn.
                if (item.isReceived()) {
                    throw new BusinessException("Món này đã được nhận rồi.");
                }
                throw new BusinessException("Bếp chưa bàn giao món này ra quầy.");
            }
            auditService.log(con, cashierId, "ORDER_ITEM", orderItemId,
                    AuditAction.ITEM_RECEIVED, "AT_COUNTER", "RECEIVED");
        });
    }

    // ============================================================ giao món cho khách

    /**
     * Giao món cho khách.
     * <p>
     * Bốn điều kiện phải cùng đúng: món đã sẵn sàng, quầy đã nhận đủ món từ bếp, tiền đã thu,
     * và với đơn đặt trước thì mã khách đưa phải khớp. Kiểm tra ở phía máy chủ chứ không dựa
     * vào giao diện, vì đây là bước quyết định món ra khỏi cửa hàng.
     * <p>
     * Lần đưa sai mã được ghi lại ở <b>giao dịch riêng</b>. Ghi chung với giao dịch chính thì
     * bản ghi bị huỷ cùng lúc với thao tác bị từ chối, và chuyện đáng theo dõi nhất — ai đó
     * đứng ở quầy thử hết mã này tới mã khác — lại là chuyện duy nhất không để lại dấu vết.
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

    /** Mã khách đưa không khớp. Chỉ dùng trong nội bộ lớp này để thoát khỏi giao dịch. */
    private static final class PickupCodeMismatch extends RuntimeException {
        private final transient String expected;
        private final transient String given;

        PickupCodeMismatch(String expected, String given) {
            super(null, null, false, false);   // không cần vết ngăn xếp, đây là luồng nghiệp vụ
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
            // Đơn sẵn sàng chỉ nghĩa là bếp đã nấu xong. Món có thể vẫn còn trong bếp, và giao
            // cho khách trước khi quầy cầm được món là cách chắc chắn nhất để phát thiếu món.
            int notReceived = orderItemDAO.countNotReceived(con, orderId);
            if (notReceived > 0) {
                throw new BusinessException("Còn " + notReceived + " món chưa được nhận tại quầy. "
                        + "Vào màn hình Quầy giao nhận để nhận món từ bếp trước khi giao cho khách.");
            }
            Payment paid = paymentDAO.findPaidByOrder(con, orderId);
            if (paid == null) {
                // Phân biệt hai chuyện khác hẳn nhau: chưa từng thu được tiền, và đã thu rồi
                // hoàn lại. Gộp làm một thì nhân viên tưởng cứ thu tiền là giao được, trong khi
                // đơn đã hoàn tiền phải lập lại từ đầu.
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
                    // KHÔNG ghi nhật ký ở đây: câu lệnh tiếp theo huỷ cả giao dịch, và bản ghi
                    // nhật ký sẽ bị huỷ theo. Ném ra ngoài rồi ghi ở giao dịch riêng — xem
                    // khối catch bên trên.
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
