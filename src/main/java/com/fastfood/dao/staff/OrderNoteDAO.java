package com.fastfood.dao.staff;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.OrderEntities.OrderNote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderNoteDAO {

    private static final String BASE =
            "SELECT n.order_note_id, n.order_id, n.author_id, n.content, n.created_at, " +
            "       n.updated_at, u.full_name AS author_name " +
            "FROM dbo.OrderNote n " +
            "JOIN dbo.Users u ON u.user_id = n.author_id ";

    /** Chèn ghi chú và lấy khóa tự tăng orderNoteId trả lại entity. */
    public int insert(Connection con, OrderNote note) throws SQLException {
        String sql = "INSERT INTO dbo.OrderNote (order_id, author_id, content, created_at) " +
                     "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, note.getOrderId());
            ps.setInt(2, note.getAuthorId());
            JdbcSupport.setString(ps, 3, note.getContent());
            JdbcSupport.setDateTime(ps, 4, note.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    note.setOrderNoteId(keys.getInt(1));
                }
            }
        }
        return note.getOrderNoteId();
    }

    /** Tìm một ghi chú cùng tên tác giả để Service kiểm tra quyền sở hữu. */
    public OrderNote findById(Connection con, int orderNoteId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE n.order_note_id = ?")) {
            ps.setInt(1, orderNoteId);
            List<OrderNote> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    /** Lấy ghi chú của một đơn theo thứ tự mới nhất trước. */
    public List<OrderNote> findByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE n.order_id = ? ORDER BY n.created_at DESC, n.order_note_id DESC")) {
            ps.setInt(1, orderId);
            return collect(ps);
        }
    }

    /** Lấy ghi chú cho nhiều orderId và gom thành Map để dashboard không phát sinh N+1 query. */
    public Map<Integer, List<OrderNote>> findByOrders(Connection con, List<Integer> orderIds)
            throws SQLException {
        Map<Integer, List<OrderNote>> byOrder = new LinkedHashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return byOrder;
        }
        StringBuilder in = new StringBuilder();
        for (int id : orderIds) {
            if (in.length() > 0) {
                in.append(',');
            }
            in.append(id);
        }
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE n.order_id IN (" + in + ") ORDER BY n.created_at DESC")) {
            for (OrderNote note : collect(ps)) {
                byOrder.computeIfAbsent(note.getOrderId(), k -> new ArrayList<>()).add(note);
            }
        }
        return byOrder;
    }

    /** UPDATE kèm authorId trong WHERE để người khác không thể sửa dù gửi request thủ công. */
    public int update(Connection con, int orderNoteId, int authorId, String content,
                      LocalDateTime now) throws SQLException {
        String sql = "UPDATE dbo.OrderNote SET content = ?, updated_at = ? " +
                     "WHERE order_note_id = ? AND author_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setString(ps, 1, content);
            JdbcSupport.setDateTime(ps, 2, now);
            ps.setInt(3, orderNoteId);
            ps.setInt(4, authorId);
            return ps.executeUpdate();
        }
    }

    /** DELETE kèm authorId trong WHERE để bảo vệ ownership ở cả tầng database. */
    public int delete(Connection con, int orderNoteId, int authorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.OrderNote WHERE order_note_id = ? AND author_id = ?")) {
            ps.setInt(1, orderNoteId);
            ps.setInt(2, authorId);
            return ps.executeUpdate();
        }
    }

    /** Chạy câu SELECT đã bind và ánh xạ các dòng ResultSet thành OrderNote. */
    private List<OrderNote> collect(PreparedStatement ps) throws SQLException {
        List<OrderNote> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderNote n = new OrderNote();
                n.setOrderNoteId(rs.getInt("order_note_id"));
                n.setOrderId(rs.getInt("order_id"));
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
