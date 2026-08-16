package com.fastfood.dao;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Hàm dùng chung cho tầng DAO: đọc và ghi giá trị có thể null.
 * <p>
 * JDBC trả về 0 cho số nguyên null và ném lỗi khi đọc thời gian null, nên mọi cột
 * cho phép bỏ trống đều phải đi qua các hàm ở đây thay vì gọi thẳng {@code rs.getInt()}.
 */
public final class JdbcSupport {

    private JdbcSupport() {
    }

    public static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    public static void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, java.sql.Timestamp.valueOf(value));
        }
    }

    public static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    public static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(value));
        }
    }

    public static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    public static void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NVARCHAR);
        } else {
            ps.setNString(index, value);
        }
    }

    public static BigDecimal getMoney(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Vi phạm ràng buộc duy nhất trong SQL Server.
     * Dùng để nhận biết callback thanh toán bị gọi lại, hoặc khách bấm đặt hàng hai lần —
     * cả hai đều là tình huống bình thường cần bỏ qua chứ không phải lỗi hệ thống.
     */
    public static boolean isUniqueViolation(SQLException e) {
        return e.getErrorCode() == 2627 || e.getErrorCode() == 2601;
    }

    /**
     * Như trên nhưng lần theo cả chuỗi nguyên nhân.
     * Tầng Service bọc lỗi SQL lại thành lỗi của riêng nó, nên khi bắt ở ngoài ranh giới
     * giao dịch thì {@link SQLException} gốc đã nằm sâu bên trong.
     */
    public static boolean isUniqueViolation(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException && isUniqueViolation((SQLException) cause)) {
                return true;
            }
        }
        return false;
    }
}
