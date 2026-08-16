package com.fastfood.dao.staff;

import com.fastfood.dao.JdbcSupport;
import com.fastfood.model.entity.OrderNote;

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

/** Truy vấn bảng OrderNote — ghi chú điều phối của thu ngân. */
public class OrderNoteDAO {

    private static final String BASE =
            "SELECT n.order_note_id, n.order_id, n.author_id, n.content, n.created_at, " +
            "       n.updated_at, u.full_name AS author_name " +
            "FROM dbo.OrderNote n " +
            "JOIN dbo.Users u ON u.user_id = n.author_id ";

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

    public OrderNote findById(Connection con, int orderNoteId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE n.order_note_id = ?")) {
            ps.setInt(1, orderNoteId);
            List<OrderNote> list = collect(ps);
            return list.isEmpty() ? null : list.get(0);
        }
    }

    public List<OrderNote> findByOrder(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                BASE + "WHERE n.order_id = ? ORDER BY n.created_at DESC, n.order_note_id DESC")) {
            ps.setInt(1, orderId);
            return collect(ps);
        }
    }

    /**
     * Ghi chú của nhiều đơn cùng lúc, gom theo mã đơn.
     * <p>
     * Màn điều phối hiện bốn tab với hàng chục đơn; hỏi ghi chú từng đơn một sẽ thành hàng chục
     * lượt truy vấn cho một lần mở trang. Danh sách mã đơn ghép thẳng vào câu lệnh được vì nó do
     * chính hệ thống sinh ra từ khoá chính, không phải dữ liệu người dùng nhập.
     */
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

    public int delete(Connection con, int orderNoteId, int authorId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM dbo.OrderNote WHERE order_note_id = ? AND author_id = ?")) {
            ps.setInt(1, orderNoteId);
            ps.setInt(2, authorId);
            return ps.executeUpdate();
        }
    }

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
