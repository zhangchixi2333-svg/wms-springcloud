/**
 * 本文件定义 OutboundOrderItemRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.OutboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboundOrderItemRepository extends JpaRepository<OutboundOrderItem, Long> {

    List<OutboundOrderItem> findByOutboundOrderId(Long outboundOrderId);
}
