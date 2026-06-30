/**
 * 本文件实现出库单创建、取消和查询业务。
 */
package com.example.wms.service;

import com.example.wms.api.OrderController.OutboundOrderCreateRequest;
import com.example.wms.api.OrderController.OutboundOrderItemRequest;
import com.example.wms.api.OrderController.OutboundOrderView;
import com.example.wms.api.OrderController.PageView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.InboundOrder;
import com.example.wms.domain.Kanban;
import com.example.wms.domain.Location;
import com.example.wms.domain.OutboundAllocation;
import com.example.wms.domain.OutboundOrder;
import com.example.wms.domain.OutboundOrderItem;
import com.example.wms.domain.Part;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OutboundOrderService extends WmsServiceSupport {

    public OutboundOrderService(WmsServiceDependencies dependencies) {
        super(dependencies);
    }

    @Transactional
    public OutboundOrderView createOutboundOrder(OutboundOrderCreateRequest request) {
        if (request.customerId() != null) {
            customerRepository.findById(request.customerId())
                    .orElseThrow(() -> new NotFoundException("客户不存在：" + request.customerId()));
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("创建出库单必须选择要出库的零件、数量和箱数");
        }

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo(nextBusinessNo("OUT"));
        order.setCustomerId(request.customerId());
        order.setInboundOrderNos("");
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.now());
        order = outboundOrderRepository.save(order);

        Set<String> inboundOrderNos = new java.util.LinkedHashSet<>();
        Set<Long> touchedKanbanIds = new java.util.LinkedHashSet<>();
        for (OutboundOrderItemRequest itemRequest : request.items()) {
            Part part = partRepository.findById(itemRequest.partId())
                    .orElseThrow(() -> new NotFoundException("零件不存在：" + itemRequest.partId()));
            validateOutboundPlan(itemRequest);
            PackagePlan packagePlan = resolveOutboundPackagePlan(part, itemRequest);
            Location requestedLocation = resolveOptionalLocation(itemRequest.locationCode());
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundOrderId(order.getId());
            item.setKanbanId(null);
            item.setKanbanNo("-");
            item.setPartId(part.getId());
            item.setPlannedQty(itemRequest.plannedQty());
            item.setScannedQty(BigDecimal.ZERO);
            item.setBoxCount(packagePlan.boxCount());
            item.setEquipmentCode(packagePlan.equipmentCode());
            item.setUnitPerBox(packagePlan.unitPerBox());
            item.setLocationCode(requestedLocation == null ? null : requestedLocation.getLocationCode());
            item.setWarehouseName(requestedLocation == null ? "全部仓库" : requestedLocation.getWarehouseName());
            item.setZoneName(requestedLocation == null ? "全部库区" : requestedLocation.getZoneName());
            item = outboundOrderItemRepository.save(item);

            List<OutboundAllocation> allocations = allocateOutboundQty(order, item, requestedLocation);
            outboundAllocationRepository.saveAll(allocations);
            for (OutboundAllocation allocation : allocations) {
                Kanban kanban = kanbanRepository.findById(allocation.getKanbanId())
                        .orElseThrow(() -> new NotFoundException("看板不存在：" + allocation.getKanbanId()));
                touchedKanbanIds.add(kanban.getId());
                inboundOrderRepository.findById(kanban.getInboundOrderId())
                        .map(InboundOrder::getInboundNo)
                        .ifPresent(inboundOrderNos::add);
            }
        }

        order.setInboundOrderNos(String.join(",", inboundOrderNos));
        outboundOrderRepository.save(order);
        refreshKanbanStatesByIds(touchedKanbanIds);
        return toOutboundOrderView(order);
    }

    @Transactional
    public OutboundOrderView cancelOutboundOrder(Long outboundOrderId) {
        OutboundOrder order = outboundOrderRepository.findById(outboundOrderId)
                .orElseThrow(() -> new NotFoundException("出库单不存在：" + outboundOrderId));
        if (!STATUS_CREATED.equals(order.getStatus())) {
            throw new BusinessException("只有已创建且未出库的出库单可以取消");
        }
        List<OutboundAllocation> allocations = outboundAllocationRepository.findByOutboundOrderId(order.getId());
        if (allocations.stream().anyMatch(allocation -> allocation.getOutboundQty().compareTo(BigDecimal.ZERO) > 0)) {
            throw new BusinessException("出库单已发生出库扣减，不能取消");
        }
        Set<Long> touchedKanbanIds = allocations.stream().map(OutboundAllocation::getKanbanId).collect(Collectors.toSet());
        for (OutboundAllocation allocation : allocations) {
            Kanban kanban = kanbanRepository.findById(allocation.getKanbanId())
                    .orElseThrow(() -> new NotFoundException("看板不存在：" + allocation.getKanbanId()));
            kanban.setReservedQty(nonNegative(defaultBigDecimal(kanban.getReservedQty()).subtract(allocation.getAllocatedQty())));
            removeOutboundOrderNo(kanban, order.getOutboundNo());
            kanbanRepository.save(kanban);
            saveOutboundUnlockTransaction(kanban, order.getOutboundNo(), allocation.getAllocatedQty());
            allocation.setStatus(STATUS_CANCELLED);
            outboundAllocationRepository.save(allocation);
        }
        order.setStatus(STATUS_CANCELLED);
        outboundOrderRepository.save(order);
        refreshKanbanStatesByIds(touchedKanbanIds);
        return toOutboundOrderView(order);
    }

    public List<OutboundOrderView> listOutboundOrders(String status, Long customerId, String outboundNo) {
        return toOutboundOrderViews(outboundOrderRepository.findAll(
                outboundOrderSpecification(status, customerId, outboundNo),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        ));
    }

    public PageView<OutboundOrderView> listOutboundOrdersPage(String status, Long customerId, String outboundNo, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        Page<OutboundOrder> orderPage = outboundOrderRepository.findAll(
                outboundOrderSpecification(status, customerId, outboundNo),
                PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageView<>(
                toOutboundOrderViews(orderPage.getContent()),
                orderPage.getTotalElements(),
                normalizedPage,
                normalizedSize,
                orderPage.getTotalPages()
        );
    }
}
