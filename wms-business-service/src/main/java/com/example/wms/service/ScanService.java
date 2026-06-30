/**
 * 本文件实现移动端和手工扫码入库、扫码出库业务。
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
public class ScanService extends WmsServiceSupport {

    public ScanService(WmsServiceDependencies dependencies) {
        super(dependencies);
    }

    @Transactional
    public ScanResultView scanInbound(ScanInboundRequest request) {
        String normalized = normalize(request.barcode());
        InboundOrder inboundOrder = findInboundOrderByScanCode(normalized).orElse(null);
        Kanban scannedKanban = inboundOrder == null ? findKanbanByScanCode(normalized) : null;
        List<Kanban> targets = inboundOrder == null
                ? resolveInboundScanTargets(scannedKanban)
                : resolveInboundOrderScanTargets(inboundOrder);
        Kanban reference = targets.get(0);
        String operationNo = inboundOrder == null ? resolveInboundNo(reference) : inboundOrder.getInboundNo();
        for (Kanban target : targets) {
            ensureInboundAllowed(target);
            Location location = resolveInboundLocationForKanban(request.locationCode(), target);
            applyInboundForSingleKanban(target, location, operationNo);
        }
        syncInboundOrderStatus(reference.getInboundOrderId());
        Kanban resultKanban = kanbanRepository.findById(reference.getId()).orElse(reference);
        return new ScanResultView(
                "INBOUND_OK",
                (inboundOrder == null ? "看板扫码入库成功" : "入库单扫码入库成功") + "，已处理 " + targets.size() + " 箱",
                inboundOrder == null ? resultKanban.getBarcode() : buildInboundOrderQrContent(inboundOrder.getInboundNo()),
                resultKanban.getKanbanNo(),
                inboundOrder == null ? null : inboundOrder.getInboundNo(),
                null,
                resultKanban.getStatus(),
                targets.size(),
                targets.stream().map(Kanban::getKanbanNo).toList()
        );
    }

    @Transactional
    public ScanResultView scanInboundBatch(ScanInboundBatchRequest request) {
        String normalized = normalize(request.scanCode());
        if (normalized == null) {
            normalized = normalize(request.parentBarcode());
        }
        if (normalized == null) {
            throw new BusinessException("请提供入库单二维码或箱看板条码");
        }
        String scanCode = normalized;
        InboundOrder inboundOrder = findInboundOrderByScanCode(scanCode)
                .orElseGet(() -> inboundOrderRepository.findById(findKanbanByScanCode(scanCode).getInboundOrderId())
                        .orElseThrow(() -> new NotFoundException("入库单不存在")));
        List<Kanban> allBoxes = kanbanRepository.findByInboundOrderId(inboundOrder.getId()).stream()
                .filter(kanban -> !kanban.isParentKanban())
                .sorted(this::compareKanbanFifo)
                .toList();
        List<Long> selectedIds = request.childKanbanIds() == null ? List.of() : request.childKanbanIds();
        List<Kanban> targets = selectedIds.isEmpty()
                ? allBoxes.stream().filter(item -> INBOUND_PENDING_STATUSES.contains(item.getStatus())).toList()
                : allBoxes.stream().filter(item -> selectedIds.contains(item.getId())).toList();
        if (targets.isEmpty()) {
            throw new BusinessException("入库单 " + inboundOrder.getInboundNo() + " 没有待入库箱子");
        }
        if (!selectedIds.isEmpty() && targets.size() != selectedIds.stream().distinct().count()) {
            throw new BusinessException("所选看板不完全属于入库单 " + inboundOrder.getInboundNo());
        }

        String operationNo = inboundOrder.getInboundNo();
        for (Kanban target : targets) {
            ensureInboundAllowed(target);
            Location location = resolveInboundLocationForKanban(request.locationCode(), target);
            applyInboundForSingleKanban(target, location, operationNo);
        }

        syncInboundOrderStatus(inboundOrder.getId());
        Kanban resultKanban = kanbanRepository.findById(targets.get(0).getId()).orElse(targets.get(0));
        return new ScanResultView(
                "INBOUND_OK",
                "批量入库成功，已处理 " + targets.size() + " 箱",
                buildInboundOrderQrContent(inboundOrder.getInboundNo()),
                resultKanban.getKanbanNo(),
                inboundOrder.getInboundNo(),
                null,
                resultKanban.getStatus(),
                targets.size(),
                targets.stream().map(Kanban::getKanbanNo).toList()
        );
    }

    @Transactional
    public ScanResultView scanOutbound(ScanOutboundRequest request) {
        String normalized = normalize(request.barcode());
        OutboundOrder order = resolveOutboundOrderForScan(normalized, request.outboundOrderNo());
        List<Kanban> targets = resolveOutboundScanTargets(normalized, order);
        for (Kanban target : targets) {
            ensureOutboundAllowed(target);
            applyOutboundAllocationScan(order, target);
        }
        Kanban resultKanban = kanbanRepository.findById(targets.get(0).getId()).orElse(targets.get(0));
        syncOutboundOrderStatus(order.getId());
        return new ScanResultView(
                "OUTBOUND_OK",
                "扫码出库成功，已处理 " + targets.size() + " 箱，出库单 " + order.getOutboundNo(),
                normalized,
                resultKanban.getKanbanNo(),
                null,
                order.getOutboundNo(),
                resultKanban.getStatus(),
                targets.size(),
                targets.stream().map(Kanban::getKanbanNo).toList()
        );
    }
}
