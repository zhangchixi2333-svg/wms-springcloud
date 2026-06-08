package com.example.wms.repo;

import com.example.wms.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 设备数据访问接口
 * 继承JpaRepository，提供Equipment实体的CRUD操作
 */
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    /**
     * 根据设备编码查询设备
     * @param equipmentCode 设备编码
     * @return 设备Optional对象，不存在则返回空
     */
    Optional<Equipment> findByEquipmentCode(String equipmentCode);
}