package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.*;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.util.DateTimeUtil;
import com.fastfood.dao.JdbcSupport;
import com.fastfood.dao.shared.OrderDAO;
import com.fastfood.dao.shared.PaymentDAO;
import com.fastfood.dao.shared.TransactionDAO;
import com.fastfood.integration.payment.GatewayCallback;
import com.fastfood.integration.payment.PaymentGateway;
import com.fastfood.integration.payment.PaymentGateways;
import com.fastfood.integration.payment.PaymentInitResult;
import com.fastfood.model.entity.OrderEntities.Order;
import com.fastfood.model.entity.OrderEntities.Payment;
import com.fastfood.model.entity.OrderEntities.Transaction;
import com.fastfood.service.Tx;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

public class PaymentService {

    public enum CallbackResult {
        PAID,
        FAILED,
        DUPLICATE,
        ORDER_GONE,
        AMOUNT_MISMATCH
    }

    private static final Logger LOG = Logger.getLogger(PaymentService.class.getName());

    private static final int ATTEMPT_NO_RETRIES = 3;

    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final OrderCoreService orderCore = new OrderCoreService();
    private final AuditService auditService = new AuditService();
    private final NotificationService notificationService = new NotificationService();
    private final PaymentGateway gateway;

    public PaymentService() {
        this(PaymentGateways.fromConfig());
    }

