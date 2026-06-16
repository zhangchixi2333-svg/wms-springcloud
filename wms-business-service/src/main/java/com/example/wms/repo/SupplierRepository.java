/**
 * 本文件定义 SupplierRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findBySupplierCode(String supplierCode);
}
