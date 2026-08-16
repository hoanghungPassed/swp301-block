package com.fastfood.service.customer;

import com.fastfood.common.constant.AuditAction;
import com.fastfood.common.constant.OrderItemStatus;
import com.fastfood.common.constant.OrderSource;
import com.fastfood.common.constant.OrderStatus;
import com.fastfood.common.exception.BusinessException;
import com.fastfood.common.exception.NotFoundException;
import com.fastfood.common.exception.ValidationException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.config.AppConfig;
import com.fastfood.dao.customer.CartDAO;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.OrderItemDAO;
import com.fastfood.dao.shared.ProductDAO;
import com.fastfood.dao.shared.UserDAO;
import com.fastfood.model.dto.Page;
import com.fastfood.model.entity.CartItem;
import com.fastfood.model.entity.Order;
import com.fastfood.model.entity.OrderItem;
import com.fastfood.model.entity.Product;
import com.fastfood.model.entity.User;
import com.fastfood.service.Tx;
import com.fastfood.service.shared.AuditService;
import com.fastfood.service.shared.NotificationService;
import com.fastfood.service.shared.OrderCoreService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Nghiệp vụ đơn hàng <b>phía khách hàng</b> — đặt trước, xem lại, tự huỷ.
 * <p>
 * Đơn đặt trước: khách chọn giờ nhận, trả tiền online, đơn nằm chờ tới sát giờ mới xuống bếp,
 * và khách nhận món bằng mã nhận hàng. Đường bán tại quầy nằm ở
 * {@code service.staff.StaffOrderService}, phần dùng chung của hai đường nằm ở
 * {@link OrderCoreService}.
 * <p>
 * Servlet gọi lớp này: {@code CartServlet} · {@code OrderHistoryServlet} ·
 * {@code OrderTrackingServlet} · {@code OrderStatusApiServlet}.
 */
public class CustomerOrderService {

    /**
     * Lời từ chối khi bếp đã bắt tay vào làm.
     * <p>
     * Dùng chung cho hai đường tới cùng một kết luận: đơn đã chuyển sang đang chế biến, và
     * đơn còn ở trạng thái đã xác nhận nhưng vừa có món được nhận. Người dùng chỉ thấy một
     * tình huống nên chỉ được nghe một câu trả lời, và câu đó phải chỉ ra việc tiếp theo cần
     * làm — báo "không huỷ được" rồi thôi thì khách chỉ còn cách gọi điện hỏi cho ra chuyện.
     */
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

    // ============================================================ đặt đơn

    /**
     * Tạo đơn đặt trước từ giỏ hàng của khách.
     * <p>
     * Ngay trước khi tạo đơn, từng món trong giỏ được đọc lại từ bảng món: giữa lúc khách bỏ
     * vào giỏ và lúc bấm đặt, quản trị viên có thể đã đổi giá hoặc đánh dấu hết hàng. Giá được
     * dùng là giá đọc lại này, và nó được sao chép vào đơn để hoá đơn không đổi về sau.
     * <p>
     * Đơn tạo ra ở trạng thái chờ thanh toán; chỉ khi tiền về đơn mới được xác nhận.
     */
    public Order createOnlineOrder(int customerId, LocalDateTime pickupTime, String idempotencyKey) {
        LocalDateTime now = DateTimeUtil.now();
        validatePickupTime(pickupTime, now);

        try {
            return doCreateOnlineOrder(customerId, pickupTime, idempotencyKey, now);
        } catch (RuntimeException e) {
            // Hai lần bấm gần như cùng lúc: cả hai cùng đọc thấy "chưa có đơn nào", cùng ghi,
            // và lần thứ hai đụng ràng buộc duy nhất trên khoá chống trùng. Đây chính là lúc
            // chống trùng phát huy tác dụng, nên trả về đơn mà lần thứ nhất vừa tạo
            // thay vì báo lỗi cho khách.
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

            // Khách bấm đặt hàng hai lần (bấm đúp, hoặc tải lại trang) chỉ tạo một đơn
            if (idempotencyKey != null) {
                Order existing = orderDAO.findByIdempotencyKey(con, idempotencyKey);
                if (existing != null) {
                    existing.setItems(orderItemDAO.findByOrder(con, existing.getOrderId()));
                    return existing;
                }
            }

            // Giỏ hàng chỉ được dọn khi tiền về, nên khách còn đang dở dang ở cổng thanh toán
            // thì giỏ vẫn nguyên hàng. Thiếu chốt này, khách bấm quay lại rồi đặt tiếp sẽ có
            // hai đơn cho cùng một lần mua.
            Order pending = orderDAO.findPendingByCustomer(con, customerId);
            if (pending != null) {
                // Nói rõ đường thoát. Chặn mà không chỉ chỗ đi tiếp thì khách tưởng mình bị kẹt
                // cho tới khi đơn cũ tự hết hạn — cả hai việc dưới đây đều làm ngay được ở
                // trang theo dõi đơn.
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
            // Giỏ hàng KHÔNG dọn ở đây mà đợi tới lúc tiền về — xem
            // OrderCoreService.confirmOnlineAfterPaid. Dọn sớm thì khách thanh toán hỏng hoặc
            // để quá hạn sẽ mất sạch giỏ và phải chọn lại từ đầu, trong khi họ chẳng làm gì sai.

            auditService.log(con, customerId, "ORDER", order.getOrderId(),
                    AuditAction.ORDER_CREATED, null, OrderStatus.PENDING_PAYMENT.name());
            return order;
        });
    }

