/**
 * 本文件实现仓储核心业务，支持父看板与箱级子看板的生成、扫码入库和扫码出库。
 */
package com.example.wms.service;

import com.example.wms.api.InventoryController.InventorySummaryView;
import com.example.wms.api.InventoryController.InventoryTransactionView;
import com.example.wms.api.InventoryController.FreezeKanbanRequest;
import com.example.wms.api.InventoryController.KanbanBalanceRequest;
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
import com.example.wms.api.ScanController.ScanInboundRequest;
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
import com.example.wms.repo.OutboundOrderItemRepository;
import com.example.wms.repo.OutboundOrderRepository;
import com.example.wms.repo.PartRepository;
import com.example.wms.repo.SupplierRepository;
import jakarta.transaction.Transactional;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WmsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final EquipmentRepository equipmentRepository;
    private final PartRepository partRepository;
    private final LocationRepository locationRepository;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;
    private final OutboundOrderRepository outboundOrderRepository;
    private final OutboundOrderItemRepository outboundOrderItemRepository;
    private final KanbanRepository kanbanRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public WmsService(SupplierRepository supplierRepository,
                      CustomerRepository customerRepository,
                      EquipmentRepository equipmentRepository,
                      PartRepository partRepository,
                      LocationRepository locationRepository,
                      InboundOrderRepository inboundOrderRepository,
                      InboundOrderItemRepository inboundOrderItemRepository,
                      OutboundOrderRepository outboundOrderRepository,
                      OutboundOrderItemRepository outboundOrderItemRepository,
                      KanbanRepository kanbanRepository,
                      InventoryRepository inventoryRepository,
                      InventoryTransactionRepository inventoryTransactionRepository) {
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.equipmentRepository = equipmentRepository;
        this.partRepository = partRepository;
        this.locationRepository = locationRepository;
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
        this.outboundOrderRepository = outboundOrderRepository;
        this.outboundOrderItemRepository = outboundOrderItemRepository;
        this.kanbanRepository = kanbanRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
    }

    @Transactional
    public InboundOrderView createInboundOrder(InboundOrderCreateRequest request) {
        supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException("??????"));
        if (request.items().isEmpty()) {
            throw new BusinessException("???????????");
        }

        InboundOrder order = new InboundOrder();
        order.setInboundNo("IN" + LocalDateTime.now().format(TS));
        order.setSupplierId(request.supplierId());
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = inboundOrderRepository.save(order);

        for (InboundOrderItemRequest itemRequest : request.items()) {
            Part part = partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("??????" + itemRequest.partId()));
            if (part.getSupplierId() != null && !part.getSupplierId().equals(request.supplierId())) {
                throw new BusinessException("???????????" + part.getPartCode());
            }
            InboundOrderItem item = new InboundOrderItem();
            item.setInboundOrderId(order.getId());
            item.setPartId(itemRequest.partId());
            item.setPlannedQty(itemRequest.plannedQty());
            item.setReceivedQty(BigDecimal.ZERO);
            item.setBoxCount(itemRequest.boxCount());
            item.setPendingRepack(itemRequest.pendingRepack());
            item.setEquipmentCode(itemRequest.equipmentCode());
            item.setUnitPerBox(calculateUnitPerBox(itemRequest.plannedQty(), itemRequest.boxCount()));
            item.setWarehouseZone(itemRequest.warehouseZone());
            inboundOrderItemRepository.save(item);
        }

        generateKanbans(order.getId());

        return toInboundOrderView(order);
    }

    public List<InboundOrderView> listInboundOrders(String status, Long supplierId, String inboundNo) {
        return inboundOrderRepository.findAll().stream()
                .filter(order -> isBlank(status) || status.equalsIgnoreCase(order.getStatus()))
                .filter(order -> supplierId == null || supplierId.equals(order.getSupplierId()))
                .filter(order -> containsIgnoreCase(order.getInboundNo(), inboundNo))
                .sorted(Comparator.comparing(InboundOrder::getCreatedAt).reversed())
                .map(this::toInboundOrderView)
                .toList();
    }

    @Transactional
    public List<KanbanView> generateKanbans(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("??????"));

        List<Kanban> existing = kanbanRepository.findByInboundOrderId(inboundOrderId);
        if (!existing.isEmpty()) {
            return existing.stream().map(this::toKanbanView).toList();
        }

        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);
        if (items.isEmpty()) {
            throw new BusinessException("???????");
        }

        int sequence = 1;
        for (InboundOrderItem item : items) {
            Kanban parent = createBaseKanban(order, item);
            parent.setKanbanNo("KB" + LocalDateTime.now().format(TS) + String.format("%02d", sequence++));
            parent.setBarcode("BC-P-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
            parent.setQrContent(buildQrContent(parent.getKanbanNo(), parent.getBarcode()));
            parent.setQty(item.getPlannedQty());
            parent.setParentKanban(true);
            parent.setBoxIndex(0);
            parent = kanbanRepository.save(parent);

            BigDecimal childQty = defaultBigDecimal(item.getUnitPerBox());
            for (int boxIndex = 1; boxIndex <= item.getBoxCount(); boxIndex++) {
                Kanban child = createBaseKanban(order, item);
                child.setParentKanbanId(parent.getId());
                child.setParentKanban(false);
                child.setBoxIndex(boxIndex);
                child.setKanbanNo(parent.getKanbanNo() + "-" + String.format("%02d", boxIndex));
                child.setBarcode("BC-C-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
                child.setQrContent(buildQrContent(child.getKanbanNo(), child.getBarcode()));
                child.setQty(childQty);
                kanbanRepository.save(child);
            }
        }
        return kanbanRepository.findByInboundOrderId(inboundOrderId).stream().map(this::toKanbanView).toList();
    }

    public List<KanbanView> listKanbans(String status,
                                        String inboundNo,
                                        String outboundNo,
                                        String kanbanNo,
                                        Long supplierId,
                                        String partCode) {
        return kanbanRepository.findAll().stream()
                .map(this::toKanbanView)
                .filter(view -> isBlank(status) || status.equalsIgnoreCase(view.status()))
                .filter(view -> containsIgnoreCase(view.inboundNo(), inboundNo))
                .filter(view -> containsIgnoreCase(view.outboundNo(), outboundNo))
                .filter(view -> containsIgnoreCase(view.kanbanNo(), kanbanNo))
                .filter(view -> supplierId == null || supplierId.equals(view.supplierId()))
                .filter(view -> containsIgnoreCase(view.partCode(), partCode))
                .sorted(Comparator.comparing(KanbanView::createdAt).reversed())
                .toList();
    }

    @Transactional
    public OutboundOrderView createOutboundOrder(OutboundOrderCreateRequest request) {
        if (request.customerId() != null) {
            customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new NotFoundException("?????"));
        }
        if (request.items().isEmpty()) {
            throw new BusinessException("???????????");
        }
        List<String> inboundOrderNos = normalizeInboundOrderNos(request.inboundOrderNos());
        if (inboundOrderNos.isEmpty()) {
            throw new BusinessException("??????????????");
        }
        inboundOrderNos.forEach(inboundNo -> inboundOrderRepository.findAll().stream()
                .filter(order -> inboundNo.equalsIgnoreCase(order.getInboundNo()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("??????: " + inboundNo)));

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo("OUT" + LocalDateTime.now().format(TS));
        order.setCustomerId(request.customerId());
        order.setInboundOrderNos(String.join(",", inboundOrderNos));
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = outboundOrderRepository.save(order);

        for (OutboundOrderItemRequest itemRequest : request.items()) {
            partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("??????" + itemRequest.partId()));
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundOrderId(order.getId());
            item.setPartId(itemRequest.partId());
            item.setPlannedQty(itemRequest.plannedQty());
            item.setScannedQty(BigDecimal.ZERO);
            item.setWarehouseName(itemRequest.warehouseName());
            item.setZoneName(itemRequest.zoneName());
            outboundOrderItemRepository.save(item);
        }

        return toOutboundOrderView(order);
    }

    public List<OutboundOrderView> listOutboundOrders(String status, Long customerId, String outboundNo) {
        return outboundOrderRepository.findAll().stream()
                .filter(order -> isBlank(status) || status.equalsIgnoreCase(order.getStatus()))
                .filter(order -> customerId == null || customerId.equals(order.getCustomerId()))
                .filter(order -> containsIgnoreCase(order.getOutboundNo(), outboundNo))
                .sorted(Comparator.comparing(OutboundOrder::getCreatedAt).reversed())
                .map(this::toOutboundOrderView)
                .toList();
    }

    @Transactional
    public ScanResultView scanInbound(ScanInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("?????"));
        List<Kanban> targets = resolveScanTargets(kanban);
        for (Kanban target : targets) {
            ensureInboundAllowed(target);
            applyInboundForSingleKanban(target, location);
        }
        refreshParentKanbanState(kanban);
        syncInboundOrderStatus(kanban.getInboundOrderId());
        Kanban resultKanban = kanban.isParentKanban()
                ? kanbanRepository.findById(kanban.getId()).orElse(kanban)
                : kanbanRepository.findById(kanban.getId()).orElse(kanban);
        return new ScanResultView("INBOUND_OK", "????", resultKanban.getBarcode(), resultKanban.getStatus());
    }

    @Transactional
    public ScanResultView scanOutbound(ScanOutboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        String outboundOrderNo = normalize(request.outboundOrderNo());
        if (outboundOrderNo == null) {
            throw new BusinessException("????????");
        }
        OutboundOrder order = outboundOrderRepository.findAll().stream()
                .filter(item -> outboundOrderNo.equalsIgnoreCase(item.getOutboundNo()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("??????"));
        List<Kanban> targets = resolveScanTargets(kanban);
        for (Kanban target : targets) {
            ensureOutboundAllowed(target);
            requireKanbanMatchesOutboundSource(order, target);
            applyOutboundScanToOrder(order, target);
            applyOutboundForSingleKanban(order, target);
        }
        refreshParentKanbanState(kanban);
        Kanban resultKanban = kanbanRepository.findById(kanban.getId()).orElse(kanban);
        return new ScanResultView("OUTBOUND_OK", "????", resultKanban.getBarcode(), resultKanban.getStatus());
    }

    public List<InventorySummaryView> getInventorySummary(String warehouseName,
                                                          String zoneName,
                                                          String materialKeyword,
                                                          Long supplierId) {

        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAll().stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<String, String> supplierNameByPartLocation = new HashMap<>();
        Map<String, Long> supplierIdByPartLocation = new HashMap<>();

        for (Kanban kanban : kanbanRepository.findAll()) {
            if (!"INBOUND".equals(kanban.getStatus()) || kanban.getLocationId() == null) {
                continue;
            }
            String key = inventoryKey(kanban.getPartId(), kanban.getLocationId());
            if (!supplierNameByPartLocation.containsKey(key)) {
                supplierNameByPartLocation.put(
                        key,
                        supplierRepository.findById(kanban.getSupplierId()).map(Supplier::getSupplierName).orElse("-")
                );
                supplierIdByPartLocation.put(key, kanban.getSupplierId());
            }
        }

        return inventoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Inventory::getUpdatedAt).reversed())
                .map(item -> {
                    Part part = partMap.get(item.getPartId());
                    Location location = locationMap.get(item.getLocationId());
                    String key = inventoryKey(item.getPartId(), item.getLocationId());
                    return new InventorySummaryView(
                            item.getId(),
                            item.getPartId(),
                            part == null ? "-" : part.getPartCode(),
                            part == null ? "-" : part.getPartName(),
                            supplierNameByPartLocation.getOrDefault(key, "-"),
                            item.getLocationId(),
                            location == null ? "-" : location.getLocationCode(),
                            location == null ? "-" : location.getWarehouseName(),
                            location == null ? "-" : location.getZoneName(),
                            item.getQty(),
                            item.getUpdatedAt()
                    );
                })
                .filter(row -> containsIgnoreCase(row.warehouseName(), warehouseName))
                .filter(row -> containsIgnoreCase(row.zoneName(), zoneName))
                .filter(row -> containsIgnoreCase(row.partCode(), materialKeyword) || containsIgnoreCase(row.partName(), materialKeyword))
                .filter(row -> supplierId == null || supplierId.equals(supplierIdByPartLocation.get(inventoryKey(row.partId(), row.locationId()))))
                .toList();
    }

    @Transactional
    public InventorySummaryView manualInventoryEntry(ManualInventoryEntryRequest request) {
        partRepository.findById(request.partId())
                .orElseThrow(() -> new NotFoundException("?????"));
        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new NotFoundException("?????"));

        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(request.partId(), request.locationId())
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setPartId(request.partId());
                    created.setLocationId(request.locationId());
                    created.setQty(BigDecimal.ZERO);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });

        inventory.setQty(inventory.getQty().add(request.qty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventory = inventoryRepository.save(inventory);

        saveInventoryTransaction(
                request.partId(),
                request.locationId(),
                "MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.qty(),
                "MANUAL_ENTRY",
                "MANUAL-" + LocalDateTime.now().format(TS),
                request.remark()
        );

        Part part = partRepository.findById(request.partId()).orElse(null);
        return new InventorySummaryView(
                inventory.getId(),
                request.partId(),
                part == null ? "-" : part.getPartCode(),
                part == null ? "-" : part.getPartName(),
                "-",
                location.getId(),
                location.getLocationCode(),
                location.getWarehouseName(),
                location.getZoneName(),
                inventory.getQty(),
                inventory.getUpdatedAt()
        );
    }

    @Transactional
    public KanbanView transferKanban(TransferKanbanRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        requireKanbanMatchesInboundNo(kanban, request.inboundOrderNo());
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("???????"));
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("?????????????");
        }

        Long sourceLocationId = kanban.getLocationId();
        moveInventory(kanban.getPartId(), sourceLocationId, kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, sourceLocationId, kanban.getQty().negate(), "TRANSFER_OUT", defaultRemark(request.remark(), "????"));

        kanban.setLocationId(targetLocation.getId());
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "TRANSFER_IN", defaultRemark(request.remark(), "????"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView freezeKanban(FreezeKanbanRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!List.of("INBOUND", "FROZEN").contains(kanban.getStatus())) {
            throw new BusinessException("??????????????");
        }
        if (request.frozen()) {
            if (kanban.getLocationId() == null) {
                throw new BusinessException("?????????");
            }
            kanban.setFrozen(true);
            kanban.setStatus("FROZEN");
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "FREEZE", defaultRemark(request.remark(), "?????"));
        } else {
            kanban.setFrozen(false);
            kanban.setStatus("INBOUND");
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "UNFREEZE", defaultRemark(request.remark(), "?????"));
        }
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackOutbound(RepackOutboundRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        Location sourceLocation = locationRepository.findById(kanban.getLocationId())
                .orElseThrow(() -> new NotFoundException("???????"));
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("???????"));
        requireWarehouseType(sourceLocation, "OWN", "???? must start from own warehouse");
        requireWarehouseType(targetLocation, "THIRD_PARTY", "???? target must be third-party warehouse");
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("?????????????");
        }

        moveInventory(kanban.getPartId(), kanban.getLocationId(), kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, kanban.getLocationId(), kanban.getQty().negate(), "REPACK_OUT", defaultRemark(request.remark(), "????"));
        kanban.setLocationId(targetLocation.getId());
        kanban.setStatus("REPACK_OUTBOUND");
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "REPACK_THIRD_IN", defaultRemark(request.remark(), "??????"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackInbound(RepackInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!"REPACK_OUTBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("??????????????");
        }
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("?????"));
        requireWarehouseType(location, "OWN", "???? target must be own warehouse");

        Long thirdPartyLocationId = kanban.getLocationId();
        Location thirdPartyLocation = locationRepository.findById(thirdPartyLocationId)
                .orElseThrow(() -> new NotFoundException("????????"));
        requireWarehouseType(thirdPartyLocation, "THIRD_PARTY", "???? must start from third-party warehouse");

        BigDecimal thirdPartyQty = kanban.getQty();
        kanban.setQty(request.qty());
        kanban.setLocationId(location.getId());
        kanban.setStatus("INBOUND");
        kanban.setFrozen(false);
        kanban.setInboundTime(LocalDateTime.now());
        kanban = kanbanRepository.save(kanban);
        moveInventory(kanban.getPartId(), thirdPartyLocationId, thirdPartyQty.negate());
        moveInventory(kanban.getPartId(), location.getId(), kanban.getQty());
        saveTransaction(kanban, thirdPartyLocationId, thirdPartyQty.negate(), "REPACK_THIRD_OUT", defaultRemark(request.remark(), "??????"));
        saveTransaction(kanban, location.getId(), kanban.getQty(), "REPACK_IN", defaultRemark(request.remark(), "????"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView adjustKanbanBalance(KanbanBalanceRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        BigDecimal delta = request.qty().subtract(kanban.getQty());
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            moveInventory(kanban.getPartId(), kanban.getLocationId(), delta);
        }
        kanban.setQty(request.qty());
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, kanban.getLocationId(), delta, "BALANCE_ADJUST", defaultRemark(request.remark(), "???????"));
        return toKanbanView(kanban);
    }

    public List<InventoryTransactionView> getTransactions() {

        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAll().stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        return inventoryTransactionRepository.findAll().stream()
                .sorted(Comparator.comparing(InventoryTransaction::getCreatedAt).reversed())
                .map(item -> new InventoryTransactionView(
                        item.getId(),
                        item.getTransactionNo(),
                        item.getBusinessType(),
                        item.getBusinessNo(),
                        item.getBarcode(),
                        partMap.get(item.getPartId()) == null ? "-" : partMap.get(item.getPartId()).getPartCode(),
                        locationMap.get(item.getLocationId()) == null ? "-" : locationMap.get(item.getLocationId()).getLocationCode(),
                        item.getQtyChange(),
                        item.getRemark(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    private Kanban findOperableStockKanban(String barcode) {
        Kanban kanban = findKanbanByScanCode(barcode);
        if (!"INBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("????????????");
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("?????????");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("?????????");
        }
        return kanban;
    }

    private Kanban createBaseKanban(InboundOrder order, InboundOrderItem item) {
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

    private List<Kanban> resolveScanTargets(Kanban kanban) {
        if (kanban.isParentKanban()) {
            return kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(kanban.getId());
        }
        return List.of(kanban);
    }

    private void ensureInboundAllowed(Kanban kanban) {
        if (!List.of("WAIT_SCAN", "CREATED").contains(kanban.getStatus())) {
            if ("INBOUND".equals(kanban.getStatus())) {
                throw new BusinessException("?? " + kanban.getKanbanNo() + " ????????????");
            }
            if ("OUTBOUND".equals(kanban.getStatus())) {
                throw new BusinessException("?? " + kanban.getKanbanNo() + " ??????????");
            }
            throw new BusinessException("?? " + kanban.getKanbanNo() + " ??????????" + kanban.getStatus());
        }
    }

    private void ensureOutboundAllowed(Kanban kanban) {
        if (!"INBOUND".equals(kanban.getStatus())) {
            if ("OUTBOUND".equals(kanban.getStatus())) {
                throw new BusinessException("?? " + kanban.getKanbanNo() + " ????????????");
            }
            if (List.of("WAIT_SCAN", "CREATED", "PARTIAL").contains(kanban.getStatus())) {
                throw new BusinessException("?? " + kanban.getKanbanNo() + " ???????????");
            }
            throw new BusinessException("?? " + kanban.getKanbanNo() + " ??????????");
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("?? " + kanban.getKanbanNo() + " ????????");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("?? " + kanban.getKanbanNo() + " ???????????");
        }
    }

    private void applyInboundForSingleKanban(Kanban kanban, Location location) {
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
        kanban.setStatus("INBOUND");
        kanban.setInboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("Inbound order item not found"));
        item.setReceivedQty(item.getReceivedQty().add(kanban.getQty()));
        inboundOrderItemRepository.save(item);

        saveTransaction(kanban, location.getId(), kanban.getQty(), "INBOUND_SCAN", "??????");
    }

    private void applyOutboundForSingleKanban(OutboundOrder order, Kanban kanban) {
        kanban.setOutboundOrderNo(order.getOutboundNo());
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), kanban.getLocationId())
                .orElseThrow(() -> new BusinessException("???????"));
        if (inventory.getQty().compareTo(kanban.getQty()) < 0) {
            throw new BusinessException("?????????");
        }

        inventory.setQty(inventory.getQty().subtract(kanban.getQty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        kanban.setStatus("OUTBOUND");
        kanban.setOutboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        saveInventoryTransaction(
                kanban.getPartId(),
                kanban.getLocationId(),
                kanban.getBarcode(),
                kanban.getQty().negate(),
                "OUTBOUND_SCAN",
                order.getOutboundNo(),
                "Outbound scan completed"
        );
    }

    private void refreshParentKanbanState(Kanban scannedKanban) {
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

        boolean allInbound = children.stream().allMatch(item -> "INBOUND".equals(item.getStatus()));
        boolean allOutbound = children.stream().allMatch(item -> "OUTBOUND".equals(item.getStatus()));
        boolean anyInboundLike = children.stream().anyMatch(item -> List.of("INBOUND", "OUTBOUND", "FROZEN", "REPACK_OUTBOUND").contains(item.getStatus()));

        if (allOutbound) {
            parent.setStatus("OUTBOUND");
            parent.setOutboundTime(children.stream().map(Kanban::getOutboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        } else if (allInbound) {
            parent.setStatus("INBOUND");
            parent.setInboundTime(children.stream().map(Kanban::getInboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
            parent.setLocationId(children.get(0).getLocationId());
        } else if (anyInboundLike) {
            parent.setStatus("PARTIAL");
        } else {
            parent.setStatus("WAIT_SCAN");
        }
        kanbanRepository.save(parent);
    }

    private Kanban findKanbanByScanCode(String scanCode) {
        String normalized = normalize(scanCode);
        if (normalized == null) {
            throw new NotFoundException("???????????");
        }
        return kanbanRepository.findByBarcode(normalized)
                .or(() -> kanbanRepository.findByQrContent(normalized))
                .or(() -> {
                    String[] parts = normalized.split("\\|");
                    if (parts.length == 3 && "WMS-KANBAN".equals(parts[0])) {
                        return kanbanRepository.findByBarcode(parts[2]);
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> new NotFoundException("???????????"));
    }

    private String buildQrContent(String kanbanNo, String barcode) {
        return "WMS-KANBAN|" + kanbanNo + "|" + barcode;
    }

    private Inventory moveInventory(Long partId, Long locationId, BigDecimal qtyChange) {
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
            throw new BusinessException("?????????????");
        }
        inventory.setQty(nextQty);
        inventory.setUpdatedAt(LocalDateTime.now());
        return inventoryRepository.save(inventory);
    }

    private String defaultRemark(String remark, String fallback) {
        String normalized = normalize(remark);
        return normalized == null ? fallback : normalized;
    }

    private void requireWarehouseType(Location location, String expectedType, String message) {
        if (!expectedType.equals(normalizeWarehouseType(location.getWarehouseType()))) {
            throw new BusinessException(message);
        }
    }

    private String normalizeWarehouseType(String value) {
        if (value == null || value.isBlank()) {
            return "OWN";
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of("OWN", "THIRD_PARTY").contains(normalized)) {
            return "OWN";
        }
        return normalized;
    }

    private BigDecimal calculateUnitPerBox(BigDecimal plannedQty, Integer boxCount) {
        if (plannedQty == null || boxCount == null || boxCount <= 0) {
            throw new BusinessException("???????????");
        }
        return plannedQty.divide(BigDecimal.valueOf(boxCount), 3, RoundingMode.HALF_UP);
    }

    private void applyOutboundScanToOrder(OutboundOrder order, Kanban kanban) {
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(order.getId());
        List<Kanban> fifoCandidates = kanbanRepository.findAll().stream()
                .filter(item -> "INBOUND".equals(item.getStatus()))
                .filter(item -> !item.isFrozen())
                .filter(item -> item.getLocationId() != null)
                .filter(item -> item.getPartId().equals(kanban.getPartId()))
                .filter(item -> {
                    InboundOrder inboundOrder = inboundOrderRepository.findById(item.getInboundOrderId()).orElse(null);
                    return inboundOrder != null && splitCsv(order.getInboundOrderNos()).stream()
                            .anyMatch(source -> source.equalsIgnoreCase(inboundOrder.getInboundNo()));
                })
                .sorted(Comparator
                        .comparing(Kanban::getInboundTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Kanban::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Kanban::getId))
                .toList();
        if (!fifoCandidates.isEmpty() && !fifoCandidates.get(0).getId().equals(kanban.getId())) {
            throw new BusinessException("??????????????????????");
        }

        Location location = kanban.getLocationId() == null ? null : locationRepository.findById(kanban.getLocationId()).orElse(null);
        OutboundOrderItem target = items.stream()
                .filter(item -> item.getPartId().equals(kanban.getPartId()))
                .filter(item -> location == null || defaultString(item.getWarehouseName()).equals(defaultString(location.getWarehouseName())))
                .filter(item -> location == null || defaultString(item.getZoneName()).equals(defaultString(location.getZoneName())))
                .filter(item -> item.getScannedQty().add(kanban.getQty()).compareTo(item.getPlannedQty()) <= 0)
                .filter(item -> item.getScannedQty().compareTo(item.getPlannedQty()) < 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException("???????????????????????????"));

        target.setScannedQty(target.getScannedQty().add(kanban.getQty()));
        outboundOrderItemRepository.save(target);

        syncOutboundOrderStatus(order.getId());
    }

    private void requireKanbanMatchesOutboundSource(OutboundOrder order, Kanban kanban) {
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("??????"));
        List<String> sources = splitCsv(order.getInboundOrderNos());
        if (sources.isEmpty()) {
            throw new BusinessException("???????????");
        }
        boolean matched = sources.stream().anyMatch(item -> item.equalsIgnoreCase(inboundOrder.getInboundNo()));
        if (!matched) {
            throw new BusinessException("??????????????????");
        }
    }

    private void requireKanbanMatchesInboundNo(Kanban kanban, String inboundOrderNo) {
        String sourceInboundNo = normalize(inboundOrderNo);
        if (sourceInboundNo == null) {
            throw new BusinessException("????????");
        }
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("??????"));
        if (!sourceInboundNo.equalsIgnoreCase(inboundOrder.getInboundNo())) {
            throw new BusinessException("??????????????????");
        }
    }

    private List<String> normalizeInboundOrderNos(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private List<String> splitCsv(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return List.of();
        }
        return java.util.Arrays.stream(normalized.split(","))
                .map(this::normalize)
                .filter(item -> item != null)
                .toList();
    }

    private void syncInboundOrderStatus(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("??????"));
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);

        boolean completed = items.stream().allMatch(item -> item.getReceivedQty().compareTo(item.getPlannedQty()) >= 0);

        boolean partial = items.stream().anyMatch(item -> item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);

        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        inboundOrderRepository.save(order);
    }

    private void syncOutboundOrderStatus(Long outboundOrderId) {
        OutboundOrder order = outboundOrderRepository.findById(outboundOrderId)
                .orElseThrow(() -> new NotFoundException("??????"));
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(outboundOrderId);

        boolean completed = items.stream().allMatch(item -> item.getScannedQty().compareTo(item.getPlannedQty()) >= 0);

        boolean partial = items.stream().anyMatch(item -> item.getScannedQty().compareTo(BigDecimal.ZERO) > 0);

        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        outboundOrderRepository.save(order);
    }

    private void saveTransaction(Kanban kanban, Long locationId, BigDecimal qtyChange, String businessType, String remark) {
        saveInventoryTransaction(
                kanban.getPartId(),
                locationId,
                kanban.getBarcode(),
                qtyChange,
                businessType,
                kanban.getKanbanNo(),
                remark
        );
    }

    private void saveInventoryTransaction(Long partId,
                                          Long locationId,
                                          String barcode,
                                          BigDecimal qtyChange,
                                          String businessType,
                                          String businessNo,
                                          String remark) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setTransactionNo("TX" + LocalDateTime.now().format(TS) + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        transaction.setPartId(partId);
        transaction.setLocationId(locationId);
        transaction.setBarcode(barcode);
        transaction.setQtyChange(qtyChange);
        transaction.setBusinessType(businessType);
        transaction.setBusinessNo(businessNo);
        transaction.setRemark(remark);
        transaction.setCreatedAt(LocalDateTime.now());
        inventoryTransactionRepository.save(transaction);
    }

    private InboundOrderView toInboundOrderView(InboundOrder order) {

        String supplierName = supplierRepository.findById(order.getSupplierId())
                .map(Supplier::getSupplierName)
                .orElse("-");
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(order.getId());

        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        return new InboundOrderView(
                order.getId(),
                order.getInboundNo(),
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

    private OutboundOrderView toOutboundOrderView(OutboundOrder order) {

        String customerName = order.getCustomerId() == null ? "-" :
                customerRepository.findById(order.getCustomerId()).map(Customer::getCustomerName).orElse("-");

        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(order.getId());
        return new OutboundOrderView(
                order.getId(),
                order.getOutboundNo(),
                order.getCustomerId(),
                customerName,
                splitCsv(order.getInboundOrderNos()),
                order.getStatus(),
                order.getCreatedAt(),
                items.stream().map(item -> {
                    Part part = partMap.get(item.getPartId());
                    return new OutboundOrderView.ItemView(
                            item.getId(),
                            item.getPartId(),
                            part == null ? "-" : part.getPartCode(),
                            part == null ? "-" : part.getPartName(),
                            part == null ? "-" : part.getUnit(),
                            item.getPlannedQty(),
                            item.getScannedQty(),
                            defaultString(item.getWarehouseName()),
                            defaultString(item.getZoneName())
                    );
                }).toList()
        );
    }

    private KanbanView toKanbanView(Kanban kanban) {
        Part part = partRepository.findById(kanban.getPartId()).orElse(null);
        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId()).orElse(null);
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId()).orElse(null);
        Location location = kanban.getLocationId() == null ? null : locationRepository.findById(kanban.getLocationId()).orElse(null);
        Supplier supplier = inboundOrder == null ? null : supplierRepository.findById(inboundOrder.getSupplierId()).orElse(null);
        Equipment equipment = item == null || isBlank(item.getEquipmentCode()) ? null :
                equipmentRepository.findByEquipmentCode(item.getEquipmentCode()).orElse(null);

        String[] plannedZone = splitWarehouseZone(item == null ? null : item.getWarehouseZone());
        String warehouseName = location != null ? location.getWarehouseName() : plannedZone[0];
        String zoneName = location != null ? location.getZoneName() : plannedZone[1];

        List<KanbanView> children = kanban.isParentKanban()
                ? kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(kanban.getId()).stream().map(this::toKanbanView).toList()
                : List.of();

        return new KanbanView(
                kanban.getId(),
                kanban.getKanbanNo(),
                kanban.getBarcode(),
                defaultString(kanban.getQrContent()).equals("-") ? buildQrContent(kanban.getKanbanNo(), kanban.getBarcode()) : kanban.getQrContent(),
                kanban.getParentKanbanId(),
                kanban.isParentKanban(),
                kanban.getBoxIndex(),
                inboundOrder == null ? "-" : inboundOrder.getInboundNo(),
                defaultString(kanban.getOutboundOrderNo()),
                part == null ? "-" : part.getPartCode(),
                part == null ? "-" : part.getPartName(),
                part == null ? "-" : part.getUnit(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? "-" : supplier.getSupplierName(),
                defaultString(kanban.getBatchNo()),
                kanban.getQty(),
                item == null ? 0 : item.getBoxCount(),
                item != null && item.isPendingRepack(),
                item == null ? "" : defaultString(item.getEquipmentCode()),
                equipment == null ? "" : defaultString(equipment.getEquipmentModel()),
                item == null ? BigDecimal.ZERO : defaultBigDecimal(item.getUnitPerBox()),
                warehouseName,
                zoneName,
                kanban.getStatus(),
                location == null ? "-" : location.getLocationCode(),
                kanban.getCreatedAt(),
                kanban.getInboundTime(),
                kanban.getOutboundTime(),
                children
        );
    }

    private String inventoryKey(Long partId, Long locationId) {
        return partId + ":" + locationId;
    }

    private String[] splitWarehouseZone(String text) {
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

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsIgnoreCase(String text, String query) {
        if (isBlank(query)) {
            return true;
        }
        return defaultString(text).toLowerCase().contains(query.trim().toLowerCase());
    }

    private String defaultString(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
