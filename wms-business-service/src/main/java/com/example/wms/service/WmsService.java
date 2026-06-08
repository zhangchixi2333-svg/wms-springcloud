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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仓库管理核心服务类
 * 负责处理WMS系统的核心业务逻辑，包括入库单管理、出库单管理、看板管理、库存操作等
 */
@Service
public class WmsService {

    // 时间格式化器，用于生成订单号、交易号等
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 注入所有相关的仓库接口
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

    /**
     * 构造函数，通过依赖注入初始化所有仓库接口
     */
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

    /**
     * 创建入库单
     * @param request 入库单创建请求，包含供应商ID和入库单行项目列表
     * @return 创建后的入库单视图对象
     * @throws NotFoundException 如果供应商或物料不存在则抛出资源未找到异常
     * @throws BusinessException 如果入库单没有行项目则抛出业务异常
     */
    @Transactional
    public InboundOrderView createInboundOrder(InboundOrderCreateRequest request) {
        supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier not found"));
        if (request.items().isEmpty()) {
            throw new BusinessException("Inbound order must have at least one item");
        }

        // 创建入库单主记录
        InboundOrder order = new InboundOrder();
        order.setInboundNo("IN" + LocalDateTime.now().format(TS));
        order.setSupplierId(request.supplierId());
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = inboundOrderRepository.save(order);

        // 批量创建入库单行项目
        for (InboundOrderItemRequest itemRequest : request.items()) {
            partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("Part not found: " + itemRequest.partId()));
            InboundOrderItem item = new InboundOrderItem();
            item.setInboundOrderId(order.getId());
            item.setPartId(itemRequest.partId());
            item.setPlannedQty(itemRequest.plannedQty());
            item.setReceivedQty(BigDecimal.ZERO);
            item.setBoxCount(itemRequest.boxCount());
            item.setPendingRepack(itemRequest.pendingRepack());
            item.setEquipmentCode(itemRequest.equipmentCode());
            item.setPackageCapacity(itemRequest.packageCapacity());
            item.setWarehouseZone(itemRequest.warehouseZone());
            inboundOrderItemRepository.save(item);
        }

