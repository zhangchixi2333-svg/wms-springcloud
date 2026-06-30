/**
 * 本文件定义箱级看板数据访问接口，并保留旧层级查询用于历史数据兼容。
 */
package com.example.wms.repo;

import com.example.wms.domain.Kanban;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KanbanRepository extends JpaRepository<Kanban, Long>, JpaSpecificationExecutor<Kanban> {

    Optional<Kanban> findByBarcode(String barcode);

    Optional<Kanban> findByQrContent(String qrContent);

    Optional<Kanban> findByKanbanNoIgnoreCase(String kanbanNo);

    List<Kanban> findByInboundOrderId(Long inboundOrderId);

    @Query("select k from Kanban k where k.parentKanban = true")
    List<Kanban> findParentKanbans();

    List<Kanban> findByParentKanbanIdOrderByBoxIndexAscIdAsc(Long parentKanbanId);

    List<Kanban> findByParentKanbanIdIn(List<Long> parentKanbanIds);

    List<Kanban> findByInboundOrderItemIdOrderByParentKanbanDescBoxIndexAscIdAsc(Long inboundOrderItemId);
}
