/**
 * 本文件定义 MenuItemRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findAllByOrderBySortOrderAscIdAsc();

    Optional<MenuItem> findByMenuKey(String menuKey);
}
