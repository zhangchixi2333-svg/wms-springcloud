package com.example.wms.repo;

import com.example.wms.domain.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 出库单数据访问接口
 * 继承JpaRepository，提供OutboundOrder实体的CRUD操作
 */
public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {
}