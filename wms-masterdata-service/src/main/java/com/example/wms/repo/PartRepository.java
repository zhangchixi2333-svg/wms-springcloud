package com.example.wms.repo;

import com.example.wms.domain.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 物料数据访问接口
 * 继承JpaRepository，提供Part实体的CRUD操作
 */
public interface PartRepository extends JpaRepository<Part, Long> {
    /**
     * 根据物料编码查询物料
     * @param partCode 物料编码
     * @return 物料Optional对象，不存在则返回空
     */
    Optional<Part> findByPartCode(String partCode);
}