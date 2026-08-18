package com.fastfood.dao.kitchen;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.OperationEntities.KitchenNote;
import com.fastfood.model.entity.OrderEntities.OrderItemNote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class KitchenNoteDAO {

    private static final String ITEM_BASE =
            "SELECT n.note_id, n.order_item_id, n.author_id, n.content, n.created_at, n.updated_at, " +
            "       u.full_name AS author_name " +
            "FROM dbo.OrderItemNote n " +
            "JOIN dbo.Users u ON u.user_id = n.author_id ";

    private static final String SHIFT_BASE =
            "SELECT n.kitchen_note_id, n.shift_date, n.author_id, n.content, n.created_at, " +
            "       n.updated_at, u.full_name AS author_name " +
            "FROM dbo.KitchenNote n " +
            "JOIN dbo.Users u ON u.user_id = n.author_id ";

    public int insertItemNote(Connection con, OrderItemNote note) throws SQLException {
        String sql = "INSERT INTO dbo.OrderItemNote (order_item_id, author_id, content, created_at) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, note.getOrderItemId());
            ps.setInt(2, note.getAuthorId());
            JdbcSupport.setString(ps, 3, note.getContent());
            JdbcSupport.setDateTime(ps, 4, note.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setNoteId(keys.getInt(1));
                }
            }
        }
        return note.getNoteId();
    }

    public OrderItemNote findItemNote(Connection con, int noteId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(ITEM_BASE + "WHERE n.note_id = ?")) {
            ps.setInt(1, noteId);
            List<OrderItemNote> list = collectItemNotes(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    public List<OrderItemNote> findNotesOfItem(Connection con, int orderItemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                ITEM_BASE + "WHERE n.order_item_id = ? ORDER BY n.created_at DESC, n.note_id DESC")) {
            ps.setInt(1, orderItemId);
            return collectItemNotes(ps);
        }
    }

    public int updateItemNote(Connection con, int noteId, int authorId, String content,
                              LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.OrderItemNote SET content = ?, updated_at = ? " +
                     "WHERE note_id = ? AND author_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, content);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, noteId);
            ps.setInt(4, authorId);
            return ps.executeUpdate();
        }
    }

    public int deleteItemNote(Connection con, int noteId, int authorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.OrderItemNote WHERE note_id = ? AND author_id = ?")) {
            ps.setInt(1, noteId);
            ps.setInt(2, authorId);
            return ps.executeUpdate();
        }
    }

    public int insertShiftNote(Connection con, KitchenNote note) throws SQLException {
        String sql = "INSERT INTO dbo.KitchenNote (shift_date, author_id, content, created_at) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            JdbcSupport.setDate(ps, 1, note.getShiftDate());
            ps.setInt(2, note.getAuthorId());
            JdbcSupport.setString(ps, 3, note.getContent());
            JdbcSupport.setDateTime(ps, 4, note.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setKitchenNoteId(keys.getInt(1));
                }
            }
        }
        return note.getKitchenNoteId();
    }

    public KitchenNote findShiftNote(Connection con, int kitchenNoteId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SHIFT_BASE + "WHERE n.kitchen_note_id = ?")) {
            ps.setInt(1, kitchenNoteId);
            List<KitchenNote> list = collectShiftNotes(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    public List<KitchenNote> findRecentShiftNotes(Connection con, int days) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SHIFT_BASE +
                "WHERE n.shift_date >= DATEADD(DAY, ?, CAST(SYSDATETIME() AS DATE)) " +
                "ORDER BY n.shift_date DESC, n.created_at DESC")) {
            ps.setInt(1, -Math.abs(days));
            return collectShiftNotes(ps);
        }
    }

    public int updateShiftNote(Connection con, int kitchenNoteId, int authorId, String content,
                               LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.KitchenNote SET content = ?, updated_at = ? " +
                     "WHERE kitchen_note_id = ? AND author_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, content);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, kitchenNoteId);
            ps.setInt(4, authorId);
            return ps.executeUpdate();
        }
    }

    public int deleteShiftNote(Connection con, int kitchenNoteId, int authorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.KitchenNote WHERE kitchen_note_id = ? AND author_id = ?")) {
            ps.setInt(1, kitchenNoteId);
            ps.setInt(2, authorId);
            return ps.executeUpdate();
        }
    }

    private List<OrderItemNote> collectItemNotes(PreparedStatement ps) throws SQLException {
        List<OrderItemNote> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderItemNote n = new OrderItemNote();
                n.setNoteId(rs.getInt("note_id"));
                n.setOrderItemId(rs.getInt("order_item_id"));
                n.setAuthorId(rs.getInt("author_id"));
                n.setContent(rs.getNString("content"));
                n.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                n.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                n.setAuthorName(rs.getNString("author_name"));
                list.add(n);
            }
        }
        return list;
    }

    private List<KitchenNote> collectShiftNotes(PreparedStatement ps) throws SQLException {
        List<KitchenNote> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                KitchenNote n = new KitchenNote();
                n.setKitchenNoteId(rs.getInt("kitchen_note_id"));
                n.setShiftDate(JdbcSupport.getDate(rs, "shift_date"));
                n.setAuthorId(rs.getInt("author_id"));
                n.setContent(rs.getNString("content"));
                n.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                n.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
                n.setAuthorName(rs.getNString("author_name"));
                list.add(n);
            }
        }
        return list;
    }
}
