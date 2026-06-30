/**
 * 本文件实现库存汇总、库存明细和库存流水查询业务。
 */
package com.example.wms.service;

import com.example.wms.api.InventoryController.InventorySummaryView;
import com.example.wms.api.InventoryController.InventoryPartSummaryView;
import com.example.wms.api.InventoryController.InventoryOperationOrderView;
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
public class InventoryQueryService extends WmsServiceSupport {

    public InventoryQueryService(WmsServiceDependencies dependencies) {
        super(dependencies);
    }

    public List<InventorySummaryView> getInventorySummary(String warehouseName,
                                                          String zoneName,
                                                          String materialKeyword,
                                                          Long supplierId) {
        return getInventoryLocationPage(null, warehouseName, zoneName, materialKeyword, supplierId, 1, 500).records();
    }

    public PageView<InventoryPartSummaryView> getInventoryPartSummaryPage(String warehouseName,
                                                                          String zoneName,
                                                                          String materialKeyword,
                                                                          Long supplierId,
                                                                          int page,
                                                                          int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<InventoryRepository.InventoryPartSummaryProjection> inventoryPage = inventoryRepository.searchPartSummary(
                normalize(warehouseName),
                normalize(zoneName),
                normalize(materialKeyword),
                supplierId,
                PageRequest.of(normalizedPage - 1, normalizedSize)
        );
        List<InventoryPartSummaryView> records = inventoryPage.getContent().stream()
                .map(item -> new InventoryPartSummaryView(
                        item.getPartId(),
                        item.getPartCode(),
                        item.getPartName(),
                        item.getSupplierId(),
                        defaultString(item.getSupplierName()),
                        defaultBigDecimal(item.getTotalQty()),
                        item.getLocationCount() == null ? 0L : item.getLocationCount(),
                        item.getLatestUpdatedAt()
                ))
                .toList();
        return new PageView<>(records, inventoryPage.getTotalElements(), normalizedPage, normalizedSize, inventoryPage.getTotalPages());
    }

    public PageView<InventorySummaryView> getInventoryLocationPage(String partCode,
                                                                  String warehouseName,
                                                                  String zoneName,
                                                                  String materialKeyword,
                                                                  Long supplierId,
                                                                  int page,
                                                                  int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<InventoryRepository.InventoryLocationProjection> inventoryPage = inventoryRepository.searchLocationRows(
                normalize(partCode),
                normalize(warehouseName),
                normalize(zoneName),
                normalize(materialKeyword),
                supplierId,
                PageRequest.of(normalizedPage - 1, normalizedSize)
        );
        List<InventorySummaryView> records = inventoryPage.getContent().stream()
                .map(item -> new InventorySummaryView(
                        item.getId(),
                        item.getPartId(),
                        item.getPartCode(),
                        item.getPartName(),
                        defaultString(item.getSupplierName()),
                        item.getLocationId(),
                        item.getLocationCode(),
                        item.getWarehouseName(),
                        item.getZoneName(),
                        defaultBigDecimal(item.getQty()),
                        item.getUpdatedAt()
                ))
                .toList();
        return new PageView<>(records, inventoryPage.getTotalElements(), normalizedPage, normalizedSize, inventoryPage.getTotalPages());
    }

    public PageView<KanbanView> getInventoryKanbanPage(String partCode,
                                                       String warehouseName,
                                                       String zoneName,
                                                       String kanbanNo,
                                                       Long supplierId,
                                                       int page,
                                                       int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<Kanban> kanbanPage = kanbanRepository.findAll(
                inventoryKanbanSpecification(partCode, warehouseName, zoneName, kanbanNo, supplierId),
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "inboundTime").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageView<>(
                toKanbanViews(kanbanPage.getContent(), false),
                kanbanPage.getTotalElements(),
                normalizedPage,
                normalizedSize,
                kanbanPage.getTotalPages()
        );
    }

    public List<InventoryTransactionView> getTransactions() {
        return getTransactionsPage(null, null, null, null, null, null, 1, 500).records();
    }

