/**
 * 本文件定义 LocationRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByLocationCode(String locationCode);
}
