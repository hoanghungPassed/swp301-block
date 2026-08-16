package com.fastfood.dao.shared;

import com.fastfood.model.entity.Transaction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/**
 * Truy vấn bảng PaymentTransaction — nhật ký đối soát với cổng thanh toán.
 */
public class TransactionDAO {

    /**
     * Ghi nhận một giao dịch từ cổng thanh toán.
     * <p>
     * Trả về false khi mã giao dịch đã tồn tại, nghĩa là cổng thanh toán gọi lại lần thứ hai.
     * Cách nhận biết này dựa vào ràng buộc duy nhất của cơ sở dữ liệu chứ không kiểm tra
     * bằng câu SELECT trước — hai lệnh gọi về cùng lúc vẫn có thể cùng thấy "chưa tồn tại".
     */
    public boolean insertIfNew(Connection con, Transaction t) throws SQLException {
        String sql = "INSERT INTO dbo.PaymentTransaction (payment_id, gateway, external_transaction_id, " +
                     "status, raw_reference, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, t.getPaymentId());
            ps.setString(2, t.getGateway());
            ps.setString(3, t.getExternalTransactionId());
            ps.setString(4, t.getStatus());
            JdbcSupport.setString(ps, 5, t.getRawReference());
            JdbcSupport.setDateTime(ps, 6, t.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setTransactionId(keys.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            if (JdbcSupport.isUniqueViolation(e)) {
                return false;
            }
            throw e;
        }
    }

    public List<Transaction> findByPayment(Connection con, int paymentId) throws SQLException {
        String sql = "SELECT transaction_id, payment_id, gateway, external_transaction_id, status, " +
                     "raw_reference, created_at FROM dbo.PaymentTransaction " +
                     "WHERE payment_id = ? ORDER BY transaction_id";
        List<Transaction> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction t = new Transaction();
                    t.setTransactionId(rs.getInt("transaction_id"));
                    t.setPaymentId(rs.getInt("payment_id"));
                    t.setGateway(rs.getString("gateway"));
                    t.setExternalTransactionId(rs.getString("external_transaction_id"));
                    t.setStatus(rs.getString("status"));
                    t.setRawReference(rs.getNString("raw_reference"));
                    t.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                    list.add(t);
                }
            }
        }
        return list;
    }

    public Transaction newTransaction(int paymentId, String gateway, String externalId,
                                      String status, String raw, LocalDateTime now) {
        Transaction t = new Transaction();
        t.setPaymentId(paymentId);
        t.setGateway(gateway);
        t.setExternalTransactionId(externalId);
        t.setStatus(status);
        t.setRawReference(raw);
        t.setCreatedAt(now);
        return t;
    }
}
