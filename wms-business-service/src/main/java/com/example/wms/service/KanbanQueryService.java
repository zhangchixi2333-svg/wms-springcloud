/**
 * 本文件实现箱级看板分页查询和兼容历史层级查询业务。
 */
package com.example.wms.service;

import com.example.wms.api.InventoryController.InventorySummaryView;
import com.example.wms.api.InventoryController.InventoryPartSummaryView;
import com.example.wms.api.InventoryController.InventoryTransactionView;
import com.example.wms.api.InventoryController.InventoryTransactionVersionView;
import com.example.wms.api.InventoryController.BatchFreezeKanbanRequest;
import com.example.wms.api.InventoryController.BatchRepackInboundRequest;
import com.example.wms.api.InventoryController.BatchRepackOutboundRequest;
import com.example.wms.api.InventoryController.BatchTransferKanbanRequest;
import com.example.wms.api.InventoryController.FreezeKanbanRequest;
import com.example.wms.api.InventoryController.ManualInventoryEntryRequest;
import com.example.wms.api.InventoryController.RepackInboundRequest;
import com.example.wms.api.InventoryController.RepackOutboundRequest;
import com.example.wms.api.InventoryController.TransferKanbanRequest;
import com.example.wms.api.OrderController.InboundOrderCreateRequest;
import com.example.wms.api.OrderController.InboundOrderItemRequest;
import com.example.wms.api.OrderController.InboundOrderView;
import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.api.OrderController.OutboundOrderCreateRequest;
import com.example.wms.api.OrderController.OutboundOrderItemRequest;
import com.example.wms.api.OrderController.OutboundOrderView;
import com.example.wms.api.OrderController.PageView;
import com.example.wms.api.ScanController.ScanInboundRequest;
import com.example.wms.api.ScanController.ScanInboundBatchRequest;
import com.example.wms.api.ScanController.ScanOutboundRequest;
import com.example.wms.api.ScanController.ScanResultView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.Customer;
import com.example.wms.domain.Equipment;
import com.example.wms.domain.InboundOrder;
import com.example.wms.domain.InboundOrderItem;
import com.example.wms.domain.Inventory;
import com.example.wms.domain.InventoryTransaction;
import com.example.wms.domain.Kanban;
import com.example.wms.domain.Location;
import com.example.wms.domain.OutboundAllocation;
import com.example.wms.domain.OutboundOrder;
import com.example.wms.domain.OutboundOrderItem;
import com.example.wms.domain.Part;
import com.example.wms.domain.Supplier;
import com.example.wms.repo.CustomerRepository;
import com.example.wms.repo.EquipmentRepository;
import com.example.wms.repo.InboundOrderItemRepository;
import com.example.wms.repo.InboundOrderRepository;
import com.example.wms.repo.InventoryRepository;
import com.example.wms.repo.InventoryTransactionRepository;
import com.example.wms.repo.KanbanRepository;
import com.example.wms.repo.LocationRepository;
import com.example.wms.repo.OutboundAllocationRepository;
import com.example.wms.repo.OutboundOrderItemRepository;
import com.example.wms.repo.OutboundOrderRepository;
import com.example.wms.repo.PartRepository;
import com.example.wms.repo.SupplierRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
