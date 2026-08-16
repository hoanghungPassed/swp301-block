package com.fastfood.dao.shared;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.EmailVerificationToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Truy vấn bảng EmailVerificationToken.
 * <p>
 * Mọi phương thức nhận vào <b>bản băm</b> của mã, không bao giờ nhận mã gốc — băm là việc của
 * {@code EmailVerificationService}, và giữ nó ngoài tầng này là cách chắc chắn không có câu
 * lệnh nào lỡ tay ghi mã gốc xuống bảng. Cùng một quy ước với {@link PasswordResetTokenDAO}.
 */
public class EmailVerificationTokenDAO {

    private static final String BASE =
            "SELECT token_id, user_id, token_hash, expires_at, used_at, requested_ip, created_at " +
            "FROM dbo.EmailVerificationToken ";

    public long insert(Connection con, EmailVerificationToken token) throws SQLException {
        String sql = "INSERT INTO dbo.EmailVerificationToken " +
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

    public EmailVerificationToken findByHash(Connection con, String tokenHash) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE token_hash = ?")) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /**
     * Đánh dấu đã dùng, và chỉ khi mã còn chưa dùng.
     * <p>
     * Điều kiện {@code used_at IS NULL} nằm trong chính câu lệnh chứ không kiểm ở tầng trên:
     * trình duyệt và các bộ quét liên kết trong hộp thư đều có thói quen tải trước đường dẫn
     * trong thư, nên lượt bấm thật của người dùng rất hay là lượt thứ hai. Để cơ sở dữ liệu
     * phân xử thì đúng một lượt đổi được số dòng.
     *
     * @return true nếu chính lần gọi này đánh dấu được
     */
    public boolean markUsed(Connection con, long tokenId, LocalDateTime usedAt) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.EmailVerificationToken SET used_at = ? WHERE token_id = ? AND used_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, usedAt);
            ps.setLong(2, tokenId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Vô hiệu hoá mọi mã còn hiệu lực của một tài khoản.
     * <p>
     * Gọi khi cấp mã mới và ngay sau khi xác thực xong. Thiếu chỗ thứ hai thì những lá thư gửi
     * trước đó vẫn còn mã dùng được, nằm lại trong hộp thư mà chẳng để làm gì.
     * <p>
     * Đánh dấu đã dùng chứ không xoá: dòng nhật ký vẫn còn để về sau tra lại.
     */
    public int invalidateAllFor(Connection con, int userId, LocalDateTime at) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.EmailVerificationToken SET used_at = ? WHERE user_id = ? AND used_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, at);
            ps.setInt(2, userId);
            return ps.executeUpdate();
        }
    }

    /** Số lần tài khoản này đã xin thư xác thực kể từ {@code since}. */
    public int countRequestsSince(Connection con, int userId, LocalDateTime since) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.EmailVerificationToken WHERE user_id = ? AND created_at >= ?")) {
            ps.setInt(1, userId);
            JdbcSupport.setDateTime(ps, 2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private EmailVerificationToken map(ResultSet rs) throws SQLException {
        EmailVerificationToken t = new EmailVerificationToken();
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
