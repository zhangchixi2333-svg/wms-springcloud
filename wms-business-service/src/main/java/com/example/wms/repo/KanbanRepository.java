package com.example.wms.repo;

import com.example.wms.domain.Kanban;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 看板数据访问接口
 * 继承JpaRepository，提供Kanban实体的CRUD操作
 */
public interface KanbanRepository extends JpaRepository<Kanban, Long> {
    /**
     * 根据条码查询看板
     * @param barcode 看板条码
     * @return 看板Optional对象，不存在则返回空
     */
    Optional<Kanban> findByBarcode(String barcode);

    Optional<Kanban> findByQrContent(String qrContent);
    
    /**
     * 根据入库单ID查询所有关联的看板
     * @param inboundOrderId 入库单ID
     * @return 看板列表
     */
    List<Kanban> findByInboundOrderId(Long inboundOrderId);
}
