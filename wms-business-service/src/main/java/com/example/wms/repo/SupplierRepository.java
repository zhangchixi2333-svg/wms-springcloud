package com.example.wms.repo;

import com.example.wms.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 供应商数据访问接口
 * 继承JpaRepository，提供Supplier实体的CRUD操作
 */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    /**
     * 根据供应商编码查询供应商
     * @param supplierCode 供应商编码
     * @return 供应商Optional对象，不存在则返回空
     */
    Optional<Supplier> findBySupplierCode(String supplierCode);
}