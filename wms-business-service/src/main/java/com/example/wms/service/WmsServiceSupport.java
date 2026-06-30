/**
 * 本文件提供 WMS 业务服务共享依赖、状态机辅助方法和视图转换逻辑。
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
import com.example.wms.domain.InventoryOperationOrder;
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
import com.example.wms.repo.InventoryOperationOrderRepository;
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

public abstract class WmsServiceSupport {

    protected static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    protected static final String STATUS_CREATED = "CREATED";
    protected static final String STATUS_WAIT_SCAN = "WAIT_SCAN";
    protected static final String STATUS_INBOUND = "INBOUND";
    protected static final String STATUS_OUTBOUND = "OUTBOUND";
    protected static final String STATUS_ALLOCATED = "ALLOCATED";
    protected static final String STATUS_FROZEN = "FROZEN";
    protected static final String STATUS_PARTIAL = "PARTIAL";
    protected static final String STATUS_PARTIAL_INBOUND = "PARTIAL_INBOUND";
    protected static final String STATUS_PARTIAL_OUTBOUND = "PARTIAL_OUTBOUND";
    protected static final String STATUS_REPACK_OUTBOUND = "REPACK_OUTBOUND";
    protected static final String STATUS_REPACK_INBOUND = "REPACK_INBOUND";
    protected static final String STATUS_THIRD_PARTY_STOCK = "THIRD_PARTY_STOCK";
    protected static final String STATUS_CONSUMED = "CONSUMED";
    protected static final String STATUS_CANCELLED = "CANCELLED";
    protected static final String STATUS_RETURNED = "RETURNED";
    protected static final String CATEGORY_OUTSOURCED = "OUTSOURCED";
    protected static final Set<String> INBOUND_PENDING_STATUSES = Set.of(STATUS_WAIT_SCAN, STATUS_CREATED);
    protected static final Set<String> OUTBOUND_PENDING_STATUSES = Set.of(STATUS_ALLOCATED, STATUS_INBOUND, STATUS_PARTIAL_OUTBOUND);
    protected static final Set<String> NOT_FULLY_INBOUND_STATUSES = Set.of(STATUS_WAIT_SCAN, STATUS_CREATED, STATUS_PARTIAL, STATUS_PARTIAL_INBOUND);
    protected static final Set<String> INBOUND_LIKE_STATUSES = Set.of(STATUS_INBOUND, STATUS_FROZEN, STATUS_REPACK_OUTBOUND, STATUS_REPACK_INBOUND, STATUS_THIRD_PARTY_STOCK);
    protected static final Set<String> OWN_STOCK_OPERABLE_STATUSES = Set.of(STATUS_INBOUND, STATUS_PARTIAL_OUTBOUND);
    protected static final Set<String> FREEZABLE_STATUSES = Set.of(STATUS_INBOUND, STATUS_PARTIAL_OUTBOUND, STATUS_THIRD_PARTY_STOCK);

    protected final SupplierRepository supplierRepository;
    protected final CustomerRepository customerRepository;
    protected final EquipmentRepository equipmentRepository;
    protected final PartRepository partRepository;
    protected final LocationRepository locationRepository;
    protected final InboundOrderRepository inboundOrderRepository;
    protected final InboundOrderItemRepository inboundOrderItemRepository;
    protected final OutboundOrderRepository outboundOrderRepository;
    protected final OutboundOrderItemRepository outboundOrderItemRepository;
    protected final OutboundAllocationRepository outboundAllocationRepository;
    protected final KanbanRepository kanbanRepository;
    protected final InventoryRepository inventoryRepository;
    protected final InventoryTransactionRepository inventoryTransactionRepository;
    protected final InventoryOperationOrderRepository inventoryOperationOrderRepository;

    protected WmsServiceSupport(WmsServiceDependencies dependencies) {
        this.supplierRepository = dependencies.supplierRepository;
        this.customerRepository = dependencies.customerRepository;
        this.equipmentRepository = dependencies.equipmentRepository;
        this.partRepository = dependencies.partRepository;
        this.locationRepository = dependencies.locationRepository;
        this.inboundOrderRepository = dependencies.inboundOrderRepository;
        this.inboundOrderItemRepository = dependencies.inboundOrderItemRepository;
        this.outboundOrderRepository = dependencies.outboundOrderRepository;
        this.outboundOrderItemRepository = dependencies.outboundOrderItemRepository;
        this.outboundAllocationRepository = dependencies.outboundAllocationRepository;
        this.kanbanRepository = dependencies.kanbanRepository;
        this.inventoryRepository = dependencies.inventoryRepository;
        this.inventoryTransactionRepository = dependencies.inventoryTransactionRepository;
        this.inventoryOperationOrderRepository = dependencies.inventoryOperationOrderRepository;
    }


    protected Kanban findOperableStockKanban(String barcode) {
        Kanban kanban = findKanbanByScanCode(barcode);
        validateOwnStockOperation(kanban, "库存操作");
        return kanban;
    }

    protected List<Kanban> resolveDistinctKanbans(List<String> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) {
            throw new BusinessException("请至少选择一个箱级看板");
        }
        List<Kanban> kanbans = new ArrayList<>();
        Set<Long> selectedIds = new java.util.LinkedHashSet<>();
        for (String barcode : barcodes) {
            Kanban kanban = findKanbanByScanCode(barcode);
            if (kanban.isParentKanban()) {
                throw new BusinessException("当前业务只允许选择箱级看板：" + kanban.getKanbanNo());
            }
            if (selectedIds.add(kanban.getId())) {
                kanbans.add(kanban);
            }
        }
        if (kanbans.isEmpty()) {
            throw new BusinessException("请至少选择一个箱级看板");
        }
        return kanbans;
    }

    protected void requireOwnInboundStockKanban(Kanban kanban, String actionName) {
        validateOwnStockOperation(kanban, actionName);
    }

    protected void validateOwnStockOperation(Kanban kanban, String actionName) {
        if (kanban.isFrozen()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已封存，不能执行" + actionName);
        }
        if (!OWN_STOCK_OPERABLE_STATUSES.contains(kanban.getStatus())) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 必须是已入库或部分出库状态，不能执行" + actionName);
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有库存库位，不能执行" + actionName);
        }
        Location sourceLocation = locationRepository.findById(kanban.getLocationId())
                .orElseThrow(() -> new NotFoundException("来源库位不存在"));
        requireWarehouseType(sourceLocation, "OWN", actionName + "只能从自有仓库发起");
        if (kanbanFreeQty(kanban).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有可操作数量，不能执行" + actionName);
        }
    }

    protected void requireThirdPartyStockKanban(Kanban kanban, String actionName) {
        if (kanban.isFrozen()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已封存，不能执行" + actionName);
        }
        if (!STATUS_THIRD_PARTY_STOCK.equals(kanban.getStatus())) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 不是第三方在库状态，不能执行" + actionName);
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有第三方库位，不能执行" + actionName);
        }
        Location sourceLocation = locationRepository.findById(kanban.getLocationId())
                .orElseThrow(() -> new NotFoundException("第三方库位不存在"));
        requireWarehouseType(sourceLocation, "THIRD_PARTY", actionName + "必须从第三方仓库返回");
        if (kanbanFreeQty(kanban).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有可操作数量，不能执行" + actionName);
        }
    }

    protected void requireFreezeActionAllowed(Kanban kanban, boolean frozen) {
        if (kanban.isParentKanban()) {
            throw new BusinessException("当前业务只允许封存箱级看板：" + kanban.getKanbanNo());
        }
        if (frozen) {
            if (defaultBigDecimal(kanban.getReservedQty()).compareTo(BigDecimal.ZERO) > 0
                    || defaultBigDecimal(kanban.getReservedTransferQty()).compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已被出库或转移锁定，不能封存");
            }
            if (!FREEZABLE_STATUSES.contains(kanban.getStatus()) || kanban.isFrozen()) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 只有已入库、部分出库或第三方在库且未封存时可以封存");
            }
            if (kanban.getLocationId() == null) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有库存库位，不能封存");
            }
        } else if (!STATUS_FROZEN.equals(kanban.getStatus()) || !kanban.isFrozen()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 只有已封存状态可以解封");
        }
    }

    protected Kanban applyFreezeAction(Kanban kanban, boolean frozen, String operationNo, String remark) {
        Long locationId = kanban.getLocationId();
        String sourceStatus = kanban.getStatus();
        if (frozen) {
            kanban.setFrozenPreviousStatus(sourceStatus);
            kanban.setFrozen(true);
            kanban.setStatus(STATUS_FROZEN);
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, locationId, BigDecimal.ZERO, "FREEZE", operationNo, remark);
            saveOperationOrder(operationNo, "FREEZE", operationNo, kanban, null, locationId, locationId, kanban.getPartId(), BigDecimal.ZERO, sourceStatus, kanban.getStatus(), remark);
        } else {
            String targetStatus = resolveUnfrozenStatus(kanban);
            kanban.setFrozen(false);
            kanban.setStatus(targetStatus);
            kanban.setFrozenPreviousStatus(null);
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, locationId, BigDecimal.ZERO, "UNFREEZE", operationNo, remark);
            saveOperationOrder(operationNo, "UNFREEZE", operationNo, kanban, null, locationId, locationId, kanban.getPartId(), BigDecimal.ZERO, sourceStatus, kanban.getStatus(), remark);
        }
        return kanban;
    }

    protected String resolveUnfrozenStatus(Kanban kanban) {
        String previousStatus = normalize(kanban.getFrozenPreviousStatus());
        if (previousStatus != null && FREEZABLE_STATUSES.contains(previousStatus)) {
            return previousStatus;
        }
        if ("THIRD_PARTY".equals(resolveWarehouseType(kanban.getLocationId()))) {
            return STATUS_THIRD_PARTY_STOCK;
        }
        if (defaultBigDecimal(kanban.getOutboundQty()).compareTo(BigDecimal.ZERO) > 0
                && kanbanAvailableQty(kanban).compareTo(BigDecimal.ZERO) > 0) {
            return STATUS_PARTIAL_OUTBOUND;
        }
        return STATUS_INBOUND;
    }

    protected String resolveWarehouseType(Long locationId) {
        if (locationId == null) {
            return "OWN";
        }
        return locationRepository.findById(locationId)
                .map(Location::getWarehouseType)
                .map(this::normalizeWarehouseType)
                .orElse("OWN");
    }

    protected Kanban createBaseKanban(InboundOrder order, InboundOrderItem item) {
        Kanban kanban = new Kanban();
        kanban.setInboundOrderId(order.getId());
        kanban.setInboundOrderItemId(item.getId());
        kanban.setPartId(item.getPartId());
        kanban.setSupplierId(order.getSupplierId());
        kanban.setBatchNo("BATCH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd")));
        kanban.setStatus("WAIT_SCAN");
        kanban.setFrozen(false);
        kanban.setCreatedAt(LocalDateTime.now());
        kanban.setParentKanbanId(null);
        return kanban;
    }

    protected Kanban moveOrSplitKanban(Kanban source,
                                     Location targetLocation,
                                     BigDecimal requestedQty,
                                     String targetStatus,
                                     String sourceBusinessType,
                                     String targetBusinessType,
                                     String transferNo,
                                     String remark) {
        if (source.isFrozen()) {
            throw new BusinessException("看板已封存，不能迁移");
        }
        if (source.getLocationId() == null) {
            throw new BusinessException("看板没有库存库位，不能迁移");
        }
        if (targetLocation.getId().equals(source.getLocationId())) {
            throw new BusinessException("目标库位与当前库位相同，无需迁移");
        }

        BigDecimal freeQty = kanbanFreeQty(source);
        BigDecimal moveQty = requestedQty == null ? freeQty : requestedQty;
        if (moveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("迁移数量必须大于 0");
        }
        if (moveQty.compareTo(freeQty) > 0) {
            throw new BusinessException("迁移数量不能大于当前可迁移数量：" + freeQty.toPlainString());
        }

        Long sourceLocationId = source.getLocationId();
        String sourceStatusBefore = source.getStatus();
        String sourceKanbanNo = source.getKanbanNo();
        String sourceBarcode = source.getBarcode();
        moveInventory(source.getPartId(), sourceLocationId, moveQty.negate());
        moveInventory(source.getPartId(), targetLocation.getId(), moveQty);
        saveTransaction(source, sourceLocationId, moveQty.negate(), sourceBusinessType, transferNo, remark);

        Kanban targetKanban;
        BigDecimal sourceAvailableBefore = kanbanAvailableQty(source);
        boolean wholeBoxMove = moveQty.compareTo(sourceAvailableBefore) == 0
                && defaultBigDecimal(source.getOutboundQty()).compareTo(BigDecimal.ZERO) == 0
                && defaultBigDecimal(source.getReservedQty()).compareTo(BigDecimal.ZERO) == 0
                && defaultBigDecimal(source.getReservedTransferQty()).compareTo(BigDecimal.ZERO) == 0;
        if (wholeBoxMove) {
            source.setLocationId(targetLocation.getId());
            source.setStatus(targetStatus);
            source.setAvailableQty(nonNegative(defaultBigDecimal(source.getAvailableQty())));
            source.setReservedTransferQty(BigDecimal.ZERO);
            appendTransferOrderNo(source, transferNo);
            source.setFrozen(false);
            source.setInboundTime(LocalDateTime.now());
            targetKanban = kanbanRepository.save(source);
        } else {
            BigDecimal remainingQty = nonNegative(defaultBigDecimal(source.getQty()).subtract(moveQty));
            BigDecimal remainingAvailableQty = nonNegative(sourceAvailableBefore.subtract(moveQty));
            source.setQty(remainingQty);
            source.setAvailableQty(remainingAvailableQty);
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                source.setStatus(STATUS_CONSUMED);
            } else if (remainingAvailableQty.compareTo(BigDecimal.ZERO) <= 0) {
                source.setStatus(STATUS_OUTBOUND);
            } else if (defaultBigDecimal(source.getOutboundQty()).compareTo(BigDecimal.ZERO) > 0) {
                source.setStatus(STATUS_PARTIAL_OUTBOUND);
            }
            source = kanbanRepository.save(source);

            targetKanban = copyKanbanForSplit(source, targetLocation, moveQty, targetStatus);
            appendTransferOrderNo(source, transferNo);
            appendTransferOrderNo(targetKanban, transferNo);
            kanbanRepository.save(source);
            targetKanban = kanbanRepository.save(targetKanban);
        }

        saveTransaction(targetKanban, targetLocation.getId(), moveQty, targetBusinessType, transferNo, remark);
        saveOperationOrder(
                transferNo,
                operationTypeFromBusinessType(sourceBusinessType),
                transferNo,
                sourceKanbanNo,
                targetKanban.getKanbanNo(),
                sourceBarcode,
                targetKanban.getBarcode(),
                targetKanban.getPartId(),
                sourceLocationId,
                targetLocation.getId(),
                moveQty,
                sourceStatusBefore,
                targetKanban.getStatus(),
                remark
        );
        return targetKanban;
    }

    protected Kanban copyKanbanForSplit(Kanban source, Location targetLocation, BigDecimal qty, String status) {
        Kanban kanban = new Kanban();
        kanban.setKanbanNo(nextBusinessNo("KB"));
        kanban.setBarcode("BC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        kanban.setQrContent(buildKanbanQrContent(kanban.getKanbanNo(), kanban.getBarcode()));
        kanban.setInboundOrderId(source.getInboundOrderId());
        kanban.setInboundOrderItemId(source.getInboundOrderItemId());
        kanban.setParentKanbanId(null);
        kanban.setParentKanban(false);
        kanban.setBoxIndex(source.getBoxIndex());
        kanban.setPartId(source.getPartId());
        kanban.setSupplierId(source.getSupplierId());
        kanban.setBatchNo(source.getBatchNo());
        kanban.setQty(qty);
        kanban.setAvailableQty(qty);
        kanban.setReservedQty(BigDecimal.ZERO);
        kanban.setReservedTransferQty(BigDecimal.ZERO);
        kanban.setOutboundQty(BigDecimal.ZERO);
        kanban.setStatus(status);
        kanban.setFrozen(false);
        kanban.setLocationId(targetLocation.getId());
        kanban.setSourceKanbanId(source.getId());
        kanban.setCreatedAt(LocalDateTime.now());
        kanban.setInboundTime(LocalDateTime.now());
        return kanban;
    }

    protected List<Kanban> resolveScanTargets(Kanban kanban) {
        if (kanban.isParentKanban()) {
            return kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(kanban.getId());
        }
        return List.of(kanban);
    }

    protected List<Kanban> resolveInboundScanTargets(Kanban kanban) {
        List<Kanban> targets = resolveScanTargets(kanban).stream()
                .filter(item -> INBOUND_PENDING_STATUSES.contains(item.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            if (!kanban.isParentKanban()) {
                ensureInboundAllowed(kanban);
            }
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有待入库箱子");
        }
        return targets;
    }

    protected List<Kanban> resolveInboundOrderScanTargets(InboundOrder order) {
        List<Kanban> targets = kanbanRepository.findByInboundOrderId(order.getId()).stream()
                .filter(kanban -> !kanban.isParentKanban())
                .filter(kanban -> INBOUND_PENDING_STATUSES.contains(kanban.getStatus()))
                .sorted(this::compareKanbanFifo)
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException("入库单 " + order.getInboundNo() + " 没有待入库箱子");
        }
        return targets;
    }

    protected List<Kanban> resolveOutboundScanTargets(String scanCode, OutboundOrder order) {
        if (findOutboundOrderByScanCode(scanCode)
                .map(scannedOrder -> Objects.equals(scannedOrder.getId(), order.getId()))
                .orElse(false)) {
            List<OutboundAllocation> orderAllocations = outboundAllocationRepository.findByOutboundOrderId(order.getId());
            Set<Long> allocatedKanbanIds = orderAllocations.stream()
                    .filter(allocation -> STATUS_ALLOCATED.equals(allocation.getStatus()))
                    .map(OutboundAllocation::getKanbanId)
                    .collect(Collectors.toSet());
            List<Kanban> targets = kanbanRepository.findAllById(allocatedKanbanIds).stream()
                    .filter(item -> Set.of(STATUS_ALLOCATED, STATUS_INBOUND, STATUS_PARTIAL_OUTBOUND).contains(item.getStatus()))
                    .sorted(this::compareKanbanFifo)
                    .toList();
            if (targets.isEmpty()) {
                throw new BusinessException(resolveOutboundOrderNoPendingReason(order, orderAllocations));
            }
            return targets;
        }

        Kanban kanban = findKanbanByScanCode(scanCode);
        List<OutboundAllocation> orderAllocations = outboundAllocationRepository.findByOutboundOrderId(order.getId());
        Set<Long> allocatedKanbanIds = orderAllocations.stream()
                .filter(allocation -> STATUS_ALLOCATED.equals(allocation.getStatus()))
                .map(OutboundAllocation::getKanbanId)
                .collect(Collectors.toSet());
        List<Long> scannedKanbanIds = resolveScanTargets(kanban).stream().map(Kanban::getId).toList();
        List<Kanban> targets = resolveScanTargets(kanban).stream()
                .filter(item -> allocatedKanbanIds.contains(item.getId()))
                .filter(item -> Set.of(STATUS_ALLOCATED, STATUS_INBOUND, STATUS_PARTIAL_OUTBOUND).contains(item.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException(resolveOutboundKanbanNoPendingReason(order, kanban, scannedKanbanIds, orderAllocations));
        }
        return targets;
    }

    protected String resolveOutboundOrderNoPendingReason(OutboundOrder order, List<OutboundAllocation> allocations) {
        if (allocations.isEmpty()) {
            return "出库单 " + order.getOutboundNo() + " 没有库存分配记录，请重新创建出库单";
        }
        BigDecimal allocatedQty = allocations.stream()
                .map(OutboundAllocation::getAllocatedQty)
                .map(this::defaultBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outboundQty = allocations.stream()
                .map(OutboundAllocation::getOutboundQty)
                .map(this::defaultBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedQty.compareTo(BigDecimal.ZERO) > 0 && outboundQty.compareTo(allocatedQty) >= 0) {
            return "出库单 " + order.getOutboundNo() + " 已全部出库完成，分配数量 "
                    + allocatedQty.toPlainString() + "，已出库 " + outboundQty.toPlainString() + "，请勿重复扫码";
        }
        long cancelledCount = allocations.stream().filter(item -> STATUS_CANCELLED.equals(item.getStatus())).count();
        if (cancelledCount == allocations.size()) {
            return "出库单 " + order.getOutboundNo() + " 已取消，锁定库存已释放，不能扫码出库";
        }
        return "出库单 " + order.getOutboundNo() + " 当前没有待出库分配，分配数量 "
                + allocatedQty.toPlainString() + "，已出库 " + outboundQty.toPlainString();
    }

    protected String resolveOutboundKanbanNoPendingReason(OutboundOrder order, Kanban scannedKanban, List<Long> scannedKanbanIds, List<OutboundAllocation> orderAllocations) {
        List<OutboundAllocation> matchedAllocations = orderAllocations.stream()
                .filter(allocation -> scannedKanbanIds.contains(allocation.getKanbanId()))
                .toList();
        if (matchedAllocations.isEmpty()) {
            List<OutboundAllocation> historyAllocations = outboundAllocationRepository.findByKanbanIdIn(scannedKanbanIds);
            List<String> historyOrderNos = historyAllocations.stream()
                    .map(allocation -> outboundOrderRepository.findById(allocation.getOutboundOrderId()).orElse(null))
                    .filter(Objects::nonNull)
                    .map(OutboundOrder::getOutboundNo)
                    .distinct()
                    .toList();
            if (!historyOrderNos.isEmpty()) {
                return "看板 " + scannedKanban.getKanbanNo() + " 没有绑定到当前出库单 " + order.getOutboundNo()
                        + " 的待出库分配；该看板历史绑定出库单：" + String.join("、", historyOrderNos);
            }
            return "看板 " + scannedKanban.getKanbanNo() + " 没有绑定到当前出库单 " + order.getOutboundNo() + "，请先在出库管理创建出库单";
        }

        BigDecimal allocatedQty = matchedAllocations.stream()
                .map(OutboundAllocation::getAllocatedQty)
                .map(this::defaultBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outboundQty = matchedAllocations.stream()
                .map(OutboundAllocation::getOutboundQty)
                .map(this::defaultBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocatedQty.compareTo(BigDecimal.ZERO) > 0 && outboundQty.compareTo(allocatedQty) >= 0) {
            return "看板 " + scannedKanban.getKanbanNo() + " 在出库单 " + order.getOutboundNo()
                    + " 中已全部出库完成，分配数量 " + allocatedQty.toPlainString()
                    + "，已出库 " + outboundQty.toPlainString() + "，请勿重复扫码";
        }
        long cancelledCount = matchedAllocations.stream().filter(item -> STATUS_CANCELLED.equals(item.getStatus())).count();
        if (cancelledCount == matchedAllocations.size()) {
            return "看板 " + scannedKanban.getKanbanNo() + " 在出库单 " + order.getOutboundNo()
                    + " 的分配已取消，不能扫码出库";
        }
        return "看板 " + scannedKanban.getKanbanNo() + " 在出库单 " + order.getOutboundNo()
                + " 中没有待出库数量，分配数量 " + allocatedQty.toPlainString()
                + "，已出库 " + outboundQty.toPlainString();
    }

    protected Location resolveInboundLocation(String locationCode, Kanban scannedKanban, List<Kanban> targets) {
        String normalizedLocationCode = normalize(locationCode);
        if (normalizedLocationCode != null) {
            return locationRepository.findByLocationCode(normalizedLocationCode)
                    .orElseThrow(() -> new NotFoundException("入库库位不存在：" + normalizedLocationCode));
        }

        Kanban reference = targets.isEmpty() ? scannedKanban : targets.get(0);
        InboundOrderItem item = inboundOrderItemRepository.findById(reference.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("入库明细不存在：" + reference.getInboundOrderItemId()));
        String[] plannedZone = splitWarehouseZone(item.getWarehouseZone());
        List<Location> matchedLocations = locationRepository.findAll().stream()
                .filter(location -> plannedZone[0].equals(location.getWarehouseName()) && plannedZone[1].equals(location.getZoneName()))
                .sorted(Comparator.comparing(Location::getLocationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        if (!matchedLocations.isEmpty()) {
            return matchedLocations.get(0);
        }
        throw new BusinessException("未找到看板计划库区对应的库位：" + plannedZone[0] + " / " + plannedZone[1]);
    }

    protected Location resolveInboundLocationForKanban(String locationCode, Kanban kanban) {
        String normalizedLocationCode = normalize(locationCode);
        if (normalizedLocationCode != null) {
            return locationRepository.findByLocationCode(normalizedLocationCode)
                    .orElseThrow(() -> new NotFoundException("入库库位不存在：" + normalizedLocationCode));
        }
        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("入库明细不存在：" + kanban.getInboundOrderItemId()));
        Part part = partRepository.findById(kanban.getPartId())
                .orElseThrow(() -> new NotFoundException("零件不存在：" + kanban.getPartId()));
        String warehouseZone = resolveInboundWarehouseZone(part, item.getWarehouseZone(), item.getEquipmentCode());
        item.setWarehouseZone(warehouseZone);
        item.setPendingRepack(isThirdPartyWarehouseZone(warehouseZone));
        inboundOrderItemRepository.save(item);
        return resolveLocationByWarehouseZone(warehouseZone);
    }

    protected String resolveInboundWarehouseZone(Part part, String requestedWarehouseZone, String equipmentCode) {
        String expectedType = expectedWarehouseType(part);
        String normalizedRequested = normalize(requestedWarehouseZone);
        if (normalizedRequested != null) {
            return normalizedRequested;
        }

        String equipmentWarehouseZone = warehouseZoneForEquipment(equipmentCode);
        if (equipmentWarehouseZone != null && warehouseZoneMatchesType(equipmentWarehouseZone, expectedType)) {
            return equipmentWarehouseZone;
        }

        String partEquipmentWarehouseZone = warehouseZoneForEquipment(part == null ? null : part.getDefaultEquipmentCode());
        if (partEquipmentWarehouseZone != null && warehouseZoneMatchesType(partEquipmentWarehouseZone, expectedType)) {
            return partEquipmentWarehouseZone;
        }

        return firstWarehouseZoneByType(expectedType);
    }

    protected boolean isOutsourcedPart(Part part) {
        return part != null && CATEGORY_OUTSOURCED.equalsIgnoreCase(defaultString(part.getCategoryCode()));
    }

    protected String expectedWarehouseType(Part part) {
        return isOutsourcedPart(part) ? "THIRD_PARTY" : "OWN";
    }

    protected boolean warehouseZoneMatchesType(String warehouseZone, String expectedType) {
        String[] plannedZone = splitWarehouseZone(warehouseZone);
        return locationRepository.findAll().stream()
                .anyMatch(location -> plannedZone[0].equals(location.getWarehouseName())
                        && plannedZone[1].equals(location.getZoneName())
                        && expectedType.equals(normalizeWarehouseType(location.getWarehouseType())));
    }

    protected String warehouseZoneForEquipment(String equipmentCode) {
        String normalizedEquipmentCode = normalize(equipmentCode);
        if (normalizedEquipmentCode == null) {
            return null;
        }
        return equipmentRepository.findByEquipmentCode(normalizedEquipmentCode)
                .filter(equipment -> !isBlank(equipment.getWarehouseName()) && !isBlank(equipment.getZoneName()))
                .map(equipment -> equipment.getWarehouseName() + " / " + equipment.getZoneName())
                .orElse(null);
    }

    protected String firstWarehouseZoneByType(String warehouseType) {
        return locationRepository.findAll().stream()
                .filter(location -> warehouseType.equals(normalizeWarehouseType(location.getWarehouseType())))
                .sorted(Comparator.comparing(Location::getLocationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(location -> location.getWarehouseName() + " / " + location.getZoneName())
                .findFirst()
                .orElseThrow(() -> new BusinessException(("THIRD_PARTY".equals(warehouseType) ? "第三方" : "自有") + "仓库没有可用库区"));
    }

    protected Location resolveLocationByWarehouseZone(String warehouseZone) {
        String[] plannedZone = splitWarehouseZone(warehouseZone);
        return locationRepository.findAll().stream()
                .filter(location -> plannedZone[0].equals(location.getWarehouseName()) && plannedZone[1].equals(location.getZoneName()))
                .sorted(Comparator.comparing(Location::getLocationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未找到看板计划库区对应的库位：" + plannedZone[0] + " / " + plannedZone[1]));
    }

    protected String resolveOutboundOrderNo(String requestedOutboundOrderNo, Kanban scannedKanban) {
        String normalized = normalize(requestedOutboundOrderNo);
        if (normalized != null) {
            return normalized;
        }

        List<Long> kanbanIds = resolveScanTargets(scannedKanban).stream().map(Kanban::getId).toList();
        List<OutboundAllocation> allocations = outboundAllocationRepository.findByKanbanIdIn(kanbanIds);
        List<String> boundOrderNos = allocations.stream()
                .filter(allocation -> STATUS_ALLOCATED.equals(allocation.getStatus()))
                .map(allocation -> outboundOrderRepository.findById(allocation.getOutboundOrderId()).orElse(null))
                .filter(Objects::nonNull)
                .map(OutboundOrder::getOutboundNo)
                .distinct()
                .toList();

        if (boundOrderNos.size() == 1) {
            return boundOrderNos.get(0);
        }
        if (boundOrderNos.size() > 1) {
            throw new BusinessException("该看板绑定了多个出库单，请先选择出库单后再扫码：" + String.join("、", boundOrderNos));
        }
        if (!allocations.isEmpty()) {
            throw new BusinessException(resolveOutboundKanbanHistoryReason(scannedKanban, allocations));
        }
        throw new BusinessException("看板 " + scannedKanban.getKanbanNo() + " 未绑定出库单，请先在出库管理创建出库单");
    }

    protected String resolveOutboundKanbanHistoryReason(Kanban scannedKanban, List<OutboundAllocation> allocations) {
        Map<Long, List<OutboundAllocation>> allocationsByOrderId = allocations.stream()
                .collect(Collectors.groupingBy(OutboundAllocation::getOutboundOrderId));
        List<String> details = allocationsByOrderId.entrySet().stream()
                .map(entry -> {
                    OutboundOrder order = outboundOrderRepository.findById(entry.getKey()).orElse(null);
                    String outboundNo = order == null ? String.valueOf(entry.getKey()) : order.getOutboundNo();
                    List<OutboundAllocation> orderAllocations = entry.getValue();
                    BigDecimal allocatedQty = orderAllocations.stream()
                            .map(OutboundAllocation::getAllocatedQty)
                            .map(this::defaultBigDecimal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal outboundQty = orderAllocations.stream()
                            .map(OutboundAllocation::getOutboundQty)
                            .map(this::defaultBigDecimal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long cancelledCount = orderAllocations.stream().filter(item -> STATUS_CANCELLED.equals(item.getStatus())).count();
                    String stateText;
                    if (cancelledCount == orderAllocations.size()) {
                        stateText = "已取消";
                    } else if (allocatedQty.compareTo(BigDecimal.ZERO) > 0 && outboundQty.compareTo(allocatedQty) >= 0) {
                        stateText = "已全部出库";
                    } else {
                        stateText = "没有待出库分配";
                    }
                    return outboundNo + "（" + stateText + "，分配 " + allocatedQty.toPlainString() + "，已出 " + outboundQty.toPlainString() + "）";
                })
                .toList();
        return "看板 " + scannedKanban.getKanbanNo()
                + " 当前没有待出库绑定；历史出库记录：" + String.join("；", details)
                + "。如需继续出库，请重新创建出库单。";
    }

    protected OutboundOrder resolveOutboundOrderForScan(String scanCode, String requestedOutboundOrderNo) {
        java.util.Optional<OutboundOrder> scannedOrder = findOutboundOrderByScanCode(scanCode);
        if (scannedOrder.isPresent()) {
            String requested = normalize(requestedOutboundOrderNo);
            if (requested != null && !requested.equalsIgnoreCase(scannedOrder.get().getOutboundNo())) {
                throw new BusinessException("扫码出库单与当前选择的出库单不一致：" + scannedOrder.get().getOutboundNo());
            }
            return scannedOrder.get();
        }

        String requested = normalize(requestedOutboundOrderNo);
        if (requested != null) {
            return outboundOrderRepository.findByOutboundNoIgnoreCase(requested)
                    .orElseThrow(() -> new NotFoundException("出库单不存在：" + requested));
        }

        Kanban kanban = findKanbanByScanCode(scanCode);
        String inferredNo = resolveOutboundOrderNo(null, kanban);
        return outboundOrderRepository.findByOutboundNoIgnoreCase(inferredNo)
                .orElseThrow(() -> new NotFoundException("出库单不存在：" + inferredNo));
    }

    protected void validateOutboundPlan(OutboundOrderItemRequest request) {
        if (request.plannedQty() == null || request.plannedQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("出库计划数量必须大于 0");
        }
    }

    protected Location resolveOptionalLocation(String locationCode) {
        String normalized = normalize(locationCode);
        if (normalized == null) {
            return null;
        }
        return locationRepository.findByLocationCode(normalized)
                .orElseThrow(() -> new NotFoundException("出库库位不存在：" + normalized));
    }

    protected List<OutboundAllocation> allocateOutboundQty(OutboundOrder order, OutboundOrderItem item, Location requestedLocation) {
        BigDecimal remaining = item.getPlannedQty();
        Long requestedLocationId = requestedLocation == null ? null : requestedLocation.getId();
        List<Kanban> candidates = kanbanRepository.findAll().stream()
                .filter(kanban -> !kanban.isParentKanban())
                .filter(kanban -> Objects.equals(kanban.getPartId(), item.getPartId()))
                .filter(kanban -> requestedLocationId == null || Objects.equals(kanban.getLocationId(), requestedLocationId))
                .filter(this::isOutboundQtyAllocatable)
                .sorted(this::compareKanbanFifo)
                .toList();

        List<OutboundAllocation> allocations = new ArrayList<>();
        for (Kanban kanban : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal freeQty = kanbanFreeQty(kanban);
            if (freeQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal allocatedQty = freeQty.min(remaining);
            kanban.setReservedQty(defaultBigDecimal(kanban.getReservedQty()).add(allocatedQty));
            appendOutboundOrderNo(kanban, order.getOutboundNo());
            kanbanRepository.save(kanban);
            saveOutboundLockTransaction(kanban, order.getOutboundNo(), allocatedQty, "出库单创建锁定库存");

            OutboundAllocation allocation = new OutboundAllocation();
            allocation.setOutboundOrderId(order.getId());
            allocation.setOutboundOrderItemId(item.getId());
            allocation.setKanbanId(kanban.getId());
            allocation.setAllocatedQty(allocatedQty);
            allocation.setOutboundQty(BigDecimal.ZERO);
            allocation.setStatus(STATUS_ALLOCATED);
            allocation.setCreatedAt(LocalDateTime.now());
            allocations.add(allocation);
            remaining = remaining.subtract(allocatedQty);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            String locationText = requestedLocation == null ? "全部库位" : requestedLocation.getLocationCode();
            throw new BusinessException("零件可用库存不足：需要 " + item.getPlannedQty().toPlainString()
                    + "，" + locationText + " 还差 " + remaining.toPlainString());
        }
        return allocations;
    }

    protected List<Kanban> allocateOutboundBoxes(List<OutboundOrderItemRequest> requests) {
        List<Kanban> allocated = new ArrayList<>();
        Set<Long> allocatedIds = new java.util.HashSet<>();

        for (OutboundOrderItemRequest request : requests) {
            if (request.boxCount() == null || request.boxCount() <= 0) {
                throw new BusinessException("出库箱数必须大于 0");
            }
            Part part = partRepository.findById(request.partId())
                    .orElseThrow(() -> new NotFoundException("零件不存在：" + request.partId()));
            String locationCode = normalize(request.locationCode());
            Long locationId = null;
            if (locationCode != null) {
                Location location = locationRepository.findByLocationCode(locationCode)
                        .orElseThrow(() -> new NotFoundException("出库库位不存在：" + locationCode));
                locationId = location.getId();
            }

            Long selectedLocationId = locationId;
            List<Kanban> candidates = kanbanRepository.findAll().stream()
                    .filter(kanban -> !kanban.isParentKanban())
                    .filter(kanban -> Objects.equals(kanban.getPartId(), request.partId()))
                    .filter(kanban -> selectedLocationId == null || Objects.equals(kanban.getLocationId(), selectedLocationId))
                    .filter(kanban -> !allocatedIds.contains(kanban.getId()))
                    .filter(this::isOutboundAllocatable)
                    .sorted(this::compareKanbanFifo)
                    .toList();

            if (candidates.size() < request.boxCount()) {
                String locationText = locationCode == null ? "全部库位" : locationCode;
                throw new BusinessException("零件 " + part.getPartCode() + " 可出库箱数不足：需要 "
                        + request.boxCount() + " 箱，" + locationText + " 当前可用 " + candidates.size() + " 箱");
            }

            List<Kanban> picked = candidates.stream().limit(request.boxCount()).toList();
            allocated.addAll(picked);
            picked.forEach(kanban -> allocatedIds.add(kanban.getId()));
        }

        if (allocated.isEmpty()) {
            throw new BusinessException("没有可分配的出库箱级看板");
        }
        return allocated;
    }

    protected boolean isOutboundAllocatable(Kanban kanban) {
        if (!STATUS_INBOUND.equals(kanban.getStatus())) {
            return false;
        }
        return !kanban.isFrozen() && kanban.getLocationId() != null && isBlank(kanban.getOutboundOrderNo());
    }

    protected boolean isOutboundQtyAllocatable(Kanban kanban) {
        if (kanban.isFrozen() || kanban.getLocationId() == null) {
            return false;
        }
        if (!Set.of(STATUS_INBOUND, STATUS_ALLOCATED, STATUS_PARTIAL_OUTBOUND).contains(kanban.getStatus())) {
            return false;
        }
        return kanbanFreeQty(kanban).compareTo(BigDecimal.ZERO) > 0;
    }

    protected BigDecimal kanbanAvailableQty(Kanban kanban) {
        BigDecimal available = kanban.getAvailableQty();
        if (available != null) {
            return nonNegative(available);
        }
        return nonNegative(defaultBigDecimal(kanban.getQty()).subtract(defaultBigDecimal(kanban.getOutboundQty())));
    }

    protected BigDecimal kanbanFreeQty(Kanban kanban) {
        return nonNegative(kanbanAvailableQty(kanban)
                .subtract(defaultBigDecimal(kanban.getReservedQty()))
                .subtract(defaultBigDecimal(kanban.getReservedTransferQty())));
    }

    protected void refreshKanbanQuantityState(Kanban kanban) {
        if (kanban.isParentKanban()) {
            return;
        }
        BigDecimal totalQty = defaultBigDecimal(kanban.getQty());
        BigDecimal outboundQty = defaultBigDecimal(kanban.getOutboundQty());
        BigDecimal reservedQty = defaultBigDecimal(kanban.getReservedQty());
        BigDecimal availableQty = nonNegative(totalQty.subtract(outboundQty));
        kanban.setAvailableQty(availableQty);
        kanban.setReservedQty(nonNegative(reservedQty));
        kanban.setOutboundQty(nonNegative(outboundQty));
        if (availableQty.compareTo(BigDecimal.ZERO) <= 0) {
            kanban.setStatus(STATUS_OUTBOUND);
            kanban.setOutboundTime(LocalDateTime.now());
        } else if (reservedQty.compareTo(BigDecimal.ZERO) > 0) {
            kanban.setStatus(STATUS_ALLOCATED);
        } else if (outboundQty.compareTo(BigDecimal.ZERO) > 0) {
            kanban.setStatus(STATUS_PARTIAL_OUTBOUND);
            kanban.setOutboundTime(LocalDateTime.now());
        } else if (STATUS_ALLOCATED.equals(kanban.getStatus()) || STATUS_PARTIAL_OUTBOUND.equals(kanban.getStatus())) {
            kanban.setStatus(STATUS_INBOUND);
        }
        kanbanRepository.save(kanban);
    }

    protected void refreshKanbanStatesByIds(Set<Long> kanbanIds) {
        List<Kanban> touched = kanbanIds.stream()
                .map(id -> kanbanRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        touched.forEach(this::refreshKanbanQuantityState);
    }

    protected boolean isParentOutboundReady(Long parentKanbanId) {
        List<Kanban> children = kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(parentKanbanId);
        return !children.isEmpty()
                && children.stream().noneMatch(item -> NOT_FULLY_INBOUND_STATUSES.contains(item.getStatus()));
    }

    protected int compareKanbanFifo(Kanban left, Kanban right) {
        return Comparator
                .comparing(Kanban::getInboundTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getParentKanbanId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getBoxIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(left, right);
    }

    protected void ensureInboundAllowed(Kanban kanban) {
        if (!INBOUND_PENDING_STATUSES.contains(kanban.getStatus())) {
            if (STATUS_INBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已入库，不能重复入库");
            }
            if (STATUS_OUTBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已出库，不能再入库");
            }
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 当前状态不能入库：" + kanban.getStatus());
        }
    }

    protected void ensureOutboundAllowed(Kanban kanban) {
        if (!OUTBOUND_PENDING_STATUSES.contains(kanban.getStatus())) {
            if (STATUS_OUTBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已出库，不能重复出库");
            }
            if (NOT_FULLY_INBOUND_STATUSES.contains(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 尚未完整入库，不能出库");
            }
            if (STATUS_PARTIAL_OUTBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已部分出库，请扫描绑定到本出库单的箱看板继续出库");
            }
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 当前状态不能出库：" + kanban.getStatus());
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已封存，不能出库");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有库存库位，不能出库");
        }
    }

    protected void applyInboundForSingleKanban(Kanban kanban, Location location, String operationNo) {
        String sourceStatus = kanban.getStatus();
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), location.getId())
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setPartId(kanban.getPartId());
                    created.setLocationId(location.getId());
                    created.setQty(BigDecimal.ZERO);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });

        inventory.setQty(inventory.getQty().add(kanban.getQty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        kanban.setLocationId(location.getId());
        kanban.setAvailableQty(kanban.getQty());
        kanban.setReservedQty(BigDecimal.ZERO);
        kanban.setReservedTransferQty(BigDecimal.ZERO);
        kanban.setOutboundQty(BigDecimal.ZERO);
        kanban.setStatus("THIRD_PARTY".equals(normalizeWarehouseType(location.getWarehouseType())) ? STATUS_THIRD_PARTY_STOCK : STATUS_INBOUND);
        kanban.setInboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("Inbound order item not found"));
        item.setReceivedQty(item.getReceivedQty().add(kanban.getQty()));
        inboundOrderItemRepository.save(item);

        String resolvedOperationNo = defaultOperationNo(operationNo, resolveInboundNo(kanban));
        saveTransaction(kanban, location.getId(), kanban.getQty(), "INBOUND_SCAN", resolveInboundNo(kanban), resolvedOperationNo, "扫码入库");
        saveOperationOrder(resolvedOperationNo, "INBOUND", resolveInboundNo(kanban), null, kanban, null, location.getId(), kanban.getPartId(), kanban.getQty(), sourceStatus, kanban.getStatus(), "扫码入库");
    }

    protected void applyOutboundForSingleKanban(OutboundOrder order, Kanban kanban) {
        String sourceStatus = kanban.getStatus();
        kanban.setOutboundOrderNo(order.getOutboundNo());
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), kanban.getLocationId())
                .orElseThrow(() -> new BusinessException("看板 " + kanban.getKanbanNo() + " 没有对应库存，不能出库"));
        if (inventory.getQty().compareTo(kanban.getQty()) < 0) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 库存不足，不能出库");
        }

        inventory.setQty(inventory.getQty().subtract(kanban.getQty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        kanban.setStatus(STATUS_OUTBOUND);
        kanban.setOutboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        saveInventoryTransaction(
                kanban.getPartId(),
                kanban.getLocationId(),
                kanban.getBarcode(),
                kanban.getQty().negate(),
                "OUTBOUND_SCAN",
                order.getOutboundNo(),
                order.getOutboundNo(),
                "扫码出库完成"
        );
        saveOperationOrder(order.getOutboundNo(), "OUTBOUND", order.getOutboundNo(), kanban, null, kanban.getLocationId(), null, kanban.getPartId(), kanban.getQty(), sourceStatus, kanban.getStatus(), "扫码出库完成");
    }

    protected void refreshParentKanbanState(Kanban scannedKanban) {
        Kanban parent = scannedKanban.isParentKanban()
                ? scannedKanban
                : scannedKanban.getParentKanbanId() == null ? null : kanbanRepository.findById(scannedKanban.getParentKanbanId()).orElse(null);
        if (parent == null || !parent.isParentKanban()) {
            return;
        }

        List<Kanban> children = kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(parent.getId());
        if (children.isEmpty()) {
            return;
        }

        parent.setOutboundOrderNo(aggregateChildOutboundOrderNos(children));
        boolean allInbound = children.stream().allMatch(item -> STATUS_INBOUND.equals(item.getStatus()));
        boolean allOutbound = children.stream().allMatch(item -> STATUS_OUTBOUND.equals(item.getStatus()));
        boolean anyOutbound = children.stream().anyMatch(item -> STATUS_OUTBOUND.equals(item.getStatus()));
        boolean anyAllocated = children.stream().anyMatch(item -> STATUS_ALLOCATED.equals(item.getStatus()));
        boolean anyPartialOutbound = children.stream().anyMatch(item -> STATUS_PARTIAL_OUTBOUND.equals(item.getStatus()));
        boolean anyInboundLike = children.stream().anyMatch(item -> INBOUND_LIKE_STATUSES.contains(item.getStatus()));

        if (allOutbound) {
            parent.setStatus(STATUS_OUTBOUND);
            parent.setOutboundTime(children.stream().map(Kanban::getOutboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        } else if (allInbound) {
            parent.setStatus(STATUS_INBOUND);
            parent.setInboundTime(children.stream().map(Kanban::getInboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
            parent.setLocationId(children.get(0).getLocationId());
        } else if (anyOutbound || anyAllocated || anyPartialOutbound) {
            parent.setStatus(STATUS_PARTIAL_OUTBOUND);
            parent.setOutboundTime(children.stream().map(Kanban::getOutboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        } else if (anyInboundLike) {
            parent.setStatus(STATUS_PARTIAL_INBOUND);
            parent.setInboundTime(children.stream().map(Kanban::getInboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        } else {
            parent.setStatus(STATUS_WAIT_SCAN);
        }
        kanbanRepository.save(parent);
    }

    protected String aggregateChildOutboundOrderNos(List<Kanban> children) {
        String outboundNos = children.stream()
                .map(Kanban::getOutboundOrderNo)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .filter(value -> !"-".equals(value))
                .distinct()
                .collect(Collectors.joining(","));
        return isBlank(outboundNos) ? null : outboundNos;
    }

    protected void refreshParentKanbanStates(List<Kanban> children) {
        children.stream()
                .map(Kanban::getParentKanbanId)
                .filter(Objects::nonNull)
                .distinct()
                .map(parentId -> kanbanRepository.findById(parentId).orElse(null))
                .filter(Objects::nonNull)
                .forEach(this::refreshParentKanbanState);
    }

    protected void repairParentKanbanStates() {
        kanbanRepository.findAll().stream()
                .filter(Kanban::isParentKanban)
                .forEach(this::refreshParentKanbanState);
    }

    protected Kanban findKanbanByScanCode(String scanCode) {
        String normalized = normalize(scanCode);
        if (normalized == null) {
            throw new NotFoundException("请提供看板二维码或条码");
        }
        return kanbanRepository.findByBarcode(normalized)
                .or(() -> kanbanRepository.findByQrContent(normalized))
                .or(() -> kanbanRepository.findByKanbanNoIgnoreCase(normalized))
                .or(() -> {
                    String[] parts = normalized.split("\\|");
                    if (parts.length == 3 && "WMS-KANBAN".equals(parts[0])) {
                        return kanbanRepository.findByBarcode(parts[2])
                                .or(() -> kanbanRepository.findByKanbanNoIgnoreCase(parts[1]));
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> new NotFoundException("没有找到对应看板：" + normalized));
    }

    protected java.util.Optional<InboundOrder> findInboundOrderByScanCode(String scanCode) {
        String businessNo = extractBusinessNo(scanCode, "WMS-INBOUND");
        return businessNo == null ? java.util.Optional.empty() : inboundOrderRepository.findByInboundNoIgnoreCase(businessNo);
    }

    protected java.util.Optional<OutboundOrder> findOutboundOrderByScanCode(String scanCode) {
        String businessNo = extractBusinessNo(scanCode, "WMS-OUTBOUND");
        return businessNo == null ? java.util.Optional.empty() : outboundOrderRepository.findByOutboundNoIgnoreCase(businessNo);
    }

    protected String extractBusinessNo(String scanCode, String prefix) {
        String normalized = normalize(scanCode);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split("\\|");
        if (parts.length == 2 && prefix.equals(parts[0])) {
            return normalize(parts[1]);
        }
        return normalized.startsWith(prefix + ":") ? normalize(normalized.substring(prefix.length() + 1)) : null;
    }

    protected String buildKanbanQrContent(String kanbanNo, String barcode) {
        return "WMS-KANBAN|" + kanbanNo + "|" + barcode;
    }

    protected String buildInboundOrderQrContent(String inboundNo) {
        return "WMS-INBOUND|" + inboundNo;
    }

    protected String buildOutboundOrderQrContent(String outboundNo) {
        return "WMS-OUTBOUND|" + outboundNo;
    }

    protected Inventory moveInventory(Long partId, Long locationId, BigDecimal qtyChange) {
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(partId, locationId)
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setPartId(partId);
                    created.setLocationId(locationId);
                    created.setQty(BigDecimal.ZERO);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });
        BigDecimal nextQty = inventory.getQty().add(qtyChange);
        if (nextQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("库存不足，不能扣减到负数");
        }
        inventory.setQty(nextQty);
        inventory.setUpdatedAt(LocalDateTime.now());
        return inventoryRepository.save(inventory);
    }

    protected String defaultRemark(String remark, String fallback) {
        String normalized = normalize(remark);
        return normalized == null ? fallback : normalized;
    }

    protected void requireWarehouseType(Location location, String expectedType, String message) {
        if (!expectedType.equals(normalizeWarehouseType(location.getWarehouseType()))) {
            throw new BusinessException(message);
        }
    }

    protected String normalizeWarehouseType(String value) {
        if (value == null || value.isBlank()) {
            return "OWN";
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of("OWN", "THIRD_PARTY").contains(normalized)) {
            return "OWN";
        }
        return normalized;
    }

    protected boolean isThirdPartyWarehouseZone(String warehouseZone) {
        String[] plannedZone = splitWarehouseZone(warehouseZone);
        return locationRepository.findAll().stream()
                .anyMatch(location -> location.getWarehouseName().equals(plannedZone[0])
                        && location.getZoneName().equals(plannedZone[1])
                        && "THIRD_PARTY".equals(normalizeWarehouseType(location.getWarehouseType())));
    }

    protected BigDecimal calculateUnitPerBox(BigDecimal plannedQty, Integer boxCount) {
        if (plannedQty == null || boxCount == null || boxCount <= 0) {
            throw new BusinessException("计划数量和箱数必须大于 0");
        }
        return plannedQty.divide(BigDecimal.valueOf(boxCount), 3, RoundingMode.HALF_UP);
    }

    protected PackagePlan resolveInboundPackagePlan(Part part, InboundOrderItemRequest request) {
        return resolvePackagePlan(
                part,
                request.plannedQty(),
                request.boxCount(),
                request.unitPerBox(),
                request.equipmentCode(),
                "入库"
        );
    }

    protected PackagePlan resolveOutboundPackagePlan(Part part, OutboundOrderItemRequest request) {
        return resolvePackagePlan(
                part,
                request.plannedQty(),
                request.boxCount(),
                request.unitPerBox(),
                request.equipmentCode(),
                "出库"
        );
    }

    protected PackagePlan resolvePackagePlan(Part part,
                                           BigDecimal plannedQty,
                                           Integer requestedBoxCount,
                                           BigDecimal requestedUnitPerBox,
                                           String equipmentCode,
                                           String scene) {
        if (plannedQty == null || plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(scene + "总数量必须大于 0");
        }
        String normalizedEquipmentCode = normalize(equipmentCode);
        if (normalizedEquipmentCode == null && part != null) {
            normalizedEquipmentCode = normalize(part.getDefaultEquipmentCode());
        }
        BigDecimal containerCapacity = resolveContainerCapacity(normalizedEquipmentCode);
        if (containerCapacity.compareTo(BigDecimal.ZERO) <= 0 && part != null) {
            containerCapacity = defaultBigDecimal(part.getDefaultPackageCapacity());
        }
        if (containerCapacity.compareTo(BigDecimal.ZERO) > 0) {
            return new PackagePlan(normalizedEquipmentCode, containerCapacity, ceilDivide(plannedQty, containerCapacity));
        }
        if (requestedBoxCount == null || requestedBoxCount <= 0) {
            throw new BusinessException(scene + "未选择容器时必须填写箱数");
        }
        BigDecimal unitPerBox = requestedUnitPerBox != null && requestedUnitPerBox.compareTo(BigDecimal.ZERO) > 0
                ? requestedUnitPerBox
                : calculateUnitPerBox(plannedQty, requestedBoxCount);
        return new PackagePlan(normalizedEquipmentCode, unitPerBox, requestedBoxCount);
    }

    protected BigDecimal resolveContainerCapacity(String equipmentCode) {
        if (equipmentCode == null) {
            return BigDecimal.ZERO;
        }
        Equipment equipment = equipmentRepository.findByEquipmentCode(equipmentCode)
                .orElseThrow(() -> new NotFoundException("器具不存在：" + equipmentCode));
        BigDecimal capacity = defaultBigDecimal(equipment.getCapacity());
        if (capacity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("器具 " + equipmentCode + " 没有有效容量，不能自动计算箱数");
        }
        return capacity;
    }

    protected int ceilDivide(BigDecimal totalQty, BigDecimal unitPerBox) {
        if (totalQty == null || unitPerBox == null || unitPerBox.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("总数量和容器容量必须大于 0");
        }
        return totalQty.divide(unitPerBox, 0, RoundingMode.CEILING).intValue();
    }

    protected record PackagePlan(String equipmentCode, BigDecimal unitPerBox, Integer boxCount) {
    }

    protected void applyOutboundScanToOrder(OutboundOrder order, Kanban kanban) {
        OutboundOrderItem target = outboundOrderItemRepository.findByOutboundOrderIdAndKanbanId(order.getId(), kanban.getId())
                .orElseThrow(() -> new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 未绑定到出库单 " + order.getOutboundNo()));
        if (target.getScannedQty().compareTo(target.getPlannedQty()) >= 0) {
            throw new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 已完成出库，不能重复扫码");
        }
        target.setScannedQty(target.getPlannedQty());
        outboundOrderItemRepository.save(target);

        syncOutboundOrderStatus(order.getId());
    }

    protected void applyOutboundAllocationScan(OutboundOrder order, Kanban kanban) {
        String sourceStatus = kanban.getStatus();
        List<OutboundAllocation> allocations = outboundAllocationRepository.findByOutboundOrderIdAndKanbanIdIn(order.getId(), List.of(kanban.getId())).stream()
                .filter(allocation -> STATUS_ALLOCATED.equals(allocation.getStatus()))
                .toList();
        if (allocations.isEmpty()) {
            throw new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 未绑定到出库单 " + order.getOutboundNo());
        }
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), kanban.getLocationId())
                .orElseThrow(() -> new BusinessException("看板 " + kanban.getKanbanNo() + " 没有对应库存，不能出库"));
        BigDecimal totalOutboundQty = allocations.stream()
                .map(allocation -> allocation.getAllocatedQty().subtract(defaultBigDecimal(allocation.getOutboundQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalOutboundQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 已完成当前出库单分配，不能重复扫码");
        }
        if (inventory.getQty().compareTo(totalOutboundQty) < 0) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 库存不足，不能出库");
        }

        inventory.setQty(inventory.getQty().subtract(totalOutboundQty));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        for (OutboundAllocation allocation : allocations) {
            BigDecimal pendingQty = allocation.getAllocatedQty().subtract(defaultBigDecimal(allocation.getOutboundQty()));
            if (pendingQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            allocation.setOutboundQty(allocation.getAllocatedQty());
            allocation.setStatus(STATUS_OUTBOUND);
            allocation.setOutboundTime(LocalDateTime.now());
            outboundAllocationRepository.save(allocation);

            OutboundOrderItem item = outboundOrderItemRepository.findById(allocation.getOutboundOrderItemId())
                    .orElseThrow(() -> new NotFoundException("出库明细不存在：" + allocation.getOutboundOrderItemId()));
            item.setScannedQty(defaultBigDecimal(item.getScannedQty()).add(pendingQty));
            outboundOrderItemRepository.save(item);
        }

        kanban.setReservedQty(nonNegative(defaultBigDecimal(kanban.getReservedQty()).subtract(totalOutboundQty)));
        kanban.setOutboundQty(defaultBigDecimal(kanban.getOutboundQty()).add(totalOutboundQty));
        appendOutboundOrderNo(kanban, order.getOutboundNo());
        refreshKanbanQuantityState(kanban);

        saveInventoryTransaction(
                kanban.getPartId(),
                kanban.getLocationId(),
                kanban.getBarcode(),
                totalOutboundQty.negate(),
                "OUTBOUND_SCAN",
                order.getOutboundNo(),
                order.getOutboundNo(),
                "扫码出库扣减，来源看板 " + kanban.getKanbanNo()
        );
        saveOperationOrder(order.getOutboundNo(), "OUTBOUND", order.getOutboundNo(), kanban, null, kanban.getLocationId(), null, kanban.getPartId(), totalOutboundQty, sourceStatus, kanban.getStatus(), "扫码出库扣减，来源看板 " + kanban.getKanbanNo());
        syncOutboundOrderStatus(order.getId());
    }

    protected void requireKanbanMatchesOutboundSource(OutboundOrder order, Kanban kanban) {
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("入库单不存在"));
        List<String> sources = splitCsv(order.getInboundOrderNos());
        if (sources.isEmpty()) {
            throw new BusinessException("出库单没有绑定来源入库单");
        }
        boolean matched = sources.stream().anyMatch(item -> item.equalsIgnoreCase(inboundOrder.getInboundNo()));
        if (!matched) {
            throw new BusinessException("看板来源入库单与出库单绑定来源不一致");
        }
    }

    protected void requireKanbanMatchesInboundNo(Kanban kanban, String inboundOrderNo) {
        String sourceInboundNo = normalize(inboundOrderNo);
        if (sourceInboundNo == null) {
            throw new BusinessException("请传入入库单号");
        }
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("入库单不存在"));
        if (!sourceInboundNo.equalsIgnoreCase(inboundOrder.getInboundNo())) {
            throw new BusinessException("看板来源入库单与当前入库单不一致");
        }
    }

    protected List<String> normalizeInboundOrderNos(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    protected List<String> splitCsv(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split(","))
                .map(this::normalize)
                .filter(item -> item != null)
                .toList();
    }

    protected void syncInboundOrderStatus(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("入库单不存在"));
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);

        boolean completed = items.stream().allMatch(item -> item.getReceivedQty().compareTo(item.getPlannedQty()) >= 0);

        boolean partial = items.stream().anyMatch(item -> item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);

        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        inboundOrderRepository.save(order);
    }

    protected void syncOutboundOrderStatus(Long outboundOrderId) {
        OutboundOrder order = outboundOrderRepository.findById(outboundOrderId)
                .orElseThrow(() -> new NotFoundException("出库单不存在"));
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(outboundOrderId);

        boolean completed = items.stream().allMatch(item -> item.getScannedQty().compareTo(item.getPlannedQty()) >= 0);

        boolean partial = items.stream().anyMatch(item -> item.getScannedQty().compareTo(BigDecimal.ZERO) > 0);

        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        outboundOrderRepository.save(order);
    }

    protected void saveTransaction(Kanban kanban, Long locationId, BigDecimal qtyChange, String businessType, String remark) {
        saveTransaction(kanban, locationId, qtyChange, businessType, kanban.getKanbanNo(), remark);
    }

    protected void saveTransaction(Kanban kanban, Long locationId, BigDecimal qtyChange, String businessType, String operationNo, String remark) {
        saveTransaction(kanban, locationId, qtyChange, businessType, kanban.getKanbanNo(), operationNo, remark);
    }

    protected void saveTransaction(Kanban kanban, Long locationId, BigDecimal qtyChange, String businessType, String businessNo, String operationNo, String remark) {
        saveInventoryTransaction(
                kanban.getPartId(),
                locationId,
                kanban.getBarcode(),
                qtyChange,
                businessType,
                businessNo,
                operationNo,
                remark
        );
    }

    protected void saveInventoryTransaction(Long partId,
                                          Long locationId,
                                          String barcode,
                                          BigDecimal qtyChange,
                                          String businessType,
                                          String businessNo,
                                          String remark) {
        saveInventoryTransaction(partId, locationId, barcode, qtyChange, businessType, businessNo, businessNo, remark);
    }

    protected void saveInventoryTransaction(Long partId,
                                          Long locationId,
                                          String barcode,
                                          BigDecimal qtyChange,
                                          String businessType,
                                          String businessNo,
                                          String operationNo,
                                          String remark) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionNo(nextBusinessNo("TX"));
        transaction.setPartId(partId);
        transaction.setLocationId(locationId);
        transaction.setBarcode(barcode);
        transaction.setQtyChange(qtyChange);
        transaction.setBusinessType(businessType);
        transaction.setBusinessNo(businessNo);
        transaction.setOperationNo(defaultOperationNo(operationNo, businessNo));
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        inventoryTransactionRepository.save(transaction);
    }

    protected void saveOutboundLockTransaction(Kanban kanban, String outboundNo, BigDecimal lockedQty, String remark) {
        saveLockTransaction(kanban, outboundNo, "OUTBOUND_LOCK", lockedQty, remark);
    }

    protected void saveOutboundUnlockTransaction(Kanban kanban, String outboundNo, BigDecimal unlockedQty, String remark) {
        saveLockTransaction(kanban, outboundNo, "OUTBOUND_UNLOCK", unlockedQty, remark);
    }

    protected void saveTransferLockTransaction(Kanban kanban, String transferNo, BigDecimal lockedQty, String remark) {
        saveLockTransaction(kanban, transferNo, "TRANSFER_LOCK", lockedQty, remark);
    }

    protected void saveTransferUnlockTransaction(Kanban kanban, String transferNo, BigDecimal unlockedQty, String remark) {
        saveLockTransaction(kanban, transferNo, "TRANSFER_UNLOCK", unlockedQty, remark);
    }

    protected void saveLockTransaction(Kanban kanban, String operationNo, String businessType, BigDecimal lockQty, String remark) {
        String normalizedRemark = defaultString(remark) + "，锁定数量 " + defaultBigDecimal(lockQty).toPlainString();
        saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, businessType, operationNo, operationNo, normalizedRemark);
    }

    protected InventoryOperationOrder saveOperationOrder(String operationNo,
                                                       String operationType,
                                                       String businessNo,
                                                       Kanban source,
                                                       Kanban target,
                                                       Long sourceLocationId,
                                                       Long targetLocationId,
                                                       Long partId,
                                                       BigDecimal qty,
                                                       String sourceStatus,
                                                       String targetStatus,
                                                       String remark) {
        return saveOperationOrder(
                operationNo,
                operationType,
                businessNo,
                source == null ? null : source.getKanbanNo(),
                target == null ? null : target.getKanbanNo(),
                source == null ? null : source.getBarcode(),
                target == null ? null : target.getBarcode(),
                partId,
                sourceLocationId,
                targetLocationId,
                qty,
                sourceStatus,
                targetStatus,
                remark
        );
    }

    protected InventoryOperationOrder saveOperationOrder(String operationNo,
                                                       String operationType,
                                                       String businessNo,
                                                       String sourceKanbanNo,
                                                       String targetKanbanNo,
                                                       String sourceBarcode,
                                                       String targetBarcode,
                                                       Long partId,
                                                       Long sourceLocationId,
                                                       Long targetLocationId,
                                                       BigDecimal qty,
                                                       String sourceStatus,
                                                       String targetStatus,
                                                       String remark) {
        InventoryOperationOrder order = new InventoryOperationOrder();
        order.setOperationNo(defaultOperationNo(operationNo, businessNo));
        order.setOperationType(operationType);
        order.setBusinessNo(businessNo);
        order.setSourceKanbanNo(sourceKanbanNo);
        order.setTargetKanbanNo(targetKanbanNo);
        order.setSourceBarcode(sourceBarcode);
        order.setTargetBarcode(targetBarcode);
        order.setPartId(partId);
        order.setSourceLocationId(sourceLocationId);
        order.setTargetLocationId(targetLocationId);
        order.setQty(defaultBigDecimal(qty));
        order.setSourceStatus(sourceStatus);
        order.setTargetStatus(targetStatus);
        order.setRemark(remark);
        order.setCreatedAt(LocalDateTime.now());
        return inventoryOperationOrderRepository.save(order);
    }

    protected String defaultOperationNo(String operationNo, String fallback) {
        String normalized = normalize(operationNo);
        if (normalized != null) {
            return normalized;
        }
        normalized = normalize(fallback);
        return normalized == null ? nextBusinessNo("OP") : normalized;
    }

    protected String resolveInboundNo(Kanban kanban) {
        return inboundOrderRepository.findById(kanban.getInboundOrderId())
                .map(InboundOrder::getInboundNo)
                .orElse(kanban.getKanbanNo());
    }

    protected String operationTypeFromBusinessType(String businessType) {
        String normalized = normalize(businessType);
        if (normalized == null) {
            return "OTHER";
        }
        if (normalized.startsWith("OUTSOURCE_TRANSFER")) {
            return "OUTSOURCE_TRANSFER";
        }
        if (normalized.startsWith("OUTSOURCE_RETURN")) {
            return "OUTSOURCE_RETURN";
        }
        if (normalized.startsWith("TRANSFER")) {
            return "TRANSFER";
        }
        if (normalized.startsWith("INBOUND")) {
            return "INBOUND";
        }
        if (normalized.startsWith("OUTBOUND")) {
            return "OUTBOUND";
        }
        return normalized;
    }

    protected Specification<InboundOrder> inboundOrderSpecification(String status, Long supplierId, String inboundNo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isBlank(status)) {
                List<String> statuses = splitCsv(status).stream().map(String::toUpperCase).toList();
                predicates.add(statuses.size() == 1
                        ? criteriaBuilder.equal(criteriaBuilder.upper(root.get("status")), statuses.get(0))
                        : criteriaBuilder.upper(root.get("status")).in(statuses));
            }
            if (supplierId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplierId"), supplierId));
            }
            if (!isBlank(inboundNo)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("inboundNo")), "%" + inboundNo.trim().toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected Specification<OutboundOrder> outboundOrderSpecification(String status, Long customerId, String outboundNo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!isBlank(status)) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("status")), status.trim().toUpperCase()));
            }
            if (customerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), customerId));
            }
            if (!isBlank(outboundNo)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("outboundNo")), "%" + outboundNo.trim().toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected Specification<Kanban> kanbanSpecification(String status,
                                                      String inboundNo,
                                                      String outboundNo,
                                                      String kanbanNo,
                                                      Long supplierId,
                                                      String partCode,
                                                      String warehouseName,
                                                      String zoneName,
                                                      String warehouseType,
                                                      boolean includeChildren) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeChildren) {
                predicates.add(criteriaBuilder.isFalse(root.get("parentKanban")));
            }
            if (!isBlank(status)) {
                List<String> statuses = splitCsv(status).stream().map(String::toUpperCase).toList();
                predicates.add(statuses.size() == 1
                        ? criteriaBuilder.equal(criteriaBuilder.upper(root.get("status")), statuses.get(0))
                        : criteriaBuilder.upper(root.get("status")).in(statuses));
            }
            if (!isBlank(outboundNo)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("outboundOrderNo")), likePattern(outboundNo)));
            }
            if (!isBlank(kanbanNo)) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("kanbanNo")), likePattern(kanbanNo)),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), likePattern(kanbanNo)),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("qrContent")), likePattern(kanbanNo))
                ));
            }
            if (supplierId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplierId"), supplierId));
            }
            if (!isBlank(inboundNo)) {
                Subquery<Long> inboundSubquery = query.subquery(Long.class);
                Root<InboundOrder> inboundRoot = inboundSubquery.from(InboundOrder.class);
                inboundSubquery.select(inboundRoot.get("id"))
                        .where(criteriaBuilder.like(criteriaBuilder.lower(inboundRoot.get("inboundNo")), likePattern(inboundNo)));
                predicates.add(root.get("inboundOrderId").in(inboundSubquery));
            }
            if (!isBlank(partCode)) {
                Subquery<Long> partSubquery = query.subquery(Long.class);
                Root<Part> partRoot = partSubquery.from(Part.class);
                partSubquery.select(partRoot.get("id"))
                        .where(criteriaBuilder.like(criteriaBuilder.lower(partRoot.get("partCode")), likePattern(partCode)));
                predicates.add(root.get("partId").in(partSubquery));
            }
            if (!isBlank(warehouseName) || !isBlank(zoneName) || !isBlank(warehouseType)) {
                Subquery<Long> locationSubquery = query.subquery(Long.class);
                Root<Location> locationRoot = locationSubquery.from(Location.class);
                List<Predicate> locationPredicates = new ArrayList<>();
                if (!isBlank(warehouseName)) {
                    locationPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(locationRoot.get("warehouseName")), likePattern(warehouseName)));
                }
                if (!isBlank(zoneName)) {
                    locationPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(locationRoot.get("zoneName")), likePattern(zoneName)));
                }
                if (!isBlank(warehouseType)) {
                    locationPredicates.add(criteriaBuilder.equal(criteriaBuilder.upper(locationRoot.get("warehouseType")), warehouseType.trim().toUpperCase()));
                }
                locationSubquery.select(locationRoot.get("id"))
                        .where(criteriaBuilder.and(locationPredicates.toArray(new Predicate[0])));
                predicates.add(root.get("locationId").in(locationSubquery));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected Specification<Kanban> inventoryKanbanSpecification(String partCode,
                                                               String warehouseName,
                                                               String zoneName,
                                                               String kanbanNo,
                                                               Long supplierId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get("parentKanban")));
            predicates.add(root.get("status").in(Set.of(
                    STATUS_INBOUND,
                    STATUS_FROZEN,
                    STATUS_REPACK_OUTBOUND,
                    STATUS_REPACK_INBOUND,
                    STATUS_THIRD_PARTY_STOCK,
                    STATUS_ALLOCATED,
                    STATUS_PARTIAL_OUTBOUND
            )));
            predicates.add(criteriaBuilder.isNotNull(root.get("locationId")));
            if (supplierId != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplierId"), supplierId));
            }
            if (!isBlank(kanbanNo)) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("kanbanNo")), likePattern(kanbanNo)),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), likePattern(kanbanNo)),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("qrContent")), likePattern(kanbanNo))
                ));
            }
            if (!isBlank(partCode)) {
                Subquery<Long> partSubquery = query.subquery(Long.class);
                Root<Part> partRoot = partSubquery.from(Part.class);
                partSubquery.select(partRoot.get("id"))
                        .where(criteriaBuilder.like(criteriaBuilder.lower(partRoot.get("partCode")), likePattern(partCode)));
                predicates.add(root.get("partId").in(partSubquery));
            }
            if (!isBlank(warehouseName) || !isBlank(zoneName)) {
                Subquery<Long> locationSubquery = query.subquery(Long.class);
                Root<Location> locationRoot = locationSubquery.from(Location.class);
                List<Predicate> locationPredicates = new ArrayList<>();
                if (!isBlank(warehouseName)) {
                    locationPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(locationRoot.get("warehouseName")), likePattern(warehouseName)));
                }
                if (!isBlank(zoneName)) {
                    locationPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(locationRoot.get("zoneName")), likePattern(zoneName)));
                }
                locationSubquery.select(locationRoot.get("id"))
                        .where(criteriaBuilder.and(locationPredicates.toArray(new Predicate[0])));
                predicates.add(root.get("locationId").in(locationSubquery));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected List<InboundOrderView> toInboundOrderViews(List<InboundOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(InboundOrder::getId).toList();
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderIdIn(orderIds);
        Map<Long, List<InboundOrderItem>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(InboundOrderItem::getInboundOrderId));
        Map<Long, Supplier> supplierMap = supplierRepository.findAllById(
                        orders.stream().map(InboundOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        Map<Long, Part> partMap = partRepository.findAllById(
                        items.stream().map(InboundOrderItem::getPartId).filter(Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));

        return orders.stream()
                .map(order -> toInboundOrderView(order, supplierMap, itemsByOrderId, partMap))
                .toList();
    }

    protected InboundOrderView toInboundOrderView(InboundOrder order) {
        List<InboundOrderView> views = toInboundOrderViews(List.of(order));
        if (views.isEmpty()) {
            throw new NotFoundException("入库单不存在");
        }
        return views.get(0);
    }

    protected InboundOrderView toInboundOrderView(InboundOrder order,
                                               Map<Long, Supplier> supplierMap,
                                               Map<Long, List<InboundOrderItem>> itemsByOrderId,
                                               Map<Long, Part> partMap) {
        String supplierName = supplierMap.getOrDefault(order.getSupplierId(), new Supplier()).getSupplierName();
        if (isBlank(supplierName)) {
            supplierName = "-";
        }
        List<InboundOrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
        return new InboundOrderView(
                order.getId(),
                order.getInboundNo(),
                buildInboundOrderQrContent(order.getInboundNo()),
                order.getSupplierId(),
                supplierName,
                order.getStatus(),
                order.getCreatedAt(),
                items.stream().map(item -> {
                    Part part = partMap.get(item.getPartId());
                    return new InboundOrderView.ItemView(
                            item.getId(),
                            item.getPartId(),
                            part == null ? "-" : part.getPartCode(),
                            part == null ? "-" : part.getPartName(),
                            part == null ? "-" : part.getUnit(),
                            item.getPlannedQty(),
                            item.getReceivedQty(),
                            item.getBoxCount(),
                            item.isPendingRepack(),
                            item.getEquipmentCode(),
                            item.getUnitPerBox(),
                            item.getWarehouseZone()
                    );
                }).toList()
        );
    }

    protected OutboundOrderView toOutboundOrderView(OutboundOrder order) {
        List<OutboundOrderView> views = toOutboundOrderViews(List.of(order));
        if (views.isEmpty()) {
            throw new NotFoundException("出库单不存在");
        }
        return views.get(0);
    }

    protected List<OutboundOrderView> toOutboundOrderViews(List<OutboundOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(OutboundOrder::getId).toList();
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderIdIn(orderIds);
        Map<Long, List<OutboundOrderItem>> itemsByOrderId = items.stream()
                .collect(Collectors.groupingBy(OutboundOrderItem::getOutboundOrderId));
        Map<Long, Customer> customerMap = customerRepository.findAllById(
                        orders.stream().map(OutboundOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, Part> partMap = partRepository.findAllById(
                        items.stream().map(OutboundOrderItem::getPartId).filter(Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<OutboundAllocation> allocations = outboundAllocationRepository.findByOutboundOrderItemIdIn(
                items.stream().map(OutboundOrderItem::getId).collect(Collectors.toSet())
        );
        Map<Long, Kanban> kanbanMap = kanbanRepository.findAllById(
                        allocations.stream().map(OutboundAllocation::getKanbanId).filter(Objects::nonNull).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Kanban::getId, Function.identity()));
        Map<Long, String> kanbanNosByItemId = allocationKanbanNosByItemId(allocations, kanbanMap);
        Map<Long, String> allocationDetailByItemId = allocationDetailByItemId(allocations, kanbanMap);

        return orders.stream()
                .map(order -> toOutboundOrderView(order, customerMap, itemsByOrderId, partMap, kanbanNosByItemId, allocationDetailByItemId))
                .toList();
    }

    protected OutboundOrderView toOutboundOrderView(OutboundOrder order,
                                                  Map<Long, Customer> customerMap,
                                                  Map<Long, List<OutboundOrderItem>> itemsByOrderId,
                                                  Map<Long, Part> partMap,
                                                  Map<Long, String> kanbanNosByItemId,
                                                  Map<Long, String> allocationDetailByItemId) {
        Customer customer = order.getCustomerId() == null ? null : customerMap.get(order.getCustomerId());
        String customerName = customer == null || isBlank(customer.getCustomerName()) ? "-" : customer.getCustomerName();
        List<OutboundOrderItem> items = itemsByOrderId.getOrDefault(order.getId(), List.of());
        return new OutboundOrderView(
                order.getId(),
                order.getOutboundNo(),
                buildOutboundOrderQrContent(order.getOutboundNo()),
                order.getCustomerId(),
                customerName,
                splitCsv(order.getInboundOrderNos()),
                order.getStatus(),
                order.getCreatedAt(),
                items.stream().map(item -> {
                    Part part = partMap.get(item.getPartId());
                    return new OutboundOrderView.ItemView(
                            item.getId(),
                            item.getKanbanId(),
                            defaultString(kanbanNosByItemId.getOrDefault(item.getId(), item.getKanbanNo())),
                            defaultString(allocationDetailByItemId.get(item.getId())),
                            item.getPartId(),
                            part == null ? "-" : part.getPartCode(),
                            part == null ? "-" : part.getPartName(),
                            part == null ? "-" : part.getUnit(),
                            item.getPlannedQty(),
                            item.getScannedQty(),
                            item.getBoxCount(),
                            item.getEquipmentCode(),
                            item.getUnitPerBox(),
                            defaultString(item.getLocationCode()),
                            defaultString(item.getWarehouseName()),
                            defaultString(item.getZoneName())
                    );
                }).toList()
        );
    }

    protected KanbanView toKanbanView(Kanban kanban) {
        return toKanbanViews(List.of(kanban), true).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("看板不存在：" + kanban.getId()));
    }

    protected List<KanbanView> toKanbanViews(List<Kanban> sourceKanbans, boolean includeChildren) {
        if (sourceKanbans.isEmpty()) {
            return List.of();
        }

        List<Kanban> renderKanbans = new ArrayList<>(sourceKanbans);
        if (includeChildren) {
            Set<Long> parentIds = sourceKanbans.stream()
                    .filter(Kanban::isParentKanban)
                    .map(Kanban::getId)
                    .collect(Collectors.toSet());
            if (!parentIds.isEmpty()) {
                List<Kanban> children = kanbanRepository.findByParentKanbanIdIn(new ArrayList<>(parentIds));
                renderKanbans.addAll(children);
            }
        }

        Set<Long> partIds = renderKanbans.stream().map(Kanban::getPartId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> itemIds = renderKanbans.stream().map(Kanban::getInboundOrderItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> inboundOrderIds = renderKanbans.stream().map(Kanban::getInboundOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> locationIds = renderKanbans.stream().map(Kanban::getLocationId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> supplierIds = renderKanbans.stream().map(Kanban::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, Part> partMap = partRepository.findAllById(partIds).stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, InboundOrderItem> itemMap = inboundOrderItemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(InboundOrderItem::getId, Function.identity()));
        Map<Long, InboundOrder> inboundOrderMap = inboundOrderRepository.findAllById(inboundOrderIds).stream()
                .collect(Collectors.toMap(InboundOrder::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAllById(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Supplier> supplierMap = supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        Set<String> equipmentCodes = itemMap.values().stream()
                .map(InboundOrderItem::getEquipmentCode)
                .filter(value -> !isBlank(value))
                .collect(Collectors.toSet());
        Map<String, Equipment> equipmentMap = equipmentRepository.findAll().stream()
                .filter(item -> equipmentCodes.contains(item.getEquipmentCode()))
                .filter(item -> !isBlank(item.getEquipmentCode()))
                .collect(Collectors.toMap(Equipment::getEquipmentCode, Function.identity(), (left, right) -> left));
        Map<Long, List<Kanban>> childrenByParentId = renderKanbans.stream()
                .filter(item -> item.getParentKanbanId() != null)
                .collect(Collectors.groupingBy(Kanban::getParentKanbanId));
        childrenByParentId.values().forEach(children -> children.sort(Comparator
                .comparing(Kanban::getBoxIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getId, Comparator.nullsLast(Comparator.naturalOrder()))));

        return sourceKanbans.stream()
                .map(kanban -> toKanbanView(
                        kanban,
                        partMap,
                        itemMap,
                        inboundOrderMap,
                        locationMap,
                        supplierMap,
                        equipmentMap,
                        childrenByParentId,
                        includeChildren
                ))
                .toList();
    }

    protected KanbanView toKanbanView(Kanban kanban,
                                    Map<Long, Part> partMap,
                                    Map<Long, InboundOrderItem> itemMap,
                                    Map<Long, InboundOrder> inboundOrderMap,
                                    Map<Long, Location> locationMap,
                                    Map<Long, Supplier> supplierMap,
                                    Map<String, Equipment> equipmentMap,
                                    Map<Long, List<Kanban>> childrenByParentId,
                                    boolean includeChildren) {
        Part part = partMap.get(kanban.getPartId());
        InboundOrderItem item = itemMap.get(kanban.getInboundOrderItemId());
        InboundOrder inboundOrder = inboundOrderMap.get(kanban.getInboundOrderId());
        Location location = kanban.getLocationId() == null ? null : locationMap.get(kanban.getLocationId());
        Supplier supplier = inboundOrder == null ? null : supplierMap.get(inboundOrder.getSupplierId());
        Equipment equipment = item == null || isBlank(item.getEquipmentCode()) ? null : equipmentMap.get(item.getEquipmentCode());

        String[] plannedZone = splitWarehouseZone(item == null ? null : item.getWarehouseZone());
        String warehouseName = location != null ? location.getWarehouseName() : plannedZone[0];
        String zoneName = location != null ? location.getZoneName() : plannedZone[1];
        List<Kanban> childEntities = kanban.isParentKanban()
                ? childrenByParentId.getOrDefault(kanban.getId(), List.of())
                : List.of();
        List<KanbanView> children = includeChildren
                ? childEntities.stream()
                .map(child -> toKanbanView(
                        child,
                        partMap,
                        itemMap,
                        inboundOrderMap,
                        locationMap,
                        supplierMap,
                        equipmentMap,
                        childrenByParentId,
                        false
                ))
                .toList()
                : List.of();
        String outboundNo = resolveParentOutboundNo(kanban, childEntities);

        return new KanbanView(
                kanban.getId(),
                kanban.getKanbanNo(),
                kanban.getBarcode(),
                defaultString(kanban.getQrContent()).equals("-") ? buildKanbanQrContent(kanban.getKanbanNo(), kanban.getBarcode()) : kanban.getQrContent(),
                kanban.getParentKanbanId(),
                kanban.isParentKanban(),
                kanban.getBoxIndex(),
                inboundOrder == null ? "-" : inboundOrder.getInboundNo(),
                outboundNo,
                kanban.getPartId(),
                part == null ? "-" : part.getPartCode(),
                part == null ? "-" : part.getPartName(),
                part == null ? "-" : part.getUnit(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? "-" : supplier.getSupplierName(),
                defaultString(kanban.getBatchNo()),
                kanban.getQty(),
                kanbanAvailableQty(kanban),
                defaultBigDecimal(kanban.getReservedQty()),
                defaultBigDecimal(kanban.getReservedTransferQty()),
                defaultBigDecimal(kanban.getOutboundQty()),
                kanban.getSourceKanbanId(),
                defaultString(kanban.getTransferOrderNo()),
                defaultString(kanban.getFrozenPreviousStatus()),
                item == null ? 0 : item.getBoxCount(),
                item != null && item.isPendingRepack(),
                item == null ? "" : defaultString(item.getEquipmentCode()),
                equipment == null ? "" : defaultString(equipment.getEquipmentModel()),
                item == null ? BigDecimal.ZERO : defaultBigDecimal(item.getUnitPerBox()),
                warehouseName,
                zoneName,
                location == null ? "OWN" : normalizeWarehouseType(location.getWarehouseType()),
                kanban.getStatus(),
                kanban.getLocationId(),
                location == null ? "-" : location.getLocationCode(),
                kanban.getCreatedAt(),
                kanban.getInboundTime(),
                kanban.getOutboundTime(),
                children
        );
    }

    protected String resolveParentOutboundNo(Kanban kanban, List<Kanban> children) {
        String outboundNo = defaultString(kanban.getOutboundOrderNo());
        if (!kanban.isParentKanban() || !"-".equals(outboundNo)) {
            return outboundNo;
        }
        String childOutboundNos = children.stream()
                .map(Kanban::getOutboundOrderNo)
                .map(this::defaultString)
                .filter(value -> !isBlank(value) && !"-".equals(value))
                .distinct()
                .collect(Collectors.joining(","));
        return isBlank(childOutboundNos) ? "-" : childOutboundNos;
    }

    protected Map<Long, String> allocationKanbanNosByItemId(List<OutboundAllocation> allocations, Map<Long, Kanban> kanbanMap) {
        if (allocations.isEmpty()) {
            return Map.of();
        }
        return allocations.stream().collect(Collectors.groupingBy(
                OutboundAllocation::getOutboundOrderItemId,
                Collectors.mapping(
                        allocation -> {
                            Kanban kanban = kanbanMap.get(allocation.getKanbanId());
                            return kanban == null ? "-" : kanban.getKanbanNo();
                        },
                        Collectors.collectingAndThen(Collectors.toCollection(java.util.LinkedHashSet::new), values -> String.join(",", values))
                )
        ));
    }

    protected Map<Long, String> allocationDetailByItemId(List<OutboundAllocation> allocations, Map<Long, Kanban> kanbanMap) {
        if (allocations.isEmpty()) {
            return Map.of();
        }
        return allocations.stream().collect(Collectors.groupingBy(
                OutboundAllocation::getOutboundOrderItemId,
                Collectors.mapping(allocation -> {
                    Kanban kanban = kanbanMap.get(allocation.getKanbanId());
                    String kanbanNo = kanban == null ? "-" : kanban.getKanbanNo();
                    BigDecimal allocatedQty = defaultBigDecimal(allocation.getAllocatedQty());
                    BigDecimal outboundQty = defaultBigDecimal(allocation.getOutboundQty());
                    BigDecimal remainingQty = nonNegative(allocatedQty.subtract(outboundQty));
                    return kanbanNo + "：分配 " + allocatedQty.toPlainString()
                            + "，已出 " + outboundQty.toPlainString()
                            + "，剩余 " + remainingQty.toPlainString();
                }, Collectors.joining("；"))
        ));
    }

    protected String inventoryKey(Long partId, Long locationId) {
        return partId + ":" + locationId;
    }

    protected String[] splitWarehouseZone(String text) {
        if (isBlank(text)) {
            return new String[]{"-", "-"};
        }

        String[] slash = text.split("/");
        if (slash.length >= 2) {
            return new String[]{slash[0].trim(), slash[1].trim()};
        }

        String[] white = text.trim().split("\\s+");
        if (white.length >= 2) {
            return new String[]{white[0], white[1]};
        }
        return new String[]{text.trim(), "-"};
    }

    protected String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    protected boolean containsIgnoreCase(String text, String query) {
        if (isBlank(query)) {
            return true;
        }
        return defaultString(text).toLowerCase().contains(query.trim().toLowerCase());
    }

    protected String likePattern(String query) {
        return "%" + query.trim().toLowerCase() + "%";
    }

    protected String defaultString(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    protected String nextBusinessNo(String prefix) {
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return defaultString(prefix).replace("-", "") + LocalDateTime.now().format(TS) + random;
    }

    protected BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    protected BigDecimal nonNegative(BigDecimal value) {
        BigDecimal normalized = defaultBigDecimal(value);
        return normalized.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : normalized;
    }

    protected void appendOutboundOrderNo(Kanban kanban, String outboundNo) {
        String normalized = normalize(outboundNo);
        if (normalized == null) {
            return;
        }
        List<String> values = new ArrayList<>(splitCsv(kanban.getOutboundOrderNo()));
        if (values.stream().noneMatch(item -> item.equalsIgnoreCase(normalized))) {
            values.add(normalized);
        }
        kanban.setOutboundOrderNo(String.join(",", values));
    }

    protected void appendTransferOrderNo(Kanban kanban, String transferNo) {
        String normalized = normalize(transferNo);
        if (normalized == null) {
            return;
        }
        List<String> values = new ArrayList<>(splitCsv(kanban.getTransferOrderNo()));
        if (values.stream().noneMatch(item -> item.equalsIgnoreCase(normalized))) {
            values.add(normalized);
        }
        kanban.setTransferOrderNo(String.join(",", values));
    }

    protected void removeOutboundOrderNo(Kanban kanban, String outboundNo) {
        String normalized = normalize(outboundNo);
        if (normalized == null) {
            return;
        }
        List<String> values = splitCsv(kanban.getOutboundOrderNo()).stream()
                .filter(item -> !item.equalsIgnoreCase(normalized))
                .toList();
        kanban.setOutboundOrderNo(values.isEmpty() ? null : String.join(",", values));
    }
}
