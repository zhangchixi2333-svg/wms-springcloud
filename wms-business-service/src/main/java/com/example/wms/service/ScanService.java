/**
 * 本文件实现移动端和手工扫码入库、扫码出库业务。
 */
package com.example.wms.service;

import com.example.wms.api.ScanController.ScanInboundRequest;
import com.example.wms.api.ScanController.ScanInboundBatchRequest;
import com.example.wms.api.ScanController.ScanOutboundRequest;
import com.example.wms.api.ScanController.ScanResultView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.InboundOrder;
import com.example.wms.domain.Kanban;
import com.example.wms.domain.Location;
import com.example.wms.domain.OutboundOrder;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

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
