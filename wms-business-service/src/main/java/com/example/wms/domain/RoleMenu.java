package com.example.wms.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "role_menu", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_menu", columnNames = {"role_code", "menu_id"})
})
public class RoleMenu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    public Long getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }
}
