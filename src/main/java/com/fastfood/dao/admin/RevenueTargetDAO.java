package com.fastfood.dao.admin;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.RevenueTarget;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng RevenueTarget — chỉ tiêu doanh thu theo kỳ. */
public class RevenueTargetDAO {

    private static final String BASE =
            "SELECT t.target_id, t.period_type, t.period_start, t.target_amount, t.note, " +
            "       t.created_by, t.created_at, t.updated_at, u.full_name AS created_by_name " +
            "FROM dbo.RevenueTarget t " +
            "JOIN dbo.Users u ON u.user_id = t.created_by ";

    public int insert(Connection con, RevenueTarget target) throws SQLException {
        String sql = "INSERT INTO dbo.RevenueTarget " +
                     "(period_type, period_start, target_amount, note, created_by, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, target.getPeriodType());
            JdbcSupport.setDate(ps, 2, target.getPeriodStart());
            ps.setBigDecimal(3, target.getTargetAmount());
            JdbcSupport.setString(ps, 4, target.getNote());
            ps.setInt(5, target.getCreatedBy());
            JdbcSupport.setDateTime(ps, 6, target.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    target.setTargetId(keys.getInt(1));
                }
            }
        }
        return target.getTargetId();
    }

    public RevenueTarget findById(Connection con, int targetId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE t.target_id = ?")) {
            ps.setInt(1, targetId);
            List<RevenueTarget> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Chỉ tiêu của đúng một kỳ. Trả null nghĩa là kỳ đó chưa ai đặt chỉ tiêu. */
    public RevenueTarget findByPeriod(Connection con, String periodType, LocalDate periodStart)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE t.period_type = ? AND t.period_start = ?")) {
            ps.setString(1, periodType);
            JdbcSupport.setDate(ps, 2, periodStart);
            List<RevenueTarget> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Danh sách chỉ tiêu, kỳ gần nhất trước. */
    public List<RevenueTarget> findRecent(Connection con, int limit) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE.replaceFirst("SELECT", "SELECT TOP (" + limit + ")") +
                "ORDER BY t.period_start DESC, t.period_type")) {
            return collect(ps);
        }
    }

    public int update(Connection con, int targetId, java.math.BigDecimal amount, String note,
                      LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.RevenueTarget SET target_amount = ?, note = ?, updated_at = ? " +
                     "WHERE target_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, amount);
            JdbcSupport.setString(ps, 2, note);
            JdbcSupport.setDateTime(ps, 3, now);
            ps.setInt(4, targetId);
            return ps.executeUpdate();
        }
    }

    public int delete(Connection con, int targetId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.RevenueTarget WHERE target_id = ?")) {
            ps.setInt(1, targetId);
            return ps.executeUpdate();
        }
    }

    private List<RevenueTarget> collect(PreparedStatement ps) throws SQLException {
        List<RevenueTarget> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RevenueTarget t = new RevenueTarget();
                t.setTargetId(rs.getInt("target_id"));
                t.setPeriodType(rs.getString("period_type"));
                t.setPeriodStart(JdbcSupport.getDate(rs, "period_start"));
                t.setTargetAmount(rs.getBigDecimal("target_amount"));
                t.setNote(rs.getNString("note"));
                t.setCreatedBy(rs.getInt("created_by"));
                t.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                t.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                t.setCreatedByName(rs.getNString("created_by_name"));
                list.add(t);
            }
        }
        return list;
    }
}
