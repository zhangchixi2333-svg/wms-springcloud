/**
 * 本文件定义库存操作单明细的数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.InventoryOperationOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryOperationOrderRepository extends JpaRepository<InventoryOperationOrder, Long> {

    List<InventoryOperationOrder> findByOperationNoOrderByIdAsc(String operationNo);

    List<InventoryOperationOrder> findByOperationNoAndSourceBarcodeOrderByIdAsc(String operationNo, String sourceBarcode);

    List<InventoryOperationOrder> findByOperationNoAndTargetBarcodeOrderByIdAsc(String operationNo, String targetBarcode);
}
