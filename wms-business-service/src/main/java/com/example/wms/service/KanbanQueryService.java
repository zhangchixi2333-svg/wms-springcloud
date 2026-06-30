/**
 * 本文件实现箱级看板分页查询和兼容历史层级查询业务。
 */
package com.example.wms.service;

import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.api.OrderController.PageView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.Kanban;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KanbanQueryService extends WmsServiceSupport {

    public KanbanQueryService(WmsServiceDependencies dependencies) {
        super(dependencies);
    }

    public List<KanbanView> listKanbans(String status,
                                        String inboundNo,
                                        String outboundNo,
                                        String kanbanNo,
                                        Long supplierId,
                                        String partCode,
                                        String warehouseName,
                                        String zoneName,
                                        String warehouseType,
                                        boolean includeChildren) {
        return toKanbanViews(kanbanRepository.findAll(
                kanbanSpecification(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode, warehouseName, zoneName, warehouseType, includeChildren),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        ), includeChildren);
    }

    public PageView<KanbanView> listKanbansPage(String status,
                                                String inboundNo,
                                                String outboundNo,
                                                String kanbanNo,
                                                Long supplierId,
                                                String partCode,
                                                String warehouseName,
                                                String zoneName,
                                                String warehouseType,
                                                boolean includeChildren,
                                                int page,
                                                int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<Kanban> kanbanPage = kanbanRepository.findAll(
                kanbanSpecification(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode, warehouseName, zoneName, warehouseType, includeChildren),
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageView<>(
                toKanbanViews(kanbanPage.getContent(), includeChildren),
                kanbanPage.getTotalElements(),
                normalizedPage,
                normalizedSize,
                kanbanPage.getTotalPages()
        );
    }

    public List<KanbanView> listKanbanChildren(Long parentId) {
        Kanban parent = kanbanRepository.findById(parentId)
                .orElseThrow(() -> new NotFoundException("兼容看板不存在：" + parentId));
        if (!parent.isParentKanban()) {
            throw new BusinessException("当前看板就是箱级看板，没有下级箱看板");
        }
        return toKanbanViews(kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(parentId), false);
    }
}
