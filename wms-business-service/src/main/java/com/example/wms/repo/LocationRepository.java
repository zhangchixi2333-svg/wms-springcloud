package com.example.wms.repo;

import com.example.wms.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 库位数据访问接口
 * 继承JpaRepository，提供Location实体的CRUD操作
 */
public interface LocationRepository extends JpaRepository<Location, Long> {
    /**
     * 根据库位编码查询库位
     * @param locationCode 库位编码
     * @return 库位Optional对象，不存在则返回空
     */
    Optional<Location> findByLocationCode(String locationCode);
}