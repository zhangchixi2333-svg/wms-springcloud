/**
 * 本文件定义 RoleMenuRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {
    List<RoleMenu> findByRoleCode(String roleCode);

    void deleteByRoleCode(String roleCode);

    void deleteByMenuId(Long menuId);
}
