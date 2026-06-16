/**
 * 本文件定义 InventoryRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByPartIdAndLocationId(Long partId, Long locationId);
}
