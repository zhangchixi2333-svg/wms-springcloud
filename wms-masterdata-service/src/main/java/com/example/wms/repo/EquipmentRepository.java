/**
 * 本文件定义 EquipmentRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByEquipmentCode(String equipmentCode);
}
