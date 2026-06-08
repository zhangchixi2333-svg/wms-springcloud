package com.example.wms.repo;

import com.example.wms.domain.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 库存交易记录数据访问接口
 * 继承JpaRepository，提供InventoryTransaction实体的CRUD操作
 */
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}