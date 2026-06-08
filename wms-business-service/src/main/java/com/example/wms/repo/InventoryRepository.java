package com.example.wms.repo;

import com.example.wms.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 库存数据访问接口
 * 继承JpaRepository，提供Inventory实体的CRUD操作
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    /**
     * 根据物料ID和库位ID查询库存记录
     * @param partId 物料ID
     * @param locationId 库位ID
     * @return 库存Optional对象，不存在则返回空
     */
    Optional<Inventory> findByPartIdAndLocationId(Long partId, Long locationId);
}