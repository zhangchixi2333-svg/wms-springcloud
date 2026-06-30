/**
 * 本文件定义 InventoryRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByPartIdAndLocationId(Long partId, Long locationId);

    @Query(value = """
            SELECT
              p.id AS partId,
              p.part_code AS partCode,
              p.part_name AS partName,
              p.supplier_id AS supplierId,
              COALESCE(s.supplier_name, '-') AS supplierName,
              SUM(i.qty) AS totalQty,
              COUNT(*) AS locationCount,
              MAX(i.updated_at) AS latestUpdatedAt
            FROM inventory i
            JOIN part p ON p.id = i.part_id
            LEFT JOIN supplier s ON s.id = p.supplier_id
            JOIN location l ON l.id = i.location_id
            WHERE (:warehouseName IS NULL OR :warehouseName = '' OR l.warehouse_name LIKE CONCAT('%', :warehouseName, '%'))
              AND (:zoneName IS NULL OR :zoneName = '' OR l.zone_name LIKE CONCAT('%', :zoneName, '%'))
              AND (:materialKeyword IS NULL OR :materialKeyword = '' OR p.part_code LIKE CONCAT('%', :materialKeyword, '%') OR p.part_name LIKE CONCAT('%', :materialKeyword, '%'))
              AND (:supplierId IS NULL OR p.supplier_id = :supplierId)
            GROUP BY p.id, p.part_code, p.part_name, p.supplier_id, s.supplier_name
            ORDER BY latestUpdatedAt DESC, p.part_code ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM (
              SELECT p.id
              FROM inventory i
              JOIN part p ON p.id = i.part_id
              LEFT JOIN supplier s ON s.id = p.supplier_id
              JOIN location l ON l.id = i.location_id
              WHERE (:warehouseName IS NULL OR :warehouseName = '' OR l.warehouse_name LIKE CONCAT('%', :warehouseName, '%'))
                AND (:zoneName IS NULL OR :zoneName = '' OR l.zone_name LIKE CONCAT('%', :zoneName, '%'))
                AND (:materialKeyword IS NULL OR :materialKeyword = '' OR p.part_code LIKE CONCAT('%', :materialKeyword, '%') OR p.part_name LIKE CONCAT('%', :materialKeyword, '%'))
                AND (:supplierId IS NULL OR p.supplier_id = :supplierId)
              GROUP BY p.id
            ) grouped_inventory
            """,
            nativeQuery = true)
    Page<InventoryPartSummaryProjection> searchPartSummary(@Param("warehouseName") String warehouseName,
                                                           @Param("zoneName") String zoneName,
                                                           @Param("materialKeyword") String materialKeyword,
                                                           @Param("supplierId") Long supplierId,
                                                           Pageable pageable);

    @Query(value = """
            SELECT
              i.id AS id,
              i.part_id AS partId,
              p.part_code AS partCode,
              p.part_name AS partName,
              COALESCE(s.supplier_name, '-') AS supplierName,
              i.location_id AS locationId,
              l.location_code AS locationCode,
              l.warehouse_name AS warehouseName,
              l.zone_name AS zoneName,
              i.qty AS qty,
              i.updated_at AS updatedAt
            FROM inventory i
            JOIN part p ON p.id = i.part_id
            LEFT JOIN supplier s ON s.id = p.supplier_id
            JOIN location l ON l.id = i.location_id
            WHERE (:partCode IS NULL OR :partCode = '' OR p.part_code = :partCode)
              AND (:warehouseName IS NULL OR :warehouseName = '' OR l.warehouse_name LIKE CONCAT('%', :warehouseName, '%'))
              AND (:zoneName IS NULL OR :zoneName = '' OR l.zone_name LIKE CONCAT('%', :zoneName, '%'))
              AND (:materialKeyword IS NULL OR :materialKeyword = '' OR p.part_code LIKE CONCAT('%', :materialKeyword, '%') OR p.part_name LIKE CONCAT('%', :materialKeyword, '%') OR l.location_code LIKE CONCAT('%', :materialKeyword, '%'))
              AND (:supplierId IS NULL OR p.supplier_id = :supplierId)
            ORDER BY i.updated_at DESC, i.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM inventory i
            JOIN part p ON p.id = i.part_id
            LEFT JOIN supplier s ON s.id = p.supplier_id
            JOIN location l ON l.id = i.location_id
            WHERE (:partCode IS NULL OR :partCode = '' OR p.part_code = :partCode)
              AND (:warehouseName IS NULL OR :warehouseName = '' OR l.warehouse_name LIKE CONCAT('%', :warehouseName, '%'))
              AND (:zoneName IS NULL OR :zoneName = '' OR l.zone_name LIKE CONCAT('%', :zoneName, '%'))
              AND (:materialKeyword IS NULL OR :materialKeyword = '' OR p.part_code LIKE CONCAT('%', :materialKeyword, '%') OR p.part_name LIKE CONCAT('%', :materialKeyword, '%') OR l.location_code LIKE CONCAT('%', :materialKeyword, '%'))
              AND (:supplierId IS NULL OR p.supplier_id = :supplierId)
            """,
            nativeQuery = true)
    Page<InventoryLocationProjection> searchLocationRows(@Param("partCode") String partCode,
                                                         @Param("warehouseName") String warehouseName,
                                                         @Param("zoneName") String zoneName,
                                                         @Param("materialKeyword") String materialKeyword,
                                                         @Param("supplierId") Long supplierId,
                                                         Pageable pageable);

    interface InventoryPartSummaryProjection {
        Long getPartId();
        String getPartCode();
        String getPartName();
        Long getSupplierId();
        String getSupplierName();
        BigDecimal getTotalQty();
        Long getLocationCount();
        LocalDateTime getLatestUpdatedAt();
    }

    interface InventoryLocationProjection {
        Long getId();
        Long getPartId();
        String getPartCode();
        String getPartName();
        String getSupplierName();
        Long getLocationId();
        String getLocationCode();
        String getWarehouseName();
        String getZoneName();
        BigDecimal getQty();
        LocalDateTime getUpdatedAt();
    }
}