    public PageView<InventoryTransactionView> getTransactionsPage(String partCode,
                                                                  String businessType,
                                                                  String businessNo,
                                                                  String operationNo,
                                                                  String barcode,
                                                                  String locationCode,
                                                                  int page,
                                                                  int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<InventoryTransaction> transactionPage = inventoryTransactionRepository.searchTransactions(
                normalize(partCode),
                normalize(businessType),
                normalize(businessNo),
                normalize(operationNo),
                normalize(barcode),
                normalize(locationCode),
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );

        List<Long> partIds = transactionPage.getContent().stream().map(InventoryTransaction::getPartId).filter(Objects::nonNull).distinct().toList();
        List<Long> locationIds = transactionPage.getContent().stream().map(InventoryTransaction::getLocationId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Part> partMap = partRepository.findAllById(partIds).stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAllById(locationIds).stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        List<InventoryTransactionView> records = transactionPage.getContent().stream()
                .map(item -> new InventoryTransactionView(
                        item.getId(),
                        item.getTransactionNo(),
                        item.getBusinessType(),
                        item.getBusinessNo(),
                        item.getOperationNo(),
                        item.getBarcode(),
                        partMap.get(item.getPartId()) == null ? "-" : partMap.get(item.getPartId()).getPartCode(),
                        locationMap.get(item.getLocationId()) == null ? "-" : locationMap.get(item.getLocationId()).getLocationCode(),
                        item.getQtyChange(),
                        item.getRemark(),
                        item.getCreatedAt()
                ))
                .toList();
        return new PageView<>(records, transactionPage.getTotalElements(), normalizedPage, normalizedSize, transactionPage.getTotalPages());
    }

    public List<InventoryOperationOrderView> getOperationOrders(String operationNo) {
        return getOperationOrders(operationNo, null);
    }

    public List<InventoryOperationOrderView> getOperationOrders(String operationNo, String barcode) {
        String normalizedOperationNo = normalize(operationNo);
        if (normalizedOperationNo == null) {
            return List.of();
        }
        String normalizedBarcode = normalize(barcode);
        List<InventoryOperationOrder> orders = normalizedBarcode == null
                ? inventoryOperationOrderRepository.findByOperationNoOrderByIdAsc(normalizedOperationNo)
                : mergeOperationOrdersByBarcode(normalizedOperationNo, normalizedBarcode);
        List<Long> partIds = orders.stream().map(InventoryOperationOrder::getPartId).filter(Objects::nonNull).distinct().toList();
        List<Long> locationIds = orders.stream()
                .flatMap(item -> java.util.stream.Stream.of(item.getSourceLocationId(), item.getTargetLocationId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Part> partMap = partRepository.findAllById(partIds).stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAllById(locationIds).stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        return orders.stream()
                .map(item -> new InventoryOperationOrderView(
                        item.getId(),
                        item.getOperationNo(),
                        item.getOperationType(),
                        item.getBusinessNo(),
                        item.getSourceKanbanNo(),
                        item.getTargetKanbanNo(),
                        item.getSourceBarcode(),
                        item.getTargetBarcode(),
                        item.getPartId(),
                        partMap.get(item.getPartId()) == null ? "-" : partMap.get(item.getPartId()).getPartCode(),
                        locationMap.get(item.getSourceLocationId()) == null ? "-" : locationMap.get(item.getSourceLocationId()).getLocationCode(),
                        locationMap.get(item.getTargetLocationId()) == null ? "-" : locationMap.get(item.getTargetLocationId()).getLocationCode(),
                        item.getQty(),
                        item.getSourceStatus(),
                        item.getTargetStatus(),
                        item.getRemark(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    private List<InventoryOperationOrder> mergeOperationOrdersByBarcode(String operationNo, String barcode) {
        Map<Long, InventoryOperationOrder> orderMap = new java.util.LinkedHashMap<>();
        inventoryOperationOrderRepository.findByOperationNoAndSourceBarcodeOrderByIdAsc(operationNo, barcode)
                .forEach(item -> orderMap.put(item.getId(), item));
        inventoryOperationOrderRepository.findByOperationNoAndTargetBarcodeOrderByIdAsc(operationNo, barcode)
                .forEach(item -> orderMap.put(item.getId(), item));
        return orderMap.values().stream()
                .sorted(Comparator.comparing(InventoryOperationOrder::getId))
                .toList();
    }

    public InventoryTransactionVersionView getTransactionVersion() {
        InventoryTransaction latest = inventoryTransactionRepository.findFirstByOrderByIdDesc().orElse(null);
        return new InventoryTransactionVersionView(
                inventoryTransactionRepository.count(),
                latest == null ? null : latest.getId(),
                latest == null ? "" : latest.getTransactionNo(),
                latest == null ? null : latest.getCreatedAt()
        );
    }
}
