package com.fastfood.dao;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class JdbcSupport {

    private JdbcSupport() {
    }

    /** Đọc cột số nguyên nullable mà vẫn phân biệt được SQL NULL với giá trị 0. */
    public static Integer getInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Chuyển SQL TIMESTAMP nullable sang LocalDateTime cho entity. */
    public static LocalDateTime getDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** Gắn LocalDateTime hoặc SQL NULL vào PreparedStatement. */
    public static void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, java.sql.Timestamp.valueOf(value));
        }
    }

    /** Chuyển SQL DATE nullable sang LocalDate. */
    public static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    /** Gắn LocalDate hoặc SQL NULL vào PreparedStatement. */
    public static void setDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(value));
        }
    }

    /** Gắn Integer nullable, dùng cho customerId và các khoá ngoại không bắt buộc. */
    public static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /** Gắn chuỗi Unicode nullable vào câu SQL Server. */
    public static void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.NVARCHAR);
        } else {
            ps.setNString(index, value);
        }
    }

    /** Đọc tiền từ DB và quy ước NULL thành 0 để phép cộng tổng tiền an toàn. */
    public static BigDecimal getMoney(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Nhận diện hai mã lỗi unique key của SQL Server để đổi sang thông báo nghiệp vụ. */
    public static boolean isUniqueViolation(SQLException e) {
        return e.getErrorCode() == 2627 || e.getErrorCode() == 2601;
    }

    /** Tìm lỗi unique key xuyên qua chuỗi nguyên nhân khi exception đã được service bọc lại. */
    public static boolean isUniqueViolation(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException && isUniqueViolation((SQLException) cause)) {
                return true;
            }
        }
        return false;
    }
}
