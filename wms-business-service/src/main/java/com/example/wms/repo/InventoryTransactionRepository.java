/**
 * 本文件定义 InventoryTransactionRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Optional<InventoryTransaction> findFirstByOrderByIdDesc();
}
