package com.fastfood.dao.shared;

import com.fastfood.model.entity.UserEntities.User;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fastfood.dao.JdbcSupport;

public class UserDAO {

    private static final String BASE =
            "SELECT u.user_id, u.full_name, u.email, u.phone, u.password_hash, u.role_id, " +
            "       u.status, u.must_change_password, u.email_verified, u.created_at, u.updated_at, " +
            "       r.name AS role_name " +
            "FROM dbo.Users u JOIN dbo.Role r ON r.role_id = u.role_id ";

    public User findByEmail(Connection con, String email) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE u.email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public User findById(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(BASE + "WHERE u.user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public boolean emailExists(Connection con, String email) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM dbo.Users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<User> search(Connection con, String roleName, String keyword) throws SQLException {
        return search(con, roleName, keyword, null, 0, Integer.MAX_VALUE);
    }

    public List<User> search(Connection con, String roleName, String keyword, String status,
                             int offset, int limit) throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = BASE + where(roleName, keyword, status, params)
                   + "ORDER BY r.role_id, u.full_name, u.user_id "
                   + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        params.add(offset);
        params.add(limit);

        List<User> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public long countSearch(Connection con, String roleName, String keyword, String status)
            throws SQLException {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM dbo.Users u JOIN dbo.Role r ON r.role_id = u.role_id "
                   + where(roleName, keyword, status, params);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private String where(String roleName, String keyword, String status, List<Object> params) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");
        if (roleName != null && !roleName.isBlank()) {
            sql.append("AND r.name = ? ");
            params.add(roleName);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (u.full_name LIKE ? OR u.email LIKE ?) ");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND u.status = ? ");
            params.add(status);
        }
        return sql.toString();
    }

    public List<User> findByRole(Connection con, String roleName) throws SQLException {
        return search(con, roleName, null);
    }

    public int insert(Connection con, User u) throws SQLException {
        String sql = "INSERT INTO dbo.Users " +
                     "(full_name, email, phone, password_hash, role_id, status, email_verified, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setNString(1, u.getFullName());
            ps.setString(2, u.getEmail());
            JdbcSupport.setString(ps, 3, u.getPhone());
            ps.setString(4, u.getPasswordHash());
            ps.setInt(5, u.getRoleId());
            ps.setString(6, u.getStatus());
            ps.setBoolean(7, u.isEmailVerified());
            JdbcSupport.setDateTime(ps, 8, u.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    u.setUserId(keys.getInt(1));
                }
            }
        }
        return u.getUserId();
    }

    public void updateProfile(Connection con, User u) throws SQLException {
        String sql = "UPDATE dbo.Users SET full_name = ?, phone = ?, updated_at = ? WHERE user_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setNString(1, u.getFullName());
            JdbcSupport.setString(ps, 2, u.getPhone());
            JdbcSupport.setDateTime(ps, 3, u.getUpdatedAt());
            ps.setInt(4, u.getUserId());
            ps.executeUpdate();
        }
    }

    public void updatePassword(Connection con, int userId, String passwordHash,
                               boolean mustChange) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Users SET password_hash = ?, must_change_password = ? WHERE user_id = ?")) {
            ps.setString(1, passwordHash);
            ps.setBoolean(2, mustChange);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    public boolean markEmailVerified(Connection con, int userId, LocalDateTime at) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Users SET email_verified = 1, updated_at = ? " +
                "WHERE user_id = ? AND email_verified = 0")) {
            JdbcSupport.setDateTime(ps, 1, at);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public void updateStatus(Connection con, int userId, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Users SET status = ? WHERE user_id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateRole(Connection con, int userId, int roleId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE dbo.Users SET role_id = ? WHERE user_id = ?")) {
            ps.setInt(1, roleId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setFullName(rs.getNString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRoleId(rs.getInt("role_id"));
        u.setStatus(rs.getString("status"));
        u.setMustChangePassword(rs.getBoolean("must_change_password"));
        u.setEmailVerified(rs.getBoolean("email_verified"));
        u.setCreatedAt(JdbcSupport.getDateTime(rs, "created_at"));
        u.setUpdatedAt(JdbcSupport.getDateTime(rs, "updated_at"));
        u.setRoleName(rs.getString("role_name"));
        return u;
    }
}
