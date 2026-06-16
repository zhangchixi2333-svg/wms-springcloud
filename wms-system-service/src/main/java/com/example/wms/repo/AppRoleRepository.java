/**
 * 本文件定义 AppRoleRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {
    List<AppRole> findAllByOrderByRoleCodeAsc();

    Optional<AppRole> findByRoleCode(String roleCode);
}
