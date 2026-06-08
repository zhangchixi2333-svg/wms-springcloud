package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 系统用户实体类
 * 对应数据库表app_user，存储系统用户的账号基本信息
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 登录用户名，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /**
     * 页面显示名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String displayName;

    /**
     * 登录密码哈希，非空
     */
    @Column(nullable = false, length = 128, columnDefinition = "varchar(128) default ''")
    private String passwordHash = "";

    /**
     * 角色名称，非空，最大长度64，如SUPER_ADMIN/WAREHOUSE_MANAGER
     */
    @Column(nullable = false, length = 64)
    private String roleName;

    /**
     * 头像背景色，非空，最大长度16，如"#0f766e"
     */
    @Column(nullable = false, length = 16)
    private String avatarColor;

    /**
     * 当前登录令牌，空值表示未登录
     */
    @Column(length = 128)
    private String authToken;

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getAvatarColor() {
        return avatarColor;
    }

    public void setAvatarColor(String avatarColor) {
        this.avatarColor = avatarColor;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
