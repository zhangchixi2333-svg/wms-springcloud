/**
 * 本文件定义出库分配记录的数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.OutboundAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OutboundAllocationRepository extends JpaRepository<OutboundAllocation, Long> {

    List<OutboundAllocation> findByOutboundOrderId(Long outboundOrderId);

    List<OutboundAllocation> findByOutboundOrderIdAndKanbanIdIn(Long outboundOrderId, Collection<Long> kanbanIds);

    List<OutboundAllocation> findByKanbanIdIn(Collection<Long> kanbanIds);

    List<OutboundAllocation> findByOutboundOrderItemId(Long outboundOrderItemId);

    List<OutboundAllocation> findByOutboundOrderItemIdIn(Collection<Long> outboundOrderItemIds);
}
