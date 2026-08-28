package com.fastfood.dao.shared;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.UserEntities.PasswordResetToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

public class PasswordResetTokenDAO {

    private static final String BASE =
            "SELECT token_id, user_id, token_hash, expires_at, used_at, requested_ip, created_at " +
            "FROM dbo.PasswordResetToken ";

    /** Lưu hash token đặt lại mật khẩu và trả tokenId tự tăng. */
    public long insert(Connection con, PasswordResetToken token) throws SQLException {
        String sql = "INSERT INTO dbo.PasswordResetToken " +
                     "(user_id, token_hash, expires_at, requested_ip, created_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, token.getUserId());
            ps.setString(2, token.getTokenHash());
            JdbcSupport.setDateTime(ps, 3, token.getExpiresAt());
            ps.setString(4, token.getRequestedIp());
            JdbcSupport.setDateTime(ps, 5, token.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    token.setTokenId(keys.getLong(1));
                }
            }
        }
        return token.getTokenId();
    }

    /** Tìm token reset bằng hash nhận từ liên kết. */
    public PasswordResetToken findByHash(Connection con, String tokenHash) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE token_hash = ?")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Đánh dấu token reset chưa dùng thành đã dùng theo cách atomic. */
    public boolean markUsed(Connection con, long tokenId, LocalDateTime usedAt) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.PasswordResetToken SET used_at = ? WHERE token_id = ? AND used_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, usedAt);
            ps.setLong(2, tokenId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Vô hiệu hóa toàn bộ token reset chưa dùng của user. */
    public int invalidateAllFor(Connection con, int userId, LocalDateTime at) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.PasswordResetToken SET used_at = ? WHERE user_id = ? AND used_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, at);
            ps.setInt(2, userId);
            return ps.executeUpdate();
        }
    }

    /** Đếm số token reset được yêu cầu gần đây để áp dụng rate limit. */
    public int countRequestsSince(Connection con, int userId, LocalDateTime since) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.PasswordResetToken WHERE user_id = ? AND created_at >= ?")) {
            ps.setInt(1, userId);
            JdbcSupport.setDateTime(ps, 2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Ánh xạ một dòng ResultSet thành PasswordResetToken. */
    private PasswordResetToken map(ResultSet rs) throws SQLException {
        PasswordResetToken t = new PasswordResetToken();
        t.setTokenId(rs.getLong("token_id"));
        t.setUserId(rs.getInt("user_id"));
        t.setTokenHash(rs.getString("token_hash"));
        t.setExpiresAt(JdbcSupport.getDateTime(rs, "expires_at"));
        t.setUsedAt(JdbcSupport.getDateTime(rs, "used_at"));
        t.setRequestedIp(rs.getString("requested_ip"));
        t.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
        return t;
    }
}
