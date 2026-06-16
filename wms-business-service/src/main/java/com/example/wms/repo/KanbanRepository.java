/**
 * 本文件定义看板数据访问接口，支持父子看板查询。
 */
package com.example.wms.repo;

import com.example.wms.domain.Kanban;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KanbanRepository extends JpaRepository<Kanban, Long> {

    Optional<Kanban> findByBarcode(String barcode);

    Optional<Kanban> findByQrContent(String qrContent);

    List<Kanban> findByInboundOrderId(Long inboundOrderId);

    List<Kanban> findByParentKanbanIdOrderByBoxIndexAscIdAsc(Long parentKanbanId);

    List<Kanban> findByInboundOrderItemIdOrderByParentKanbanDescBoxIndexAscIdAsc(Long inboundOrderItemId);
}
