package com.fastfood.dao.shared;

import com.fastfood.model.entity.UserEntities.Notification;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class NotificationDAO {

    private static final String COLS =
            "notification_id, user_id, order_id, channel, event_type, content, status, sent_at, read_at ";

    public int insert(Connection con, Notification n) throws SQLException {
        String sql = "INSERT INTO dbo.Notification (user_id, order_id, channel, event_type, content, " +
                     "status, sent_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setInteger(ps, 1, n.getUserId());
            ps.setInt(2, n.getOrderId());
            ps.setString(3, n.getChannel());
            ps.setString(4, n.getEventType());
            JdbcSupport.setString(ps, 5, n.getContent());
            ps.setString(6, n.getStatus());
            JdbcSupport.setDateTime(ps, 7, n.getSentAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    n.setNotificationId(keys.getInt(1));
                }
            }
        }
        return n.getNotificationId();
    }

    public List<Notification> findByOrder(Connection con, int orderId) throws SQLException {
        String sql = "SELECT " + COLS + "FROM dbo.Notification WHERE order_id = ? ORDER BY notification_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return collect(ps);
        }
    }

    public List<Notification> findByUser(Connection con, int userId, int offset, int limit)
            throws SQLException {
        String sql = "SELECT " + COLS + "FROM dbo.Notification WHERE user_id = ? " +
                     "ORDER BY notification_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, offset);
            ps.setInt(3, limit);
            return collect(ps);
        }
    }

    public long countByUser(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.Notification WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public int countUnread(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM dbo.Notification WHERE user_id = ? AND read_at IS NULL")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int markAllRead(Connection con, int userId, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Notification SET read_at = ? WHERE user_id = ? AND read_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, userId);
            return ps.executeUpdate();
        }
    }

    public int markReadByOrder(Connection con, int userId, int orderId, LocalDateTime now)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Notification SET read_at = ? " +
                "WHERE user_id = ? AND order_id = ? AND read_at IS NULL")) {
            JdbcSupport.setDateTime(ps, 1, now);
            ps.setInt(2, userId);
            ps.setInt(3, orderId);
            return ps.executeUpdate();
        }
    }

    private List<Notification> collect(PreparedStatement ps) throws SQLException {
        List<Notification> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private Notification map(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotificationId(rs.getInt("notification_id"));
        n.setUserId(JdbcSupport.getInteger(rs, "user_id"));
        n.setOrderId(rs.getInt("order_id"));
        n.setChannel(rs.getString("channel"));
        n.setEventType(rs.getString("event_type"));
        n.setContent(rs.getNString("content"));
        n.setStatus(rs.getString("status"));
        n.setSentAt(JdbcSupport.getDateTime(rs, "sent_at"));
        n.setReadAt(JdbcSupport.getDateTime(rs, "read_at"));
        return n;
    }
}
