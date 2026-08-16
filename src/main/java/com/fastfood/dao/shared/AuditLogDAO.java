package com.fastfood.dao.shared;

import com.fastfood.model.entity.AuditLog;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

/** Truy vấn bảng AuditLog. */
public class AuditLogDAO {

    public void insert(Connection con, AuditLog log) throws SQLException {
        String sql = "INSERT INTO dbo.AuditLog (actor_id, entity_type, entity_id, action, old_value, " +
                     "new_value, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            JdbcSupport.setInteger(ps, 1, log.getActorId());
            ps.setString(2, log.getEntityType());
            ps.setString(3, log.getEntityId());
            ps.setString(4, log.getAction());
            JdbcSupport.setString(ps, 5, log.getOldValue());
            JdbcSupport.setString(ps, 6, log.getNewValue());
            JdbcSupport.setDateTime(ps, 7, log.getCreatedAt());
            ps.executeUpdate();
        }
    }

    public List<AuditLog> findByEntity(Connection con, String entityType, String entityId) throws SQLException {
        String sql = base() + "WHERE a.entity_type = ? AND a.entity_id = ? ORDER BY a.created_at DESC";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setString(2, entityId);
            return collect(ps);
        }
    }

    /**
     * Một trang bản ghi khớp bộ lọc, mới nhất trước.
     * <p>
     * OFFSET/FETCH bắt buộc phải có ORDER BY: không sắp thứ tự thì "trang 2" không có
     * nghĩa gì, và cùng một truy vấn chạy hai lần có thể trả về hai tập khác nhau.
     */
    public List<AuditLog> search(Connection con, String entityType, String action,
                                 LocalDateTime from, LocalDateTime to,
                                 int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = base() + where(entityType, action, from, to, params)
                   + "ORDER BY a.created_at DESC, a.audit_id DESC "
                   + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = bind(ps, params);
            ps.setInt(i++, offset);
            ps.setInt(i, limit);
            return collect(ps);
        }
    }

    /** Tổng số bản ghi khớp bộ lọc, để biết có bao nhiêu trang. */
    public long countSearch(Connection con, String entityType, String action,
                            LocalDateTime from, LocalDateTime to) throws SQLException {
        List<Object> params = new ArrayList<>();
        // Đếm không cần nối sang bảng Users: điều kiện lọc chỉ đụng tới cột của AuditLog.
        String sql = "SELECT COUNT(*) FROM dbo.AuditLog a " + where(entityType, action, from, to, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * Mệnh đề lọc dùng chung cho cả câu lấy dữ liệu lẫn câu đếm.
     * Viết hai lần thì sớm muộn hai bên cũng lệch nhau, và khi đó số trang sẽ không khớp
     * với số dòng thật sự lấy được.
     */
    private String where(String entityType, String action,
                         LocalDateTime from, LocalDateTime to, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        if (entityType != null && !entityType.isBlank()) {
            sql.append("AND a.entity_type = ? ");
            params.add(entityType);
        }
        if (action != null && !action.isBlank()) {
            sql.append("AND a.action = ? ");
            params.add(action);
        }
        if (from != null) {
            sql.append("AND a.created_at >= ? ");
            params.add(Timestamp.valueOf(from));
        }
        if (to != null) {
            sql.append("AND a.created_at <= ? ");
            params.add(Timestamp.valueOf(to));
        }
        return sql.toString();
    }

    /** Gán tham số lọc, trả về vị trí tham số kế tiếp còn trống. */
    private int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return params.size() + 1;
    }

    /** Các loại thao tác đã từng ghi nhận, dùng để đổ vào ô lọc. */
    public List<String> distinctActions(Connection con) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT DISTINCT action FROM dbo.AuditLog ORDER BY action");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString(1));
            }
        }
        return list;
    }

    private String base() {
        return "SELECT a.audit_id, a.actor_id, a.entity_type, a.entity_id, a.action, a.old_value, " +
               "       a.new_value, a.created_at, u.full_name AS actor_name " +
               "FROM dbo.AuditLog a LEFT JOIN dbo.Users u ON u.user_id = a.actor_id ";
    }

    private List<AuditLog> collect(PreparedStatement ps) throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AuditLog a = new AuditLog();
                a.setAuditId(rs.getLong("audit_id"));
                a.setActorId(JdbcSupport.getInteger(rs, "actor_id"));
                a.setEntityType(rs.getString("entity_type"));
                a.setEntityId(rs.getString("entity_id"));
                a.setAction(rs.getString("action"));
                a.setOldValue(rs.getNString("old_value"));
                a.setNewValue(rs.getNString("new_value"));
                a.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
                a.setActorName(rs.getNString("actor_name"));
                list.add(a);
            }
        }
        return list;
    }
}