        return toInboundOrderView(order);
    }

    /**
     * 查询入库单列表，支持多条件筛选
     * @param status 订单状态筛选
     * @param supplierId 供应商ID筛选
     * @param inboundNo 入库单号模糊搜索
     * @return 入库单视图对象列表，按创建时间倒序排列
     */
    public List<InboundOrderView> listInboundOrders(String status, Long supplierId, String inboundNo) {
        return inboundOrderRepository.findAll().stream()
                .filter(order -> isBlank(status) || status.equalsIgnoreCase(order.getStatus()))
                .filter(order -> supplierId == null || supplierId.equals(order.getSupplierId()))
                .filter(order -> containsIgnoreCase(order.getInboundNo(), inboundNo))
                .sorted(Comparator.comparing(InboundOrder::getCreatedAt).reversed())
                .map(this::toInboundOrderView)
                .toList();
    }

    /**
     * 为入库单生成看板
     * @param inboundOrderId 入库单ID
     * @return 生成的看板视图对象列表
     * @throws NotFoundException 如果入库单不存在则抛出资源未找到异常
     * @throws BusinessException 如果入库单没有行项目则抛出业务异常
     */
    @Transactional
    public List<KanbanView> generateKanbans(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("Inbound order not found"));
        // 如果已经生成过看板，直接返回
        List<Kanban> existing = kanbanRepository.findByInboundOrderId(inboundOrderId);
        if (!existing.isEmpty()) {
            return existing.stream().map(this::toKanbanView).toList();
        }

        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);
        if (items.isEmpty()) {
            throw new BusinessException("Inbound order has no items");
        }

        // 为每个行项目生成一个看板
        int sequence = 1;
        for (InboundOrderItem item : items) {
            Kanban kanban = new Kanban();
            kanban.setKanbanNo("KB" + LocalDateTime.now().format(TS) + String.format("%02d", sequence++));
            kanban.setBarcode("BC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
            kanban.setQrContent(buildQrContent(kanban.getKanbanNo(), kanban.getBarcode()));
            kanban.setInboundOrderId(order.getId());
            kanban.setInboundOrderItemId(item.getId());
            kanban.setPartId(item.getPartId());
            kanban.setSupplierId(order.getSupplierId());
            kanban.setBatchNo("BATCH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd")));
            kanban.setQty(item.getPlannedQty());
            kanban.setStatus("WAIT_SCAN");
            kanban.setFrozen(false);
            kanban.setCreatedAt(LocalDateTime.now());
            kanbanRepository.save(kanban);
        }
        return kanbanRepository.findByInboundOrderId(inboundOrderId).stream().map(this::toKanbanView).toList();
    }

    /**
     * 查询看板列表，支持多条件筛选
     * @param status 看板状态筛选
     * @param inboundNo 入库单号模糊搜索
     * @param outboundNo 出库单号模糊搜索
     * @param kanbanNo 看板编号模糊搜索
     * @param supplierId 供应商ID筛选
     * @param partCode 物料编码模糊搜索
     * @return 看板视图对象列表，按创建时间倒序排列
     */
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

    /**
     * 创建出库单
     * @param request 出库单创建请求，包含客户ID和出库单行项目列表
     * @return 创建后的出库单视图对象
     * @throws NotFoundException 如果客户或物料不存在则抛出资源未找到异常
     * @throws BusinessException 如果出库单没有行项目则抛出业务异常
     */
    @Transactional
    public OutboundOrderView createOutboundOrder(OutboundOrderCreateRequest request) {
        if (request.customerId() != null) {
            customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new NotFoundException("Customer not found"));
        }
        if (request.items().isEmpty()) {
            throw new BusinessException("Outbound order must have at least one item");
        }
        List<String> inboundOrderNos = normalizeInboundOrderNos(request.inboundOrderNos());
        if (inboundOrderNos.isEmpty()) {
            throw new BusinessException("Outbound order must bind at least one inbound order");
        }
        inboundOrderNos.forEach(inboundNo -> inboundOrderRepository.findAll().stream()
                .filter(order -> inboundNo.equalsIgnoreCase(order.getInboundNo()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Inbound order not found: " + inboundNo)));

        // 创建出库单主记录
        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo("OUT" + LocalDateTime.now().format(TS));
        order.setCustomerId(request.customerId());
        order.setInboundOrderNos(String.join(",", inboundOrderNos));
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = outboundOrderRepository.save(order);

        // 批量创建出库单行项目
        for (OutboundOrderItemRequest itemRequest : request.items()) {
            partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("Part not found: " + itemRequest.partId()));
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

    /**
     * 查询出库单列表，支持多条件筛选
     * @param status 订单状态筛选
     * @param customerId 客户ID筛选
     * @param outboundNo 出库单号模糊搜索
     * @return 出库单视图对象列表，按创建时间倒序排列
     */
    public List<OutboundOrderView> listOutboundOrders(String status, Long customerId, String outboundNo) {
        return outboundOrderRepository.findAll().stream()
                .filter(order -> isBlank(status) || status.equalsIgnoreCase(order.getStatus()))
                .filter(order -> customerId == null || customerId.equals(order.getCustomerId()))
                .filter(order -> containsIgnoreCase(order.getOutboundNo(), outboundNo))
                .sorted(Comparator.comparing(OutboundOrder::getCreatedAt).reversed())
                .map(this::toOutboundOrderView)
                .toList();
    }

    /**
     * 执行入库扫描，将看板对应的物料入库到指定库位
     * @param request 入库扫描请求，包含条码和库位编码
     * @return 扫描结果视图对象
     * @throws NotFoundException 如果条码、库位或入库单行项目不存在则抛出资源未找到异常
     * @throws BusinessException 如果看板状态不允许入库则抛出业务异常
     */
    @Transactional
    public ScanResultView scanInbound(ScanInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!List.of("WAIT_SCAN", "CREATED").contains(kanban.getStatus())) {
            throw new BusinessException("Kanban status does not allow inbound");
        }
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // 查询或创建库存记录
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), location.getId())
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setPartId(kanban.getPartId());
                    created.setLocationId(location.getId());
                    created.setQty(BigDecimal.ZERO);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });
        // 增加库存数量
        inventory.setQty(inventory.getQty().add(kanban.getQty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        // 更新看板状态
        kanban.setLocationId(location.getId());
        kanban.setStatus("INBOUND");
        kanban.setInboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        // 更新入库单行项目的已收货数量
        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId())
                .orElseThrow(() -> new NotFoundException("Inbound order item not found"));
        item.setReceivedQty(item.getReceivedQty().add(kanban.getQty()));
        inboundOrderItemRepository.save(item);
        // 同步更新入库单状态
        syncInboundOrderStatus(kanban.getInboundOrderId());

        // 保存库存交易记录
        saveTransaction(kanban, location.getId(), kanban.getQty(), "INBOUND_SCAN", "Inbound scan completed");

        return new ScanResultView("INBOUND_OK", "Inbound completed", kanban.getBarcode(), kanban.getStatus());
    }

    /**
     * 执行出库扫描，将看板对应的物料从库存中出库
     * @param request 出库扫描请求，包含条码和出库单号
     * @return 扫描结果视图对象
     * @throws NotFoundException 如果条码、出库单不存在则抛出资源未找到异常
     * @throws BusinessException 如果看板状态不允许出库、库存不足或其他业务校验不通过则抛出异常
     */
    @Transactional
    public ScanResultView scanOutbound(ScanOutboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!"INBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("Kanban is not currently in stock");
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("Frozen kanban cannot be outbound");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("Kanban has no location");
        }

        String outboundOrderNo = normalize(request.outboundOrderNo());
        if (outboundOrderNo == null) {
            throw new BusinessException("Outbound order number is required");
        }
        OutboundOrder order = outboundOrderRepository.findAll().stream()
                .filter(item -> outboundOrderNo.equalsIgnoreCase(item.getOutboundNo()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Outbound order not found"));
        requireKanbanMatchesOutboundSource(order, kanban);
        applyOutboundScanToOrder(order, kanban);
        kanban.setOutboundOrderNo(order.getOutboundNo());

        // 检查库存是否足够
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(kanban.getPartId(), kanban.getLocationId())
                .orElseThrow(() -> new BusinessException("Inventory record not found"));
        if (inventory.getQty().compareTo(kanban.getQty()) < 0) {
            throw new BusinessException("Inventory is not enough for outbound");
        }

        // 减少库存数量
        inventory.setQty(inventory.getQty().subtract(kanban.getQty()));
        inventory.setUpdatedAt(LocalDateTime.now());
        inventoryRepository.save(inventory);

        // 更新看板状态
        kanban.setStatus("OUTBOUND");
        kanban.setOutboundTime(LocalDateTime.now());
        kanbanRepository.save(kanban);

        // 保存库存交易记录
        saveInventoryTransaction(
                kanban.getPartId(),
                kanban.getLocationId(),
                kanban.getBarcode(),
                kanban.getQty().negate(),
                "OUTBOUND_SCAN",
                order.getOutboundNo(),
                "Outbound scan completed"
        );
        return new ScanResultView("OUTBOUND_OK", "Outbound completed", kanban.getBarcode(), kanban.getStatus());
    }

    /**
     * 获取库存汇总列表，支持多条件筛选
     * @param warehouseName 仓库名称模糊搜索
     * @param zoneName 库区名称模糊搜索
     * @param materialKeyword 物料编码或名称模糊搜索
     * @param supplierId 供应商ID筛选
     * @return 库存汇总视图对象列表，按更新时间倒序排列
     */
    public List<InventorySummaryView> getInventorySummary(String warehouseName,
                                                          String zoneName,
                                                          String materialKeyword,
                                                          Long supplierId) {
        // 预加载所有物料和库位，提高查询效率
        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAll().stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<String, String> supplierNameByPartLocation = new HashMap<>();
        Map<String, Long> supplierIdByPartLocation = new HashMap<>();

        // 从在库看板中提取供应商信息
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

        // 转换库存实体为视图对象并应用筛选条件
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

    /**
     * 手动录入库存，直接增加指定库位的物料数量
     * @param request 手动入库请求，包含物料ID、库位ID和数量
     * @return 更新后的库存汇总视图对象
     * @throws NotFoundException 如果物料或库位不存在则抛出资源未找到异常
     */
    @Transactional
    public InventorySummaryView manualInventoryEntry(ManualInventoryEntryRequest request) {
        partRepository.findById(request.partId())
                .orElseThrow(() -> new NotFoundException("Part not found"));
        Location location = locationRepository.findById(request.locationId())
                .orElseThrow(() -> new NotFoundException("Location not found"));

        // 查询或创建库存记录
        Inventory inventory = inventoryRepository.findByPartIdAndLocationId(request.partId(), request.locationId())
                .orElseGet(() -> {
                    Inventory created = new Inventory();
                    created.setPartId(request.partId());
                    created.setLocationId(request.locationId());
                    created.setQty(BigDecimal.ZERO);
                    created.setUpdatedAt(LocalDateTime.now());
                    return created;
                });

        // 增加库存数量
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

        // 查询物料信息，构建库存汇总视图返回
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

    /**
     * 获取库存交易记录列表
     * @return 库存交易记录视图对象列表，按创建时间倒序排列
     */
    @Transactional
    public KanbanView transferKanban(TransferKanbanRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        requireKanbanMatchesInboundNo(kanban, request.inboundOrderNo());
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("Target location not found"));
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("Target location is the same as current location");
        }

        Long sourceLocationId = kanban.getLocationId();
        moveInventory(kanban.getPartId(), sourceLocationId, kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, sourceLocationId, kanban.getQty().negate(), "TRANSFER_OUT", defaultRemark(request.remark(), "Transfer out"));

        kanban.setLocationId(targetLocation.getId());
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "TRANSFER_IN", defaultRemark(request.remark(), "Transfer in"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView freezeKanban(FreezeKanbanRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!List.of("INBOUND", "FROZEN").contains(kanban.getStatus())) {
            throw new BusinessException("Only stock kanbans can be frozen or unfrozen");
        }
        if (request.frozen()) {
            if (kanban.getLocationId() == null) {
                throw new BusinessException("Kanban has no location");
            }
            kanban.setFrozen(true);
            kanban.setStatus("FROZEN");
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "FREEZE", defaultRemark(request.remark(), "Kanban frozen"));
        } else {
            kanban.setFrozen(false);
            kanban.setStatus("INBOUND");
            kanban = kanbanRepository.save(kanban);
            saveTransaction(kanban, kanban.getLocationId(), BigDecimal.ZERO, "UNFREEZE", defaultRemark(request.remark(), "Kanban unfrozen"));
        }
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackOutbound(RepackOutboundRequest request) {
        Kanban kanban = findOperableStockKanban(request.barcode());
        Location sourceLocation = locationRepository.findById(kanban.getLocationId())
                .orElseThrow(() -> new NotFoundException("Source location not found"));
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("Target location not found"));
        requireWarehouseType(sourceLocation, "OWN", "Repack outbound must start from own warehouse");
        requireWarehouseType(targetLocation, "THIRD_PARTY", "Repack outbound target must be third-party warehouse");
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("Target location is the same as current location");
        }

        moveInventory(kanban.getPartId(), kanban.getLocationId(), kanban.getQty().negate());
        moveInventory(kanban.getPartId(), targetLocation.getId(), kanban.getQty());
        saveTransaction(kanban, kanban.getLocationId(), kanban.getQty().negate(), "REPACK_OUT", defaultRemark(request.remark(), "Repack outbound"));
        kanban.setLocationId(targetLocation.getId());
        kanban.setStatus("REPACK_OUTBOUND");
        kanban = kanbanRepository.save(kanban);
        saveTransaction(kanban, targetLocation.getId(), kanban.getQty(), "REPACK_THIRD_IN", defaultRemark(request.remark(), "Repack third-party inbound"));
        return toKanbanView(kanban);
    }

    @Transactional
    public KanbanView repackInbound(RepackInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        if (!"REPACK_OUTBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("Kanban is not waiting for repack inbound");
        }
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("Location not found"));
        requireWarehouseType(location, "OWN", "Repack inbound target must be own warehouse");

        Long thirdPartyLocationId = kanban.getLocationId();
        Location thirdPartyLocation = locationRepository.findById(thirdPartyLocationId)
                .orElseThrow(() -> new NotFoundException("Third-party location not found"));
        requireWarehouseType(thirdPartyLocation, "THIRD_PARTY", "Repack inbound must start from third-party warehouse");

        BigDecimal thirdPartyQty = kanban.getQty();
        kanban.setQty(request.qty());
        kanban.setLocationId(location.getId());
        kanban.setStatus("INBOUND");
        kanban.setFrozen(false);
        kanban.setInboundTime(LocalDateTime.now());
        kanban = kanbanRepository.save(kanban);
        moveInventory(kanban.getPartId(), thirdPartyLocationId, thirdPartyQty.negate());
        moveInventory(kanban.getPartId(), location.getId(), kanban.getQty());
        saveTransaction(kanban, thirdPartyLocationId, thirdPartyQty.negate(), "REPACK_THIRD_OUT", defaultRemark(request.remark(), "Repack third-party outbound"));
        saveTransaction(kanban, location.getId(), kanban.getQty(), "REPACK_IN", defaultRemark(request.remark(), "Repack inbound"));
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
        saveTransaction(kanban, kanban.getLocationId(), delta, "BALANCE_ADJUST", defaultRemark(request.remark(), "Kanban balance adjusted"));
        return toKanbanView(kanban);
    }

    public List<InventoryTransactionView> getTransactions() {
        // 预加载物料和库位信息
        Map<Long, Part> partMap = partRepository.findAll().stream().collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Location> locationMap = locationRepository.findAll().stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        // 转换为视图对象返回
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

    /**
     * 将出库扫描应用到出库单，更新出库单行项目的已扫描数量
     * @param order 出库单实体
     * @param kanban 看板实体
     * @throws BusinessException 如果找不到匹配的出库单行项目则抛出业务异常
     */
    private Kanban findOperableStockKanban(String barcode) {
        Kanban kanban = findKanbanByScanCode(barcode);
        if (!"INBOUND".equals(kanban.getStatus())) {
            throw new BusinessException("Kanban is not currently in stock");
        }
        if (kanban.isFrozen()) {
            throw new BusinessException("Frozen kanban cannot be operated");
        }
        if (kanban.getLocationId() == null) {
            throw new BusinessException("Kanban has no location");
        }
        return kanban;
    }

    private Kanban findKanbanByScanCode(String scanCode) {
        String normalized = normalize(scanCode);
        if (normalized == null) {
            throw new NotFoundException("Barcode not found");
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
                .orElseThrow(() -> new NotFoundException("Barcode not found"));
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
            throw new BusinessException("Inventory is not enough for this operation");
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

    private void applyOutboundScanToOrder(OutboundOrder order, Kanban kanban) {
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(order.getId());
        // 找到第一个匹配该物料且未完成的行项目
        Location location = kanban.getLocationId() == null ? null : locationRepository.findById(kanban.getLocationId()).orElse(null);
        OutboundOrderItem target = items.stream()
                .filter(item -> item.getPartId().equals(kanban.getPartId()))
                .filter(item -> location == null || defaultString(item.getWarehouseName()).equals(defaultString(location.getWarehouseName())))
                .filter(item -> location == null || defaultString(item.getZoneName()).equals(defaultString(location.getZoneName())))
                .filter(item -> item.getScannedQty().add(kanban.getQty()).compareTo(item.getPlannedQty()) <= 0)
                .filter(item -> item.getScannedQty().compareTo(item.getPlannedQty()) < 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException("No outbound order line matches this source, location, part, or remaining quantity"));

        // 更新已扫描数量
        target.setScannedQty(target.getScannedQty().add(kanban.getQty()));
        outboundOrderItemRepository.save(target);
        // 同步更新出库单状态
        syncOutboundOrderStatus(order.getId());
    }

    private void requireKanbanMatchesOutboundSource(OutboundOrder order, Kanban kanban) {
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("Inbound order not found"));
        List<String> sources = splitCsv(order.getInboundOrderNos());
        if (sources.isEmpty()) {
            throw new BusinessException("Outbound order has no bound inbound source");
        }
        boolean matched = sources.stream().anyMatch(item -> item.equalsIgnoreCase(inboundOrder.getInboundNo()));
        if (!matched) {
            throw new BusinessException("Kanban inbound source is not bound to this outbound order");
        }
    }

    private void requireKanbanMatchesInboundNo(Kanban kanban, String inboundOrderNo) {
        String sourceInboundNo = normalize(inboundOrderNo);
        if (sourceInboundNo == null) {
            throw new BusinessException("Inbound order number is required");
        }
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId())
                .orElseThrow(() -> new NotFoundException("Inbound order not found"));
        if (!sourceInboundNo.equalsIgnoreCase(inboundOrder.getInboundNo())) {
            throw new BusinessException("Kanban inbound source does not match selected inbound order");
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

    /**
     * 同步更新入库单状态，根据行项目的收货情况自动设置订单状态
     * @param inboundOrderId 入库单ID
     * @throws NotFoundException 如果入库单不存在则抛出资源未找到异常
     */
    private void syncInboundOrderStatus(Long inboundOrderId) {
        InboundOrder order = inboundOrderRepository.findById(inboundOrderId)
                .orElseThrow(() -> new NotFoundException("Inbound order not found"));
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(inboundOrderId);
        // 判断订单是否全部完成
        boolean completed = items.stream().allMatch(item -> item.getReceivedQty().compareTo(item.getPlannedQty()) >= 0);
        // 判断订单是否部分完成
        boolean partial = items.stream().anyMatch(item -> item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0);
        // 设置订单状态：COMPLETED-已完成, PARTIAL-部分完成, CREATED-创建
        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        inboundOrderRepository.save(order);
    }

    /**
     * 同步更新出库单状态，根据行项目的扫描情况自动设置订单状态
     * @param outboundOrderId 出库单ID
     * @throws NotFoundException 如果出库单不存在则抛出资源未找到异常
     */
    private void syncOutboundOrderStatus(Long outboundOrderId) {
        OutboundOrder order = outboundOrderRepository.findById(outboundOrderId)
                .orElseThrow(() -> new NotFoundException("Outbound order not found"));
        List<OutboundOrderItem> items = outboundOrderItemRepository.findByOutboundOrderId(outboundOrderId);
        // 判断订单是否全部完成
        boolean completed = items.stream().allMatch(item -> item.getScannedQty().compareTo(item.getPlannedQty()) >= 0);
        // 判断订单是否部分完成
        boolean partial = items.stream().anyMatch(item -> item.getScannedQty().compareTo(BigDecimal.ZERO) > 0);
        // 设置订单状态：COMPLETED-已完成, PARTIAL-部分完成, CREATED-创建
        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "CREATED");
        outboundOrderRepository.save(order);
    }

    /**
     * 保存库存交易记录
     * @param kanban 看板实体
     * @param locationId 库位ID
     * @param qtyChange 库存变化数量（正数为增加，负数为减少）
     * @param businessType 业务类型
     * @param remark 备注信息
     */
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

    /**
     * 将入库单实体转换为视图对象
     * @param order 入库单实体
     * @return 入库单视图对象
     */
    private InboundOrderView toInboundOrderView(InboundOrder order) {
        // 查询供应商名称
        String supplierName = supplierRepository.findById(order.getSupplierId())
                .map(Supplier::getSupplierName)
                .orElse("-");
        List<InboundOrderItem> items = inboundOrderItemRepository.findByInboundOrderId(order.getId());
        // 预加载所有物料信息
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
                            item.getPackageCapacity(),
                            item.getWarehouseZone()
                    );
                }).toList()
        );
    }

    /**
     * 将出库单实体转换为视图对象
     * @param order 出库单实体
     * @return 出库单视图对象
     */
    private OutboundOrderView toOutboundOrderView(OutboundOrder order) {
        // 查询客户名称
        String customerName = order.getCustomerId() == null ? "-" :
                customerRepository.findById(order.getCustomerId()).map(Customer::getCustomerName).orElse("-");
        // 预加载所有物料信息
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

    /**
     * 将看板实体转换为视图对象
     * @param kanban 看板实体
     * @return 看板视图对象
     */
    private KanbanView toKanbanView(Kanban kanban) {
        // 查询关联的物料、入库单、库位、供应商等信息
        Part part = partRepository.findById(kanban.getPartId()).orElse(null);
        InboundOrderItem item = inboundOrderItemRepository.findById(kanban.getInboundOrderItemId()).orElse(null);
        InboundOrder inboundOrder = inboundOrderRepository.findById(kanban.getInboundOrderId()).orElse(null);
        Location location = kanban.getLocationId() == null ? null : locationRepository.findById(kanban.getLocationId()).orElse(null);
        Supplier supplier = inboundOrder == null ? null : supplierRepository.findById(inboundOrder.getSupplierId()).orElse(null);
        Equipment equipment = item == null || isBlank(item.getEquipmentCode()) ? null :
                equipmentRepository.findByEquipmentCode(item.getEquipmentCode()).orElse(null);

        // 解析仓库和库区名称
        String[] plannedZone = splitWarehouseZone(item == null ? null : item.getWarehouseZone());
        String warehouseName = location != null ? location.getWarehouseName() : plannedZone[0];
        String zoneName = location != null ? location.getZoneName() : plannedZone[1];

        return new KanbanView(
                kanban.getId(),
                kanban.getKanbanNo(),
                kanban.getBarcode(),
                defaultString(kanban.getQrContent()).equals("-") ? buildQrContent(kanban.getKanbanNo(), kanban.getBarcode()) : kanban.getQrContent(),
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
                item == null ? BigDecimal.ZERO : defaultBigDecimal(item.getPackageCapacity()),
                warehouseName,
                zoneName,
                kanban.getStatus(),
                location == null ? "-" : location.getLocationCode(),
                kanban.getCreatedAt(),
                kanban.getInboundTime(),
                kanban.getOutboundTime()
        );
    }

    /**
     * 生成库存唯一标识键，用于关联物料和库位
     * @param partId 物料ID
     * @param locationId 库位ID
     * @return 格式为"partId:locationId"的唯一键
     */
    private String inventoryKey(Long partId, Long locationId) {
        return partId + ":" + locationId;
    }

    /**
     * 分割仓库库区字符串，提取仓库名称和库区名称
     * @param text 原始字符串，格式如"仓库/库区"
     * @return 包含仓库名称和库区名称的数组
     */
    private String[] splitWarehouseZone(String text) {
        if (isBlank(text)) {
            return new String[]{"-", "-"};
        }
        // 尝试按斜杠分割
        String[] slash = text.split("/");
        if (slash.length >= 2) {
            return new String[]{slash[0].trim(), slash[1].trim()};
        }
        // 尝试按空格分割
        String[] white = text.trim().split("\\s+");
        if (white.length >= 2) {
            return new String[]{white[0], white[1]};
        }
        return new String[]{text.trim(), "-"};
    }

    /**
     * 规范化字符串，去除首尾空格，空字符串转为null
     * @param value 原始字符串
     * @return 规范化后的字符串
     */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 判断字符串是否为空
     * @param value 待判断的字符串
     * @return 如果字符串为null或空白则返回true，否则返回false
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 判断文本是否包含查询字符串（忽略大小写）
     * @param text 原始文本
     * @param query 查询字符串
     * @return 如果包含则返回true，否则返回false
     */
    private boolean containsIgnoreCase(String text, String query) {
        if (isBlank(query)) {
            return true;
        }
        return defaultString(text).toLowerCase().contains(query.trim().toLowerCase());
    }

    /**
     * 如果字符串为空则返回默认值"-"
     * @param value 原始字符串
     * @return 处理后的字符串
     */
    private String defaultString(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * 如果BigDecimal为null则返回默认值BigDecimal.ZERO
     * @param value 原始BigDecimal
     * @return 处理后的BigDecimal
     */
    private BigDecimal defaultBigDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
