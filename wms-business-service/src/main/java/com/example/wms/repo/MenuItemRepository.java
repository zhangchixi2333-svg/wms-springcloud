package com.example.wms.repo;

import com.example.wms.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 菜单项数据访问接口
 * 继承JpaRepository，提供MenuItem实体的CRUD操作
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    /**
     * 查询所有菜单，按sortOrder升序、id升序排序
     * @return 排序后的菜单项列表
     */
    List<MenuItem> findAllByOrderBySortOrderAscIdAsc();
    
    /**
     * 根据菜单标识查询菜单
     * @param menuKey 菜单标识
     * @return 菜单项Optional对象，不存在则返回空
     */
    Optional<MenuItem> findByMenuKey(String menuKey);
}