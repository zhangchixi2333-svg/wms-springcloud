package com.example.wms.domain;

import jakarta.persistence.*;

/**
 * 菜单项实体类
 * 对应数据库表menu_item，存储系统前端菜单配置信息
 * 用于系统权限控制和前端菜单渲染
 */
@Entity
@Table(name = "menu_item")
public class MenuItem {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 父菜单ID，顶级菜单为null
     */
    private Long parentId;

    /**
     * 菜单唯一标识，唯一非空，最大长度64
     */
    @Column(nullable = false, unique = true, length = 64)
    private String menuKey;

    /**
     * 菜单名称，非空，最大长度128
     */
    @Column(nullable = false, length = 128)
    private String menuName;

    /**
     * 菜单类型，非空，最大长度32，如DIRECTORY/MENU/BUTTON
     */
    @Column(nullable = false, length = 32)
    private String menuType;

    /**
     * 关联页面标识，最大长度64
     */
    @Column(length = 64)
    private String pageKey;

    /**
     * 图标标识，最大长度64
     */
    @Column(length = 64)
    private String iconKey;

    /**
     * 路由路径，最大长度64
     */
    @Column(length = 64)
    private String pathKey;

    /**
     * 排序权重，非空，数值越小越靠前
     */
    @Column(nullable = false)
    private Integer sortOrder;

    /**
     * 是否可见，非空，true表示在菜单中显示
     */
    @Column(nullable = false)
    private boolean visible;

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public void setMenuKey(String menuKey) {
        this.menuKey = menuKey;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getPageKey() {
        return pageKey;
    }

    public void setPageKey(String pageKey) {
        this.pageKey = pageKey;
    }

    public String getIconKey() {
        return iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    public String getPathKey() {
        return pathKey;
    }

    public void setPathKey(String pathKey) {
        this.pathKey = pathKey;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}