    /**
     * Chặn đặt đơn online khi địa chỉ email chưa được xác thực.
     * <p>
     * Đặt ở tầng Service chứ không chỉ ẩn nút ngoài giao diện: nút bị ẩn vẫn gửi được yêu cầu
     * bằng tay, mà đây đúng là chốt chặn không được phép đi vòng. Giao diện chỉ có nhiệm vụ nói
     * trước lý do, xem dải nhắc ở {@code layout/page-start.jspf} và phần chọn giờ ở
     * {@code customer/cart.jsp}.
     * <p>
     * Đọc lại từ cơ sở dữ liệu chứ không tin bản chụp trong phiên: người dùng có thể vừa bấm
     * liên kết xác thực ở một máy khác, và bắt họ đăng nhập lại chỉ vì phiên bên này chưa kịp
     * biết là bắt sai người.
     * <p>
     * Chỉ áp cho đường đặt trước online. Bán tại quầy không đi qua đây — khách vãng lai đứng
     * ngay trước mặt thu ngân, không có email nào để xác thực và cũng chẳng cần.
     */
    private void requireVerifiedEmail(java.sql.Connection con, int customerId) throws java.sql.SQLException {
        User customer = userDAO.findById(con, customerId);
        if (customer == null) {
            throw new NotFoundException("Không tìm thấy tài khoản.");
        }
        if (!customer.isEmailVerified()) {
            // Câu này phải mang theo cả địa chỉ lẫn việc cần làm: khách gõ nhầm email lúc đăng
            // ký sẽ nhận ra ngay ở đây, và đó thường là lần đầu tiên họ nhìn thấy địa chỉ mình
            // đã gõ kể từ lúc đăng ký.
            throw new BusinessException("Bạn cần xác thực địa chỉ email " + customer.getEmail()
                    + " trước khi đặt đơn online. Mở thư chúng tôi đã gửi và bấm liên kết trong đó,"
                    + " hoặc bấm \"Gửi lại thư xác thực\" ở dải nhắc trên đầu trang.");
        }
    }

    /**
     * Kiểm tra giờ hẹn nhận hàng.
     * <p>
     * Ba điều kiện: cách hiện tại đủ xa để còn kịp thanh toán và chế biến, không quá 7 ngày,
     * và nằm trong giờ mở cửa. Điều kiện cuối không thừa — thiếu nó thì khách hẹn 3 giờ sáng
     * vẫn đặt được và bộ hẹn giờ vẫn đẩy đơn xuống bếp lúc 2 giờ 40 khi không có ai ở cửa hàng.
     * <p>
     * Giờ hẹn còn phải muộn hơn giờ mở cửa ít nhất bằng thời gian chuẩn bị, vì đơn được đưa
     * xuống bếp trước giờ hẹn đúng khoảng đó.
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

    /**
     * Giờ hẹn sớm nhất khách được chọn — dùng đặt giá trị nhỏ nhất cho ô nhập trên giao diện.
     * <p>
     * Nhảy sang khung mở cửa kế tiếp nếu thời điểm tính ra rơi ngoài giờ làm việc, để ô nhập
     * không gợi ý sẵn một giá trị mà bấm gửi lên là bị từ chối.
     */
    public LocalDateTime earliestPickupTime() {
        LocalDateTime candidate = DateTimeUtil.now().plusMinutes(AppConfig.pickupMinLeadMinutes());
        int openHour = AppConfig.storeOpenHour();
        int closeHour = AppConfig.storeCloseHour();
        LocalDateTime openThatDay = candidate.toLocalDate().atTime(openHour, 0)
                .plusMinutes(AppConfig.kitchenPrepLeadMinutes());

        if (candidate.isBefore(openThatDay)) {
            return openThatDay;                     // sáng sớm — đợi tới giờ mở cửa hôm nay
        }
        if (candidate.getHour() >= closeHour) {
            return openThatDay.plusDays(1);         // đã qua giờ đóng cửa — sang hôm sau
        }
        return candidate;
    }

    // ============================================================ đọc

