package com.example.wms.repo;

import com.example.wms.domain.InboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 入库单行项目数据访问接口
 * 继承JpaRepository，提供InboundOrderItem实体的CRUD操作
 */
public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {
    /**
     * 根据入库单ID查询所有关联的行项目
     * @param inboundOrderId 入库单ID
     * @return 入库单行项目列表
     */
    List<InboundOrderItem> findByInboundOrderId(Long inboundOrderId);
}