/**
 * 本文件实现入库单创建、查询、退回和看板生成业务。
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
public class InboundOrderService extends WmsServiceSupport {

    public InboundOrderService(WmsServiceDependencies dependencies) {
        super(dependencies);
    }

    @Transactional
    public InboundOrderView createInboundOrder(InboundOrderCreateRequest request) {
        supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException("供应商不存在"));
        if (request.items().isEmpty()) {
            throw new BusinessException("入库单至少需要一条明细");
        }

        InboundOrder order = new InboundOrder();
        order.setInboundNo(nextBusinessNo("IN"));
        order.setSupplierId(request.supplierId());
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = inboundOrderRepository.save(order);

        for (InboundOrderItemRequest itemRequest : request.items()) {
            Part part = partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("零件不存在：" + itemRequest.partId()));
            if (part.getSupplierId() != null && !part.getSupplierId().equals(request.supplierId())) {
                throw new BusinessException("零件不属于当前供应商：" + part.getPartCode());
            }
            InboundOrderItem item = new InboundOrderItem();
            PackagePlan packagePlan = resolveInboundPackagePlan(part, itemRequest);
            String warehouseZone = resolveInboundWarehouseZone(part, itemRequest.warehouseZone(), packagePlan.equipmentCode());
            item.setInboundOrderId(order.getId());
            item.setPartId(itemRequest.partId());
            item.setPlannedQty(itemRequest.plannedQty());
            item.setReceivedQty(BigDecimal.ZERO);
            item.setBoxCount(packagePlan.boxCount());
            item.setPendingRepack(isThirdPartyWarehouseZone(warehouseZone));
            item.setEquipmentCode(packagePlan.equipmentCode());
            item.setUnitPerBox(packagePlan.unitPerBox());
            item.setWarehouseZone(warehouseZone);
            inboundOrderItemRepository.save(item);
        }

        generateKanbans(order.getId());

        return toInboundOrderView(order);
    }

    public List<InboundOrderView> listInboundOrders(String status, Long supplierId, String inboundNo) {
        return toInboundOrderViews(inboundOrderRepository.findAll(
                inboundOrderSpecification(status, supplierId, inboundNo),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        ));
    }

    public PageView<InboundOrderView> listInboundOrdersPage(String status, Long supplierId, String inboundNo, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<InboundOrder> orderPage = inboundOrderRepository.findAll(
                inboundOrderSpecification(status, supplierId, inboundNo),
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageView<>(
                toInboundOrderViews(orderPage.getContent()),
                orderPage.getTotalElements(),
                normalizedPage,
                normalizedSize,
                orderPage.getTotalPages()
        );
    }

    @Transactional
    public List<KanbanView> generateKanbans(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("入库单不存在"));

        List<Kanban> existing = kanbanRepository.findByInboundOrderId(inboundOrderId);
        if (!existing.isEmpty()) {
            return existing.stream().map(this::toKanbanView).toList();
        }

        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);
        if (items.isEmpty()) {
            throw new BusinessException("入库单没有明细，不能生成看板");
        }

        int sequence = 1;
        List<Kanban> createdKanbans = new ArrayList<>();
        for (InboundOrderItem item : items) {
            BigDecimal remainingQty = defaultBigDecimal(item.getPlannedQty());
            BigDecimal unitPerBox = defaultBigDecimal(item.getUnitPerBox());
            String kanbanNoPrefix = nextBusinessNo("KB") + String.format("%02d", sequence);
            for (int boxIndex = 1; boxIndex <= item.getBoxCount(); boxIndex++) {
                BigDecimal boxQty = boxIndex == item.getBoxCount()
                        ? remainingQty
                        : unitPerBox.min(remainingQty);
                if (boxQty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("入库明细箱数与总数量不匹配，不能生成空箱看板");
                }
                Kanban kanban = createBaseKanban(order, item);
                kanban.setParentKanban(false);
                kanban.setParentKanbanId(null);
                kanban.setBoxIndex(boxIndex);
                kanban.setKanbanNo(kanbanNoPrefix + String.format("%04d", boxIndex));
                kanban.setBarcode("BC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
                kanban.setQrContent(buildKanbanQrContent(kanban.getKanbanNo(), kanban.getBarcode()));
                kanban.setQty(boxQty);
                kanban.setAvailableQty(BigDecimal.ZERO);
                kanban.setReservedQty(BigDecimal.ZERO);
                kanban.setReservedTransferQty(BigDecimal.ZERO);
                kanban.setOutboundQty(BigDecimal.ZERO);
                createdKanbans.add(kanban);
                remainingQty = nonNegative(remainingQty.subtract(boxQty));
            }
            sequence++;
        }
        List<Kanban> savedKanbans = kanbanRepository.saveAll(createdKanbans);
        return toKanbanViews(savedKanbans, false);
    }

    @Transactional
    public InboundOrderView returnInboundOrder(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("入库单不存在：" + inboundOrderId));
        if (!STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException("只有已创建且未入库的入库单可以退回");
        }

        List<Kanban> kanbans = kanbanRepository.findByInboundOrderId(inboundOrderId);
        boolean hasInboundBoxes = kanbans.stream()
                .anyMatch(kanban -> !kanban.isParentKanban() && !INBOUND_PENDING_STATUSES.contains(kanban.getStatus()));
        if (hasInboundBoxes) {
            throw new BusinessException("入库单已有箱看板发生入库或库存操作，不能退回");
        }

        kanbans.forEach(kanban -> {
            kanban.setStatus(STATUS_RETURNED);
            kanban.setAvailableQty(BigDecimal.ZERO);
            kanban.setReservedQty(BigDecimal.ZERO);
            kanban.setReservedTransferQty(BigDecimal.ZERO);
            kanban.setOutboundQty(BigDecimal.ZERO);
            kanban.setLocationId(null);
            kanbanRepository.save(kanban);
        });
        order.setStatus(STATUS_RETURNED);
        inboundOrderRepository.save(order);
        return toInboundOrderView(order);
    }
}
