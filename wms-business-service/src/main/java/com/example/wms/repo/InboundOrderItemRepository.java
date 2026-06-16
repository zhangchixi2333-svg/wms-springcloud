/**
 * 本文件定义 InboundOrderItemRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.InboundOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundOrderItemRepository extends JpaRepository<InboundOrderItem, Long> {

    List<InboundOrderItem> findByInboundOrderId(Long inboundOrderId);
}
