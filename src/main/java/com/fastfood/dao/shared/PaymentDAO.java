package com.fastfood.dao.shared;

import com.fastfood.model.entity.OrderEntities.Payment;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class PaymentDAO {



    private static final String COLS =
            "p.payment_id, p.order_id, p.method, p.amount, p.payment_status, p.attempt_no, " +
            "p.created_at, p.paid_at ";

    /** Chèn một lần thử thanh toán và gán paymentId tự tăng vào entity. */
    public int insert(Connection con, Payment p) throws SQLException {
        String sql = "INSERT INTO dbo.Payment (order_id, method, amount, payment_status, attempt_no, " +
                     "created_at, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getOrderId());
            ps.setString(2, p.getMethod());
            ps.setBigDecimal(3, p.getAmount());
            ps.setString(4, p.getPaymentStatus());
            ps.setInt(5, p.getAttemptNo());
            JdbcSupport.setDateTime(ps, 6, p.getCreatedAt());
            JdbcSupport.setDateTime(ps, 7, p.getPaidAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setPaymentId(keys.getInt(1));
                }
            }
        }
        return p.getPaymentId();
    }

    /** Tính số lần thử tiếp theo của một đơn từ attemptNo lớn nhất hiện có. */
    public int nextAttemptNo(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ISNULL(MAX(attempt_no), 0) + 1 FROM dbo.Payment WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    /** Đánh dấu payment PENDING thành PAID đúng một lần. */
    public int markPaid(Connection con, int paymentId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Payment SET payment_status = 'PAID', paid_at = ? " +
                     "WHERE payment_id = ? AND payment_status IN ('PENDING', 'UNPAID')";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, paymentId);
            return ps.executeUpdate();
        }
    }

    /** Chuyển payment còn PENDING sang FAILED. */
    public int markFailed(Connection con, int paymentId) throws SQLException {
        String sql = "UPDATE dbo.Payment SET payment_status = 'FAILED' " +
                     "WHERE payment_id = ? AND payment_status = 'PENDING'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            return ps.executeUpdate();
        }
    }

    /* Ghi nhận tiền đã về cho khoản thu mà bộ hẹn giờ đã đóng trước đó. Khác markPaid ở chỗ
       nhận cả trạng thái FAILED: tới lúc gọi hàm này thì cổng thanh toán đã xác nhận tiền vào
       tài khoản cửa hàng, và để bản ghi ở FAILED nghĩa là sổ sách nói không thu được trong khi
       sao kê ngân hàng nói có. Vẫn loại trừ PAID để lần gọi lại thứ hai không dời paid_at. */
    /** Ghi nhận tiền về muộn cho payment đã đóng để phục vụ đối soát. */
    public int markPaidLate(Connection con, int paymentId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Payment SET payment_status = 'PAID', paid_at = ? " +
                     "WHERE payment_id = ? AND payment_status <> 'PAID'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, paymentId);
            return ps.executeUpdate();
        }
    }

    /** Tìm payment theo khóa chính. */
    public Payment findById(Connection con, int paymentId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT " + COLS + "FROM dbo.Payment p WHERE p.payment_id = ?")) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Lấy toàn bộ lần thử thanh toán của một đơn. */
    public List<Payment> findByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT " + COLS + "FROM dbo.Payment p WHERE p.order_id = ? ORDER BY p.attempt_no")) {
            ps.setInt(1, orderId);
            List<Payment> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        }
    }

    /** Tìm payment đã PAID của đơn, nếu có. */
    public Payment findPaidByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT " + COLS + "FROM dbo.Payment p WHERE p.order_id = ? AND p.payment_status = 'PAID'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tìm lần thử thanh toán mới nhất của đơn. */
    public Payment findLatestByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT TOP 1 " + COLS + "FROM dbo.Payment p WHERE p.order_id = ? ORDER BY p.attempt_no DESC")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Ánh xạ một dòng ResultSet thành Payment. */
    private Payment map(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setMethod(rs.getString("method"));
        p.setAmount(JdbcSupport.getMoney(rs, "amount"));
        p.setPaymentStatus(rs.getString("payment_status"));
        p.setAttemptNo(rs.getInt("attempt_no"));
        p.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
        p.setPaidAt(JdbcSupport.getDateTime(rs, "paid_at"));
        return p;
    }
}
