/**
 * 本文件定义 OutboundOrderRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long>, JpaSpecificationExecutor<OutboundOrder> {

    Optional<OutboundOrder> findByOutboundNoIgnoreCase(String outboundNo);
}
