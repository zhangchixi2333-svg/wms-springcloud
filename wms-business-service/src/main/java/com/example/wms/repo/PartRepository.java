/**
 * 本文件定义 PartRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {

    Optional<Part> findByPartCode(String partCode);

    java.util.List<Part> findBySupplierId(Long supplierId);
}
