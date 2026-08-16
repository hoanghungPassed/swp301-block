package com.fastfood.dao.staff;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.Shift;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng Shift — ca làm việc và số liệu đối soát tiền mặt của nó. */
public class ShiftDAO {

    private static final String BASE =
            "SELECT s.shift_id, s.cashier_id, s.opened_at, s.opening_cash, s.closed_at, " +
            "       s.counted_cash, s.expected_cash, s.variance, s.note, s.status, " +
            "       u.full_name AS cashier_name, " +
            "       (SELECT COUNT(*) FROM dbo.Orders o WHERE o.shift_id = s.shift_id) AS order_count " +
            "FROM dbo.Shift s " +
            "JOIN dbo.Users u ON u.user_id = s.cashier_id ";

    public int insert(Connection con, Shift shift) throws SQLException {
        String sql = "INSERT INTO dbo.Shift (cashier_id, opened_at, opening_cash, note, status) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, shift.getCashierId());
            JdbcSupport.setDateTime(ps, 2, shift.getOpenedAt());
            ps.setBigDecimal(3, shift.getOpeningCash());
            JdbcSupport.setString(ps, 4, shift.getNote());
            ps.setString(5, shift.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    shift.setShiftId(keys.getInt(1));
                }
            }
        }
        return shift.getShiftId();
    }

    public Shift findById(Connection con, int shiftId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE s.shift_id = ?")) {
            ps.setInt(1, shiftId);
            List<Shift> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Ca đang mở của một thu ngân. Chỉ có tối đa một — xem {@code UX_Shift_openPerCashier}. */
    public Shift findOpenOf(Connection con, int cashierId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE s.cashier_id = ? AND s.status = 'OPEN'")) {
            ps.setInt(1, cashierId);
            List<Shift> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Lịch sử ca của một thu ngân, mới nhất trước. */
    public List<Shift> findByCashier(Connection con, int cashierId, int limit) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE.replaceFirst("SELECT", "SELECT TOP (" + limit + ")") +
                "WHERE s.cashier_id = ? ORDER BY s.opened_at DESC")) {
            ps.setInt(1, cashierId);
            return collect(ps);
        }
    }

    public int updateNote(Connection con, int shiftId, int cashierId, String note) throws SQLException {
        String sql = "UPDATE dbo.Shift SET note = ? WHERE shift_id = ? AND cashier_id = ? AND status = 'OPEN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, note);
            ps.setInt(2, shiftId);
            ps.setInt(3, cashierId);
            return ps.executeUpdate();
        }
    }

    public int close(Connection con, int shiftId, int cashierId, BigDecimal counted,
                     BigDecimal expected, BigDecimal variance, LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.Shift SET status = 'CLOSED', closed_at = ?, counted_cash = ?, " +
                     "expected_cash = ?, variance = ? " +
                     "WHERE shift_id = ? AND cashier_id = ? AND status = 'OPEN'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setBigDecimal(2, counted);
            ps.setBigDecimal(3, expected);
            ps.setBigDecimal(4, variance);
            ps.setInt(5, shiftId);
            ps.setInt(6, cashierId);
            return ps.executeUpdate();
        }
    }

    /**
     * Thu hồi ca mở nhầm. Điều kiện "chưa có đơn nào" nằm ngay trong câu lệnh: kiểm tra trước
     * rồi mới ghi thì giữa hai bước đó một đơn có thể vừa được gắn vào ca.
     */
    public int cancel(Connection con, int shiftId, int cashierId) throws SQLException {
        String sql = "UPDATE dbo.Shift SET status = 'CANCELLED' " +
                     "WHERE shift_id = ? AND cashier_id = ? AND status = 'OPEN' " +
                     "AND NOT EXISTS (SELECT 1 FROM dbo.Orders o WHERE o.shift_id = ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            ps.setInt(2, cashierId);
            ps.setInt(3, shiftId);
            return ps.executeUpdate();
        }
    }

    /**
     * Số tiền mặt lẽ ra phải có trong két của ca này.
     * <p>
     * Cả hai vế đều <b>chỉ tính đơn thuộc chính ca này</b>, và đây là điểm quan trọng: phạm vi
     * theo ca, không theo khoảng thời gian. Trừ mọi khoản hoàn tiền mặt xảy ra trong giờ của ca
     * sẽ kéo cả khoản hoàn của thu ngân khác — hai người bán cùng lúc ở hai két thì két này gánh
     * khoản chi của két kia.
     * <p>
     * Trong phạm vi đó, vế thu và vế hoàn vẫn đếm theo <b>hai mốc thời gian riêng</b> —
     * {@code paid_at} và {@code refunded_at} — cùng bài học với báo cáo doanh thu. Lọc vế thu
     * theo trạng thái {@code PAID} mới là sai: một khoản đã thu rồi hoàn sẽ biến mất khỏi vế thu
     * nhưng vẫn còn ở vế hoàn, tức là bị trừ hai lần.
     * <p>
     * <b>Giới hạn đã biết:</b> khoản hoàn cho đơn của ca trước, chi ra từ két của ca này, không
     * được tính vào đây. Bảng {@code Payment} không lưu ai thực hiện việc hoàn tiền nên hệ thống
     * không quy được nó về đúng két; quy bừa theo thời gian còn sai hơn. Dấu vết vẫn còn đủ
     * trong nhật ký thao tác ({@code PAYMENT_REFUNDED} có người thực hiện) để đối chiếu tay.
     */
    public BigDecimal expectedCash(Connection con, int shiftId) throws SQLException {
        String sql =
            "SELECT s.opening_cash " +
            "     + ISNULL((SELECT SUM(p.amount) FROM dbo.Payment p " +
            "               JOIN dbo.Orders o ON o.order_id = p.order_id " +
            "               WHERE o.shift_id = s.shift_id AND p.method = 'CASH' " +
            "                 AND p.paid_at IS NOT NULL), 0) " +
            "     - ISNULL((SELECT SUM(p.amount) FROM dbo.Payment p " +
            "               JOIN dbo.Orders o ON o.order_id = p.order_id " +
            "               WHERE o.shift_id = s.shift_id AND p.method = 'CASH' " +
            "                 AND p.refunded_at IS NOT NULL), 0) " +
            "       AS expected_cash " +
            "FROM dbo.Shift s WHERE s.shift_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shiftId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? JdbcSupport.getMoney(rs, "expected_cash") : BigDecimal.ZERO;
            }
        }
    }

    private List<Shift> collect(PreparedStatement ps) throws SQLException {
        List<Shift> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Shift s = new Shift();
                s.setShiftId(rs.getInt("shift_id"));
                s.setCashierId(rs.getInt("cashier_id"));
                s.setOpenedAt(JdbcSupport.getDateTime(rs, "opened_at"));
                s.setOpeningCash(JdbcSupport.getMoney(rs, "opening_cash"));
                s.setClosedAt(JdbcSupport.getDateTime(rs, "closed_at"));
                s.setCountedCash(rs.getBigDecimal("counted_cash"));
                s.setExpectedCash(rs.getBigDecimal("expected_cash"));
                s.setVariance(rs.getBigDecimal("variance"));
                s.setNote(rs.getNString("note"));
                s.setStatus(rs.getString("status"));
                s.setCashierName(rs.getNString("cashier_name"));
                s.setOrderCount(rs.getInt("order_count"));
                list.add(s);
            }
        }
        return list;
    }
}
