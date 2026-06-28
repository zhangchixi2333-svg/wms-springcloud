/**
 * 本文件实现仓储核心业务，支持父看板与箱级子看板的生成、扫码入库和扫码出库。
 */
package com.example.wms.service;

import com.example.wms.api.InventoryController.InventorySummaryView;
import com.example.wms.api.InventoryController.InventoryTransactionView;
import com.example.wms.api.InventoryController.InventoryTransactionVersionView;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WmsService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STATUS_CREATED = "CREATED";
    private static final String STATUS_WAIT_SCAN = "WAIT_SCAN";
    private static final String STATUS_INBOUND = "INBOUND";
    private static final String STATUS_OUTBOUND = "OUTBOUND";
    private static final String STATUS_ALLOCATED = "ALLOCATED";
    private static final String STATUS_FROZEN = "FROZEN";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_PARTIAL_INBOUND = "PARTIAL_INBOUND";
    private static final String STATUS_PARTIAL_OUTBOUND = "PARTIAL_OUTBOUND";
    private static final String STATUS_REPACK_OUTBOUND = "REPACK_OUTBOUND";
    private static final String STATUS_REPACK_INBOUND = "REPACK_INBOUND";
    private static final Set<String> INBOUND_PENDING_STATUSES = Set.of(STATUS_WAIT_SCAN, STATUS_CREATED);
    private static final Set<String> OUTBOUND_PENDING_STATUSES = Set.of(STATUS_ALLOCATED, STATUS_INBOUND);
    private static final Set<String> NOT_FULLY_INBOUND_STATUSES = Set.of(STATUS_WAIT_SCAN, STATUS_CREATED, STATUS_PARTIAL, STATUS_PARTIAL_INBOUND);
    private static final Set<String> INBOUND_LIKE_STATUSES = Set.of(STATUS_INBOUND, STATUS_FROZEN, STATUS_REPACK_OUTBOUND, STATUS_REPACK_INBOUND);

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
                .orElseThrow(() -> new NotFoundException("供应商不存在"));
        if (request.items().isEmpty()) {
            throw new BusinessException("入库单至少需要一条明细");
        }

        InboundOrder order = new InboundOrder();
        order.setInboundNo("IN" + LocalDateTime.now().format(TS));
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
                                        String partCode,
                                        boolean includeChildren) {
        List<Kanban> source = includeChildren ? kanbanRepository.findAll() : kanbanRepository.findParentKanbans();
        return toKanbanViews(source, includeChildren).stream()
                .filter(view -> isBlank(status) || status.equalsIgnoreCase(view.status()))
                .filter(view -> containsIgnoreCase(view.inboundNo(), inboundNo))
                .filter(view -> containsIgnoreCase(view.outboundNo(), outboundNo))
                .filter(view -> containsIgnoreCase(view.kanbanNo(), kanbanNo))
                .filter(view -> supplierId == null || supplierId.equals(view.supplierId()))
                .filter(view -> containsIgnoreCase(view.partCode(), partCode))
                .sorted(Comparator.comparing(KanbanView::createdAt).reversed())
                .toList();
    }

    public List<KanbanView> listKanbanChildren(Long parentId) {
        Kanban parent = kanbanRepository.findById(parentId)
                .orElseThrow(() -> new NotFoundException("父看板不存在：" + parentId));
        if (!parent.isParentKanban()) {
            throw new BusinessException("只有父看板可以展开子看板");
        }
        return toKanbanViews(kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(parentId), false);
    }

    @Transactional
    public OutboundOrderView createOutboundOrder(OutboundOrderCreateRequest request) {
        if (request.customerId() != null) {
            customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new NotFoundException("客户不存在：" + request.customerId()));
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("创建出库单必须选择要出库的零件和箱数");
        }

        List<Kanban> selectedBoxes = allocateOutboundBoxes(request.items());
        List<String> inboundOrderNos = selectedBoxes.stream()
                .map(kanban -> inboundOrderRepository.findById(kanban.getInboundOrderId())
                        .map(InboundOrder::getInboundNo)
                        .orElse("-"))
                .distinct()
                .toList();

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo("OUT" + LocalDateTime.now().format(TS));
        order.setCustomerId(request.customerId());
        order.setInboundOrderNos(String.join(",", inboundOrderNos));
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = outboundOrderRepository.save(order);

        for (Kanban kanban : selectedBoxes) {
            Location location = locationRepository.findById(kanban.getLocationId())
                    .orElseThrow(() -> new NotFoundException("看板库位不存在：" + kanban.getKanbanNo()));
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundOrderId(order.getId());
            item.setKanbanId(kanban.getId());
            item.setKanbanNo(kanban.getKanbanNo());
            item.setPartId(kanban.getPartId());
            item.setPlannedQty(kanban.getQty());
            item.setScannedQty(BigDecimal.ZERO);
            item.setWarehouseName(location.getWarehouseName());
            item.setZoneName(location.getZoneName());
            outboundOrderItemRepository.save(item);

            kanban.setOutboundOrderNo(order.getOutboundNo());
            kanban.setStatus("ALLOCATED");
            kanbanRepository.save(kanban);
            refreshParentKanbanState(kanban);
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
        List<Kanban> targets = resolveInboundScanTargets(kanban);
        Location location = resolveInboundLocation(request.locationCode(), kanban, targets);
        for (Kanban target : targets) {
            ensureInboundAllowed(target);
            applyInboundForSingleKanban(target, location);
        }
        refreshParentKanbanState(kanban);
        refreshParentKanbanStates(targets);
        syncInboundOrderStatus(kanban.getInboundOrderId());
        Kanban resultKanban = kanban.isParentKanban()
                ? kanbanRepository.findById(kanban.getId()).orElse(kanban)
                : kanbanRepository.findById(kanban.getId()).orElse(kanban);
        return new ScanResultView(
                "INBOUND_OK",
                "扫码入库成功，已处理 " + targets.size() + " 箱",
                resultKanban.getBarcode(),
                resultKanban.getKanbanNo(),
                parentKanbanNo(resultKanban),
                null,
                resultKanban.getStatus(),
                targets.size(),
                targets.stream().map(Kanban::getKanbanNo).toList()
        );
    }

    @Transactional
    public ScanResultView scanOutbound(ScanOutboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        String outboundOrderNo = resolveOutboundOrderNo(request.outboundOrderNo(), kanban);
        if (outboundOrderNo == null) {
            throw new BusinessException("请选择出库单后再扫码出库");
        }
        OutboundOrder order = outboundOrderRepository.findByOutboundNoIgnoreCase(outboundOrderNo)
                .orElseThrow(() -> new NotFoundException("出库单不存在：" + outboundOrderNo));
        List<Kanban> targets = resolveOutboundScanTargets(kanban, order);
        for (Kanban target : targets) {
            ensureOutboundAllowed(target);
            applyOutboundScanToOrder(order, target);
            applyOutboundForSingleKanban(order, target);
        }
        refreshParentKanbanState(kanban);
        refreshParentKanbanStates(targets);
        Kanban resultKanban = kanbanRepository.findById(kanban.getId()).orElse(kanban);
        syncOutboundOrderStatus(order.getId());
        return new ScanResultView(
                "OUTBOUND_OK",
                "扫码出库成功，已处理 " + targets.size() + " 箱，出库单 " + order.getOutboundNo(),
                resultKanban.getBarcode(),
                resultKanban.getKanbanNo(),
                parentKanbanNo(resultKanban),
                order.getOutboundNo(),
                resultKanban.getStatus(),
                targets.size(),
                targets.stream().map(Kanban::getKanbanNo).toList()
        );
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
                .orElseThrow(() -> new NotFoundException("零件不存在"));
        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new NotFoundException("库位不存在"));

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
                .orElseThrow(() -> new NotFoundException("目标库位不存在"));
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("目标库位与当前库位相同，无需转存");
        }

        Long sourceLocationId = kanban.getLocationId();
        moveInventory(kanban.getPartId(), sourceLocationId, kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, sourceLocationId, kanban.getQty().negate(), "TRANSFER_OUT", defaultRemark(request.remark(), "转存出库"));

        kanban.setLocationId(targetLocation.getId());
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "TRANSFER_IN", defaultRemark(request.remark(), "转存入库"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView freezeKanban(FreezeKanbanRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!Set.of(STATUS_INBOUND, STATUS_FROZEN).contains(kanban.getStatus())) {
            throw new BusinessException("只有已入库或已封存的看板可以执行封存操作");
        }
        if (request.frozen()) {
            if (kanban.getLocationId() == null) {
                throw new BusinessException("看板没有库存库位，不能封存");
            }
            kanban.setFrozen(true);
            kanban.setStatus(STATUS_FROZEN);
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "FREEZE", defaultRemark(request.remark(), "封存看板"));
        } else {
            kanban.setFrozen(false);
            kanban.setStatus(STATUS_INBOUND);
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "UNFREEZE", defaultRemark(request.remark(), "解除封存"));
        }
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackOutbound(RepackOutboundRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        Location sourceLocation = locationRepository.findById(kanban.getLocationId())
                .orElseThrow(() -> new NotFoundException("来源库位不存在"));
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("目标库位不存在"));
        requireWarehouseType(sourceLocation, "OWN", "转包出库必须从自有仓库发起");
        requireWarehouseType(targetLocation, "THIRD_PARTY", "转包目标必须是第三方仓库");
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("目标库位与当前库位相同，不能转包");
        }

        moveInventory(kanban.getPartId(), kanban.getLocationId(), kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, kanban.getLocationId(), kanban.getQty().negate(), "REPACK_OUT", defaultRemark(request.remark(), "转包出库"));
        kanban.setLocationId(targetLocation.getId());
        kanban.setStatus(STATUS_REPACK_OUTBOUND);
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "REPACK_THIRD_IN", defaultRemark(request.remark(), "第三方入库"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackInbound(RepackInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!STATUS_REPACK_OUTBOUND.equals(kanban.getStatus())) {
            throw new BusinessException("只有已转包出库的看板可以执行转包入库");
        }
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("库位不存在"));
        requireWarehouseType(location, "OWN", "转包入库目标必须是自有仓库");

        Long thirdPartyLocationId = kanban.getLocationId();
        Location thirdPartyLocation = locationRepository.findById(thirdPartyLocationId)
                .orElseThrow(() -> new NotFoundException("第三方库位不存在"));
        requireWarehouseType(thirdPartyLocation, "THIRD_PARTY", "转包入库必须从第三方仓库返回");

        BigDecimal thirdPartyQty = kanban.getQty();
        kanban.setQty(request.qty());
        kanban.setLocationId(location.getId());
        kanban.setStatus(STATUS_INBOUND);
        kanban.setFrozen(false);
        kanban.setInboundTime(LocalDateTime.now());
        kanban = kanbanRepository.save(kanban);
        moveInventory(kanban.getPartId(), thirdPartyLocationId, thirdPartyQty.negate());
        moveInventory(kanban.getPartId(), location.getId(), kanban.getQty());
        saveTransaction(kanban, thirdPartyLocationId, thirdPartyQty.negate(), "REPACK_THIRD_OUT", defaultRemark(request.remark(), "第三方出库"));
        saveTransaction(kanban, location.getId(), kanban.getQty(), "REPACK_IN", defaultRemark(request.remark(), "转包入库"));
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
        saveTransaction(kanban, kanban.getLocationId(), delta, "BALANCE_ADJUST", defaultRemark(request.remark(), "转包结余调整"));
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

    public InventoryTransactionVersionView getTransactionVersion() {
        InventoryTransaction latest = inventoryTransactionRepository.findFirstByOrderByIdDesc().orElse(null);
        return new InventoryTransactionVersionView(
                inventoryTransactionRepository.count(),
                latest == null ? null : latest.getId(),
                latest == null ? "" : latest.getTransactionNo(),
                latest == null ? null : latest.getCreatedAt()
        );
    }

    private Kanban findOperableStockKanban(String barcode) {
        Kanban kanban = findKanbanByScanCode(barcode);
        if (!"INBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("看板不是已入库状态，不能执行库存操作");
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("看板已封存，不能执行库存操作");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("看板没有库存库位，不能执行库存操作");
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

    private List<Kanban> resolveInboundScanTargets(Kanban kanban) {
        List<Kanban> targets = resolveScanTargets(kanban).stream()
                .filter(item -> INBOUND_PENDING_STATUSES.contains(item.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有待入库箱子");
        }
        return targets;
    }

    private List<Kanban> resolveOutboundScanTargets(Kanban kanban, OutboundOrder order) {
        List<Kanban> targets = resolveScanTargets(kanban).stream()
                .filter(item -> order.getOutboundNo().equalsIgnoreCase(defaultString(item.getOutboundOrderNo())))
                .filter(item -> OUTBOUND_PENDING_STATUSES.contains(item.getStatus()))
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException("看板 " + kanban.getKanbanNo() + " 没有绑定到当前出库单的待出库箱子");
        }
        return targets;
    }

    private Location resolveInboundLocation(String locationCode, Kanban scannedKanban, List<Kanban> targets) {
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
                .toList();
        if (matchedLocations.size() == 1) {
            return matchedLocations.get(0);
        }
        if (matchedLocations.isEmpty()) {
            throw new BusinessException("未找到看板计划库区对应的库位，请在移动端选择入库库位后重试：" + plannedZone[0] + " / " + plannedZone[1]);
        }
        throw new BusinessException("看板计划库区匹配到多个库位，请在移动端手动选择入库库位：" + plannedZone[0] + " / " + plannedZone[1]);
    }

    private String parentKanbanNo(Kanban kanban) {
        if (kanban.isParentKanban()) {
            return kanban.getKanbanNo();
        }
        if (kanban.getParentKanbanId() == null) {
            return null;
        }
        return kanbanRepository.findById(kanban.getParentKanbanId())
                .map(Kanban::getKanbanNo)
                .orElse(null);
    }

    private String resolveOutboundOrderNo(String requestedOutboundOrderNo, Kanban scannedKanban) {
        String normalized = normalize(requestedOutboundOrderNo);
        if (normalized != null) {
            return normalized;
        }

        List<String> boundOrderNos = resolveScanTargets(scannedKanban).stream()
                .map(Kanban::getOutboundOrderNo)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .filter(value -> !"-".equals(value))
                .distinct()
                .toList();

        if (boundOrderNos.size() == 1) {
            return boundOrderNos.get(0);
        }
        if (boundOrderNos.size() > 1) {
            throw new BusinessException("该父看板绑定了多个出库单，请先选择出库单后再扫码：" + String.join("、", boundOrderNos));
        }
        throw new BusinessException("看板 " + scannedKanban.getKanbanNo() + " 未绑定出库单，请先在出库管理创建出库单");
    }

    private List<Kanban> allocateOutboundBoxes(List<OutboundOrderItemRequest> requests) {
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

    private boolean isOutboundAllocatable(Kanban kanban) {
        if (!STATUS_INBOUND.equals(kanban.getStatus())) {
            return false;
        }
        if (kanban.isFrozen() || kanban.getLocationId() == null || !isBlank(kanban.getOutboundOrderNo())) {
            return false;
        }
        Kanban parent = kanban.getParentKanbanId() == null ? null : kanbanRepository.findById(kanban.getParentKanbanId()).orElse(null);
        return parent != null
                && parent.isParentKanban()
                && isParentOutboundReady(parent.getId());
    }

    private boolean isParentOutboundReady(Long parentKanbanId) {
        List<Kanban> children = kanbanRepository.findByParentKanbanIdOrderByBoxIndexAscIdAsc(parentKanbanId);
        return !children.isEmpty()
                && children.stream().noneMatch(item -> NOT_FULLY_INBOUND_STATUSES.contains(item.getStatus()));
    }

    private int compareKanbanFifo(Kanban left, Kanban right) {
        return Comparator
                .comparing(Kanban::getInboundTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getParentKanbanId, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getBoxIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Kanban::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .compare(left, right);
    }

    private void ensureInboundAllowed(Kanban kanban) {
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

    private void ensureOutboundAllowed(Kanban kanban) {
        if (!OUTBOUND_PENDING_STATUSES.contains(kanban.getStatus())) {
            if (STATUS_OUTBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已出库，不能重复出库");
            }
            if (NOT_FULLY_INBOUND_STATUSES.contains(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 尚未完整入库，不能出库");
            }
            if (STATUS_PARTIAL_OUTBOUND.equals(kanban.getStatus())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 已部分出库，请扫描绑定的箱级子看板继续出库");
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
        kanban.setStatus(STATUS_INBOUND);
        kanban.setInboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("Inbound order item not found"));
        item.setReceivedQty(item.getReceivedQty().add(kanban.getQty()));
        inboundOrderItemRepository.save(item);

        saveTransaction(kanban, location.getId(), kanban.getQty(), "INBOUND_SCAN", "扫码入库");
    }

    private void applyOutboundForSingleKanban(OutboundOrder order, Kanban kanban) {
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
                "扫码出库完成"
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

        parent.setOutboundOrderNo(aggregateChildOutboundOrderNos(children));
        boolean allInbound = children.stream().allMatch(item -> STATUS_INBOUND.equals(item.getStatus()));
        boolean allOutbound = children.stream().allMatch(item -> STATUS_OUTBOUND.equals(item.getStatus()));
        boolean anyOutbound = children.stream().anyMatch(item -> STATUS_OUTBOUND.equals(item.getStatus()));
        boolean anyAllocated = children.stream().anyMatch(item -> STATUS_ALLOCATED.equals(item.getStatus()));
        boolean anyInboundLike = children.stream().anyMatch(item -> INBOUND_LIKE_STATUSES.contains(item.getStatus()));

        if (allOutbound) {
            parent.setStatus(STATUS_OUTBOUND);
            parent.setOutboundTime(children.stream().map(Kanban::getOutboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
        } else if (allInbound) {
            parent.setStatus(STATUS_INBOUND);
            parent.setInboundTime(children.stream().map(Kanban::getInboundTime).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(LocalDateTime.now()));
            parent.setLocationId(children.get(0).getLocationId());
        } else if (anyOutbound || anyAllocated) {
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

    private String aggregateChildOutboundOrderNos(List<Kanban> children) {
        String outboundNos = children.stream()
                .map(Kanban::getOutboundOrderNo)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .filter(value -> !"-".equals(value))
                .distinct()
                .collect(Collectors.joining(","));
        return isBlank(outboundNos) ? null : outboundNos;
    }

    private void refreshParentKanbanStates(List<Kanban> children) {
        children.stream()
                .map(Kanban::getParentKanbanId)
                .filter(Objects::nonNull)
                .distinct()
                .map(parentId -> kanbanRepository.findById(parentId).orElse(null))
                .filter(Objects::nonNull)
                .forEach(this::refreshParentKanbanState);
    }

    private void repairParentKanbanStates() {
        kanbanRepository.findAll().stream()
                .filter(Kanban::isParentKanban)
                .forEach(this::refreshParentKanbanState);
    }

    private Kanban findKanbanByScanCode(String scanCode) {
        String normalized = normalize(scanCode);
        if (normalized == null) {
            throw new NotFoundException("请提供看板二维码或条码");
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
                .orElseThrow(() -> new NotFoundException("没有找到对应看板：" + normalized));
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
            throw new BusinessException("库存不足，不能扣减到负数");
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
            throw new BusinessException("计划数量和箱数必须大于 0");
        }
        return plannedQty.divide(BigDecimal.valueOf(boxCount), 3, RoundingMode.HALF_UP);
    }

    private void applyOutboundScanToOrder(OutboundOrder order, Kanban kanban) {
        OutboundOrderItem target = outboundOrderItemRepository.findByOutboundOrderIdAndKanbanId(order.getId(), kanban.getId())
                .orElseThrow(() -> new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 未绑定到出库单 " + order.getOutboundNo()));
        if (target.getScannedQty().compareTo(target.getPlannedQty()) >= 0) {
            throw new BusinessException("箱级看板 " + kanban.getKanbanNo() + " 已完成出库，不能重复扫码");
        }
        target.setScannedQty(target.getPlannedQty());
        outboundOrderItemRepository.save(target);

        syncOutboundOrderStatus(order.getId());
    }

    private void requireKanbanMatchesOutboundSource(OutboundOrder order, Kanban kanban) {
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

    private void requireKanbanMatchesInboundNo(Kanban kanban, String inboundOrderNo) {
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
                .orElseThrow(() -> new NotFoundException("入库单不存在"));
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);

        boolean completed = items.stream().allMatch(item -> item.getReceivedQty().compareTo(item.getPlannedQty()) >= 0);

        boolean partial = items.stream().anyMatch(item -> item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);

        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        inboundOrderRepository.save(order);
    }

    private void syncOutboundOrderStatus(Long outboundOrderId) {
        OutboundOrder order = outboundOrderRepository.findById(outboundOrderId)
                .orElseThrow(() -> new NotFoundException("出库单不存在"));
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
                            item.getKanbanId(),
                            defaultString(item.getKanbanNo()),
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
        return toKanbanViews(List.of(kanban), true).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("看板不存在：" + kanban.getId()));
    }

    private List<KanbanView> toKanbanViews(List<Kanban> sourceKanbans, boolean includeChildren) {
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

        Map<Long, Part> partMap = partRepository.findAll().stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, InboundOrderItem> itemMap = inboundOrderItemRepository.findAll().stream()
                .collect(Collectors.toMap(InboundOrderItem::getId, Function.identity()));
        Map<Long, InboundOrder> inboundOrderMap = inboundOrderRepository.findAll().stream()
                .collect(Collectors.toMap(InboundOrder::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAll().stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Supplier> supplierMap = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        Map<String, Equipment> equipmentMap = equipmentRepository.findAll().stream()
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

    private KanbanView toKanbanView(Kanban kanban,
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
                defaultString(kanban.getQrContent()).equals("-") ? buildQrContent(kanban.getKanbanNo(), kanban.getBarcode()) : kanban.getQrContent(),
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
                item == null ? 0 : item.getBoxCount(),
                item != null && item.isPendingRepack(),
                item == null ? "" : defaultString(item.getEquipmentCode()),
                equipment == null ? "" : defaultString(equipment.getEquipmentModel()),
                item == null ? BigDecimal.ZERO : defaultBigDecimal(item.getUnitPerBox()),
                warehouseName,
                zoneName,
                kanban.getStatus(),
                kanban.getLocationId(),
                location == null ? "-" : location.getLocationCode(),
                kanban.getCreatedAt(),
                kanban.getInboundTime(),
                kanban.getOutboundTime(),
                children
        );
    }

    private String resolveParentOutboundNo(Kanban kanban, List<Kanban> children) {
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
