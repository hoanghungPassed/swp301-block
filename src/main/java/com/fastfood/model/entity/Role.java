package com.fastfood.model.entity;

import com.fastfood.common.constant.RoleName;

/** Vai trò người dùng. Mỗi tài khoản chỉ giữ một vai trò. */
public class Role {
    private int roleId;
    private String name;
    private String description;

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public RoleName toEnum() { return RoleName.from(name); }
}
