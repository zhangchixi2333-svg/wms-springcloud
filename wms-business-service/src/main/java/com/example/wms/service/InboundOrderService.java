/**
 * 本文件实现入库单创建、查询、退回和看板生成业务。
 */
package com.example.wms.service;

import com.example.wms.api.OrderController.InboundOrderCreateRequest;
import com.example.wms.api.OrderController.InboundOrderItemRequest;
import com.example.wms.api.OrderController.InboundOrderView;
import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.api.OrderController.PageView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.InboundOrder;
import com.example.wms.domain.InboundOrderItem;
import com.example.wms.domain.Kanban;
import com.example.wms.domain.Part;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
