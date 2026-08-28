package com.fastfood.dao.shared;

import com.fastfood.model.entity.UserEntities.Role;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

    public Role findById(Connection con, int roleId) throws SQLException {
        String sql = "SELECT role_id, name, description FROM dbo.Role WHERE role_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Tìm role theo tên, dùng để gán CUSTOMER khi đăng ký. */
    public Role findByName(Connection con, String name) throws SQLException {
        String sql = "SELECT role_id, name, description FROM dbo.Role WHERE name = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** Ánh xạ một dòng ResultSet thành Role. */
    private Role map(ResultSet rs) throws SQLException {
        Role r = new Role();
        r.setRoleId(rs.getInt("role_id"));
        r.setName(rs.getString("name"));
        r.setDescription(rs.getString("description"));
        return r;
    }
}
