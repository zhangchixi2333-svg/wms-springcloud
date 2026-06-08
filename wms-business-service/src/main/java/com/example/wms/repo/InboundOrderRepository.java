package com.example.wms.repo;

import com.example.wms.domain.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 入库单数据访问接口
 * 继承JpaRepository，提供InboundOrder实体的CRUD操作
 */
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {
}