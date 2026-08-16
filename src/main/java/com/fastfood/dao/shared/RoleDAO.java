package com.fastfood.dao.shared;

import com.fastfood.model.entity.Role;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Truy vấn bảng Role. */
public class RoleDAO {

    public List<Role> findAll(Connection con) throws SQLException {
        String sql = "SELECT role_id, name, description FROM dbo.Role ORDER BY role_id";
        List<Role> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Vai trò theo mã, hoặc {@code null} nếu không có.
     * <p>
     * Mã vai trò đến từ ô chọn trên biểu mẫu, tức là từ một chuỗi người dùng sửa được. Không
     * đọc lại ở đây thì mã lạ đi thẳng xuống câu lệnh ghi và bị khoá ngoại chặn — đúng kết quả,
     * sai thông báo: người dùng nhận được "Lỗi ghi dữ liệu" thay vì "Vai trò không hợp lệ".
     */
    public Role findById(Connection con, int roleId) throws SQLException {
        String sql = "SELECT role_id, name, description FROM dbo.Role WHERE role_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Role findByName(Connection con, String name) throws SQLException {
        String sql = "SELECT role_id, name, description FROM dbo.Role WHERE name = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    private Role map(ResultSet rs) throws SQLException {
        Role r = new Role();
        r.setRoleId(rs.getInt("role_id"));
        r.setName(rs.getString("name"));
        r.setDescription(rs.getString("description"));
        return r;
    }
}
