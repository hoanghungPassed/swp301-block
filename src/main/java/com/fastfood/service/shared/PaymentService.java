package com.fastfood.service.shared;

import com.fastfood.common.constant.Constants.*;
import com.fastfood.common.exception.AppException.BusinessException;
import com.fastfood.common.exception.AppException.NotFoundException;
import com.fastfood.common.exception.AppException.ValidationException;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

public class PaymentService {

    public enum CallbackResult {
        PAID,
        FAILED,
        DUPLICATE,
        REFUNDED_ORDER_GONE,
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
    private final PaymentGateway gateway = PaymentGateways.fromConfig();

    public String startOnlinePayment(int orderId, int customerId, String baseUrl) {
        LocalDateTime now = DateTimeUtil.now();

        return Tx.write(con -> {
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

            PaymentInitResult init = gateway.initiate(payment.getPaymentId(), orderId,
                    order.getTotalAmount(), baseUrl);
            return init.getRedirectUrl();
        });
    }

    public CallbackResult handleCallback(GatewayCallback callback) {
        if (!gateway.verifySignature(callback)) {
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

            int paidRows = paymentDAO.markPaid(con, payment.getPaymentId(), now);
            if (paidRows == 0) {
                return CallbackResult.DUPLICATE;
            }
            auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                    AuditAction.PAYMENT_PAID, PaymentStatus.PAID.name());

            Order order = orderDAO.findById(con, payment.getOrderId());
            if (order != null && order.isOnline()
                    && !orderCore.confirmOnlineAfterPaid(con, order, now)) {
                paymentDAO.markRefunded(con, payment.getPaymentId(), now);
                auditService.logSystem(con, "PAYMENT", payment.getPaymentId(),
                        AuditAction.PAYMENT_REFUNDED, PaymentStatus.REFUNDED.name());
                notificationService.notifyRefundedOrderGone(con, order);
                LOG.warning("Tien ve sau khi don #" + order.getOrderId()
                        + " het hieu luc (" + order.getOrderStatus() + "); da hoan lai ngay.");
                return CallbackResult.REFUNDED_ORDER_GONE;
            }
            return CallbackResult.PAID;
        });
    }

    public void refund(int orderId, int actorId, String reason) {
        String note = reason == null ? "" : reason.trim();
        if (note.isEmpty()) {
            throw new ValidationException("Vui lòng nhập lý do hoàn tiền.");
        }
        LocalDateTime now = DateTimeUtil.now();
        Tx.writeVoid(con -> {
            Order order = orderDAO.findById(con, orderId);
            if (order == null) {
                throw new NotFoundException("Không tìm thấy đơn hàng.");
            }
            Payment paid = paymentDAO.findPaidByOrder(con, orderId);
            if (paid == null) {
                throw new BusinessException("Đơn này chưa có khoản thanh toán nào để hoàn.");
            }
            String status = order.getOrderStatus();
            if (!OrderStatus.CANCELLED.name().equals(status) && !OrderStatus.EXPIRED.name().equals(status)) {
                throw new BusinessException("Chỉ hoàn tiền cho đơn đã huỷ hoặc đã hết hiệu lực. "
                        + "Đơn còn hiệu lực thì dùng chức năng huỷ đơn — hệ thống hoàn tiền kèm theo.");
            }

            int changed = paymentDAO.markRefunded(con, paid.getPaymentId(), now);
            if (changed == 0) {
                throw new BusinessException("Khoản thanh toán này đã được hoàn trước đó.");
            }
            auditService.log(con, actorId, "PAYMENT", paid.getPaymentId(),
                    AuditAction.PAYMENT_REFUNDED, PaymentStatus.PAID.name(),
                    PaymentStatus.REFUNDED.name() + ": " + note);
        });
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

    public PaymentGateway getGateway() {
        return gateway;
    }
}
