/**
 * 本文件定义 InventoryTransactionRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Optional<InventoryTransaction> findFirstByOrderByIdDesc();

    @Query("""
            select tx
            from InventoryTransaction tx
            join Part p on p.id = tx.partId
            join Location l on l.id = tx.locationId
            where (:partCode is null or :partCode = '' or lower(p.partCode) like lower(concat('%', :partCode, '%')))
              and (:businessType is null or :businessType = '' or upper(tx.businessType) = upper(:businessType))
              and (:businessNo is null or :businessNo = '' or lower(tx.businessNo) like lower(concat('%', :businessNo, '%')))
              and (:operationNo is null or :operationNo = '' or lower(tx.operationNo) like lower(concat('%', :operationNo, '%')))
              and (:barcode is null or :barcode = '' or lower(tx.barcode) like lower(concat('%', :barcode, '%')))
              and (:locationCode is null or :locationCode = '' or lower(l.locationCode) like lower(concat('%', :locationCode, '%')))
            """)
    Page<InventoryTransaction> searchTransactions(@Param("partCode") String partCode,
                                                  @Param("businessType") String businessType,
                                                  @Param("businessNo") String businessNo,
                                                  @Param("operationNo") String operationNo,
                                                  @Param("barcode") String barcode,
                                                  @Param("locationCode") String locationCode,
                                                  Pageable pageable);
}
