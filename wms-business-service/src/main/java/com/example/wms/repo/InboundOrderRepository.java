/**
 * 本文件定义 InboundOrderRepository 数据访问接口。
 */
package com.example.wms.repo;

import com.example.wms.domain.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {
}