    /**
     * Chỗ nối để bài kiểm tra đưa vào một cổng khác cổng đang cấu hình.
     *
     * <p>Có mặt vì PayOS: mở cổng ở đó là một lời gọi HTTP ra Internet, nên nếu không thay được
     * đường truyền thì mọi bài kiểm tra chạm tới thanh toán đều đòi mạng và đòi khoá thật —
     * tức là trên thực tế không ai chạy chúng. Đưa vào một {@code PayOsGateway} dựng trên một
     * {@code PayOsApi} giả thì phần ký, phần dựng mã đơn và phần đọc kết quả vẫn là mã thật.
     */
    public PaymentService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String startOnlinePayment(int orderId, int customerId, String baseUrl) {
        LocalDateTime now = DateTimeUtil.now();

        Payment created = Tx.write(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
                throw new BusinessException("Đơn hàng này không còn ở trạng thái chờ thanh toán.");
            }

            Payment payment = new Payment();
            payment.setOrderId(orderId);
            payment.setMethod(PaymentMethod.ONLINE_GATEWAY.name());
            payment.setAmount(order.getTotalAmount());
            payment.setPaymentStatus(PaymentStatus.PENDING.name());
            payment.setCreatedAt(now);

            for (int attempt = 1; ; attempt++) {
                payment.setAttemptNo(paymentDAO.nextAttemptNo(con, orderId));
                try {
                    paymentDAO.insert(con, payment);
                    break;
                } catch (SQLException e) {
                    if (!JdbcSupport.isUniqueViolation(e) || attempt == ATTEMPT_NO_RETRIES) {
                        throw e;
                    }
                }
            }

            auditService.log(con, customerId, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_INITIATED, null, PaymentStatus.PENDING.name());
            return payment;
        });

        /* Gọi cổng NGOÀI giao dịch, sau khi khoản thu đã được ghi hẳn.

           Với một cổng tính địa chỉ tại chỗ thì để lời gọi nằm trong giao dịch cũng không sao,
           nhưng PayOS thì mở cổng là một lời gọi HTTP ra Internet — dài tới mươi giây nếu bên
           kia chậm. Giữ giao dịch mở suốt quãng đó là giữ luôn một kết nối trong bể dùng chung;
           vài chục khách bấm thanh toán cùng lúc lúc cổng đang ì là bể cạn, và thứ hỏng không
           phải trang thanh toán mà là cả ứng dụng.

           Đổi lại: PayOS hỏng thì còn một khoản thu PENDING không có liên kết nào. Đây là cái
           giá rẻ hơn — khách bấm lại sẽ có một lần thanh toán mới (attempt_no kế tiếp), còn
           khoản dở dang thì bộ hẹn giờ quá hạn đóng lại như mọi lần khách bỏ ngang. */
        return gateway.initiate(created.getPaymentId(), orderId, created.getAmount(), baseUrl)
                .getRedirectUrl();
    }

    public CallbackResult handleCallback(GatewayCallback callback) {
        /* Bỏ qua bước kiểm chữ ký khi dữ liệu do chính hệ thống đi hỏi cổng mà có — xem
           GatewayCallback.isTrusted(). Không phải một lối tắt cho tiện: cổng như PayOS không
           ký các tham số trên địa chỉ khách quay về, nên ở đó chữ ký không tồn tại để mà kiểm,
           và thứ thay thế nó là lời gọi HTTPS có khoá API tới đúng máy chủ của cổng. */
        if (!callback.isTrusted() && !gateway.verifySignature(callback)) {
            LOG.warning("Bo qua ket qua thanh toan co chu ky khong hop le, paymentId="
                    + callback.getPaymentId());
            throw new BusinessException("Dữ liệu thanh toán không hợp lệ.");
        }

        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
            Payment payment = paymentDAO.findById(con, callback.getPaymentId());
            if (payment == null) {
                throw new NotFoundException("Không tìm thấy giao dịch thanh toán.");
            }

            BigDecimal received = callback.getAmount();
            boolean amountMatches = received != null && received.compareTo(payment.getAmount()) == 0;

            Transaction txn = transactionDAO.newTransaction(
                    payment.getPaymentId(), gateway.getName(), callback.getExternalTransactionId(),
                    transactionStatus(callback.isSuccess(), amountMatches),
                    callback.getRawPayload(), now);

            boolean isNew = transactionDAO.insertIfNew(con, txn);
            if (!isNew) {
                auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                        AuditAction.CALLBACK_IGNORED, callback.getExternalTransactionId());
                LOG.info("Bo qua callback trung lap: " + callback.getExternalTransactionId());
                return CallbackResult.DUPLICATE;
            }

            if (!callback.isSuccess()) {
                paymentDAO.markFailed(con, payment.getPaymentId());
                auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                        AuditAction.PAYMENT_FAILED, PaymentStatus.FAILED.name());
                return CallbackResult.FAILED;
            }

            if (!amountMatches) {
                String moTa = "cho " + payment.getAmount().toPlainString()
                        + ", nhan " + (received == null ? "khong ro" : received.toPlainString());
                auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                        AuditAction.PAYMENT_FAILED, "AMOUNT_MISMATCH: " + moTa);
                LOG.severe("So tien khong khop cho paymentId=" + payment.getPaymentId() + ": " + moTa
                        + ", ma giao dich " + callback.getExternalTransactionId()
                        + ". Can doi soat thu cong.");
                return CallbackResult.AMOUNT_MISMATCH;
            }

            Order order = orderDAO.findById(con, payment.getOrderId());

            int paidRows = paymentDAO.markPaid(con, payment.getPaymentId(), now);
            if (paidRows == 0) {
                /* Không ghi được PAID vì khoản thu đã rời khỏi PENDING/UNPAID. Hai chuyện rất
                   khác nhau cùng rơi vào đây, và gộp chúng lại thì chuyện thứ hai đi vào im lặng:

                     · đã PAID  — cổng gọi lại lần nữa cho khoản đã ghi nhận, bỏ qua là đúng
                     · đã FAILED — bộ hẹn giờ đã đóng khoản này vì quá hạn, rồi tiền mới về

                   Vế sau là tiền thật đã bị ngân hàng trừ của khách. */
                if (!payment.isPaid()) {
                    paymentDAO.markPaidLate(con, payment.getPaymentId(), now);
                    auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                            AuditAction.PAYMENT_PAID, PaymentStatus.PAID.name());
                    return orphan(con, order, payment,
                            "khoan thu da bi dong luc " + payment.getPaymentStatus()
                            + " truoc khi tien ve");
                }
                return CallbackResult.DUPLICATE;
            }
            auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_PAID, PaymentStatus.PAID.name());

            /* Đơn còn chỗ cho khoản tiền này không? Đơn đặt trước thì câu trả lời nằm ở lần xác
               nhận: hết hiệu lực rồi thì không xác nhận được nữa. Đơn tại quầy đã là CONFIRMED
               ngay từ lúc lập, nên chỗ để hỏi là trạng thái hiện thời — bộ hẹn giờ có thể vừa
               đóng đơn vì quá 15 phút không ai trả tiền, đúng lúc khách bấm trả trên điện thoại. */
            boolean orderGone = order != null && (order.isOnline()
                    ? !orderCore.confirmOnlineAfterPaid(con, order, now)
                    : !OrderStatus.CONFIRMED.name().equals(order.getOrderStatus()));
            if (orderGone) {
                return orphan(con, order, payment, "don da o trang thai " + order.getOrderStatus());
            }
            return CallbackResult.PAID;
        });
    }

    /* Tiền về mà không còn đơn nào nhận nó. Khoản thu giữ nguyên trạng thái hiện có thay vì bị
       kéo về FAILED: ngân hàng đã trừ tiền thật, và ghi ngược lại ở đây sẽ làm sổ sách lệch với
       sao kê. Hệ thống không có đường hoàn tiền tự động, nên việc còn lại là để dấu vết thật rõ
       — một dòng nhật ký tra được bằng PAYMENT_ORPHANED, một tin cho khách, và một cảnh báo mức
       SEVERE trong log máy chủ để người trực đối soát nhìn thấy ngay trong ngày. */
    private CallbackResult orphan(Connection con, Order order, Payment payment, String lyDo)
            throws SQLException {
        int orderId = order == null ? 0 : order.getOrderId();
        auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                AuditAction.PAYMENT_ORPHANED, "don #" + orderId + ": " + lyDo);
        if (order != null) {
            notificationService.notifyPaymentOrphaned(con, order);
        }
        LOG.severe("Tien ve nhung don #" + orderId + " khong con nhan duoc (" + lyDo
                + "). Khoan thu paymentId=" + payment.getPaymentId()
                + " can doi soat va hoan thu cong qua cong thanh toan.");
        return CallbackResult.ORDER_GONE;
    }

    private static String transactionStatus(boolean success, boolean amountMatches) {
        if (!success) {
            return "FAILED";
        }
        return amountMatches ? "SUCCESS" : "MISMATCH";
    }

    public int orderIdOfPayment(int paymentId) {
        return Tx.read(con -> {
            Payment p = paymentDAO.findById(con, paymentId);
            return p == null ? 0 : p.getOrderId();
        });
    }

    public List<Payment> findByOrder(int orderId) {
        return Tx.read(con -> paymentDAO.findByOrder(con, orderId));
    }

    public List<Transaction> findTransactions(int paymentId) {
        return Tx.read(con -> transactionDAO.findByPayment(con, paymentId));
    }

    public Payment findForCustomer(int paymentId, int customerId) {
        return Tx.read(con -> {
            Payment payment = paymentDAO.findById(con, paymentId);
            if (payment == null) {
                throw new NotFoundException("Không tìm thấy giao dịch thanh toán.");
            }
            Order order = orderDAO.findById(con, payment.getOrderId());
            if (order == null || order.getCustomerId() == null || order.getCustomerId() != customerId) {
                throw new NotFoundException("Không tìm thấy giao dịch thanh toán.");
            }
            return payment;
        });
    }

    /**
     * Địa chỉ trả tiền của cổng cho một khoản thu ĐÃ có sẵn trong cơ sở dữ liệu.
     *
     * <p>Khác {@link #startOnlinePayment} ở chỗ không ghi gì cả — dùng cho màn hình quầy, nơi
     * khoản thu được lập lúc thu ngân bấm nút còn chỗ trả tiền thì lấy lại mỗi lần mở trang để
     * mã hoá thành mã QR. Lấy lại được vì cổng chỉ cần mã khoản thu và số tiền; riêng PayOS thì
     * lần gọi thứ hai trở đi không tạo thêm liên kết mới mà trả về đúng liên kết cũ, nên mở
     * trang mười lần vẫn chỉ có một chỗ để khách trả tiền.
     */
    public PaymentInitResult paymentLink(int paymentId, int orderId, BigDecimal amount,
                                         String baseUrl) {
        return gateway.initiate(paymentId, orderId, amount, baseUrl);
    }

    /** Đơn bán tại quầy hay đơn đặt trước — dùng để chọn trang trả về sau khi cổng báo kết quả. */
    public boolean isCounterOrder(int orderId) {
        return Tx.read(con -> {
            Order order = orderDAO.findById(con, orderId);
            return order != null && !order.isOnline();
        });
    }

    public PaymentGateway getGateway() {
        return gateway;
    }
}
