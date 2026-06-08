package com.example.wms.repo;

import com.example.wms.domain.OutboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 出库单行项目数据访问接口
 * 继承JpaRepository，提供OutboundOrderItem实体的CRUD操作
 */
public interface OutboundOrderItemRepository extends JpaRepository<OutboundOrderItem, Long> {
    /**
     * 根据出库单ID查询所有关联的行项目
     * @param outboundOrderId 出库单ID
     * @return 出库单行项目列表
     */
    List<OutboundOrderItem> findByOutboundOrderId(Long outboundOrderId);
}