    /**
     * Lấy đơn của chính khách đang đăng nhập.
     * Đơn của người khác trả về "không tìm thấy" chứ không phải "không có quyền", để không
     * tiết lộ rằng mã đơn đó có tồn tại.
     */
    public Order findForCustomer(int orderId, int customerId) {
        Order order = Tx.read(con -> orderCore.loadFull(con, orderId));
        if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
            throw new NotFoundException("Không tìm thấy đơn hàng.");
        }
        return order;
    }

    /**
     * Một trang lịch sử đơn của khách, mới nhất trước, có lọc theo trạng thái và ngày đặt.
     * <p>
     * Bộ lọc trả lời đúng hai câu hỏi mà khách quen mang tới trang này: "đơn hôm kia của tôi
     * hết bao nhiêu tiền" và "những đơn nào tôi từng bị huỷ". Không có nó thì cách duy nhất là
     * bấm lần lượt qua từng trang, và số đơn càng nhiều — tức là khách càng gắn bó — thì việc
     * đó càng lâu.
     * <p>
     * Khoảng ngày nhận vào là <b>ngày</b> chứ không phải mốc thời gian: khách nghĩ theo ngày,
     * và bắt họ gõ giờ phút chỉ để lọc lịch sử là thừa. Ngày kết thúc được mở rộng tới cuối
     * ngày ngay tại đây — thiếu bước đó thì chọn "đến hôm nay" lại bỏ sót trọn vẹn đơn đặt
     * hôm nay, vì mốc so sánh rơi vào lúc 0 giờ.
     *
     * @param status trạng thái đơn cần lọc; giá trị lạ hoặc rỗng đều hiểu là không lọc
     */
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

    /**
     * Lọc giá trị trạng thái đến từ địa chỉ trên thanh trình duyệt.
     * <p>
     * Một giá trị lạ được hiểu là "không lọc" chứ không phải một lỗi: tham số này chỉ có thể
     * sai khi ai đó sửa tay địa chỉ, và câu trả lời đúng cho việc đó là hiện toàn bộ lịch sử,
     * không phải một trang lỗi.
     */
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

    // ============================================================ huỷ đơn

    /**
     * Khách tự huỷ đơn. Nhận hai trạng thái, vì hai lý do khác nhau:
     * <ul>
     *   <li><b>Chờ thanh toán</b> — khách đổi ý trước khi trả tiền. Chưa thu đồng nào, bếp chưa
     *       thấy đơn, nên bỏ đi là chuyện hiển nhiên phải cho phép. Không cho thì khách vừa
     *       không trả tiền được vừa không đặt đơn khác được, phải ngồi đợi đơn tự hết hạn.</li>
     *   <li><b>Đã xác nhận</b> — đã trả tiền, và chốt chặn là <b>bếp đã bắt đầu làm hay chưa</b>,
     *       không phải "đơn đã xuống bếp hay chưa". Đơn xuống bếp trước giờ hẹn 20 phút và suốt
     *       khoảng đó có thể chưa ai nhận việc; huỷ lúc ấy chưa tốn nguyên liệu.</li>
     * </ul>
     * Dòng đơn được khoá trước khi đếm. Cơ sở dữ liệu bật chế độ đọc ảnh chụp, nên nếu không
     * khoá thì lệnh đếm ở đây và lệnh nhận việc của bếp có thể cùng nhìn thấy trạng thái cũ
     * của nhau: đơn bị huỷ trong khi bếp vừa bắc chảo lên. {@code KitchenService.claim} khoá
     * cùng dòng đơn này trước khi ghi, nên hai bên luôn xếp hàng chứ không chạy song song.
     * <p>
     * Khách huỷ đơn chờ thanh toán rồi cổng mới báo tiền về vẫn an toàn:
     * {@code confirmOnlineAfterPaid} thấy đơn không còn chờ thanh toán nên trả về false, và
     * {@code PaymentService.handleCallback} hoàn tiền ngay trong cùng giao dịch.
     */
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

            // Đơn chưa trả tiền do chính khách bỏ đi thì không gửi tin: họ vừa tự bấm và đang
            // nhìn màn hình, mà cũng không có khoản tiền nào để báo. Gửi chỉ thêm nhiễu.
            if (!unpaid) {
                notificationService.notifyOrderCancelled(con, order, "khách tự huỷ", refunded);
            }
            return refunded;
        });
    }

    /**
     * Vì sao đơn ở trạng thái này không huỷ được — nói theo ngôn ngữ của khách.
     * <p>
     * Trả về một câu chung chung là đẩy khách vào chỗ bí: họ không biết mình đang ở tình huống
     * nào và cũng không biết còn làm được gì. Mỗi trạng thái ở đây tương ứng với một chuyện
     * khác hẳn nhau đã xảy ra, và mỗi chuyện có một hướng xử lý riêng.
     */
    private static String cannotCancelReason(String status) {
        OrderStatus current = OrderStatus.valueOf(status);
        return switch (current) {
            // Bếp đang làm hoặc đã làm xong: tiền và nguyên liệu đều đã bỏ ra
            case PREPARING, READY -> KITCHEN_ALREADY_STARTED;
            case COMPLETED -> "Đơn này đã được giao cho khách nên không huỷ được nữa.";
            case CANCELLED -> "Đơn này đã được huỷ trước đó rồi.";
            case EXPIRED -> "Đơn này đã hết hiệu lực vì quá hạn thanh toán. Vui lòng đặt lại.";
            default -> "Đơn này không còn ở trạng thái huỷ được.";
        };
    }
}
