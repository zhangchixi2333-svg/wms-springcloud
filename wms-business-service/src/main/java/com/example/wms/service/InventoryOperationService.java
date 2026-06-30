/**
 * 本文件实现手工入账、移库、封存解封和转包转入转出业务。
 */
package com.example.wms.service;

import com.example.wms.api.InventoryController.InventorySummaryView;
import com.example.wms.api.InventoryController.BatchFreezeKanbanRequest;
import com.example.wms.api.InventoryController.BatchRepackInboundRequest;
import com.example.wms.api.InventoryController.BatchRepackOutboundRequest;
import com.example.wms.api.InventoryController.BatchTransferKanbanRequest;
import com.example.wms.api.InventoryController.FreezeKanbanRequest;
import com.example.wms.api.InventoryController.ManualInventoryEntryRequest;
import com.example.wms.api.InventoryController.RepackInboundRequest;
import com.example.wms.api.InventoryController.RepackOutboundRequest;
import com.example.wms.api.InventoryController.TransferKanbanRequest;
import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.common.BusinessException;
import com.example.wms.common.NotFoundException;
import com.example.wms.domain.Inventory;
import com.example.wms.domain.Kanban;
import com.example.wms.domain.Location;
import com.example.wms.domain.Part;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryOperationService extends WmsServiceSupport {

    public InventoryOperationService(WmsServiceDependencies dependencies) {
        super(dependencies);
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

        String operationNo = nextBusinessNo("OP");
        saveInventoryTransaction(
                request.partId(),
                request.locationId(),
                "MANUAL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                request.qty(),
                "MANUAL_ENTRY",
                operationNo,
                operationNo,
                request.remark()
        );
        saveOperationOrder(operationNo, "MANUAL_ENTRY", operationNo, null, null, null, request.locationId(), request.partId(), request.qty(), null, null, request.remark());

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
        requireWarehouseType(targetLocation, "OWN", "自有移库目标必须是自有仓库");
        if (targetLocation.getId().equals(kanban.getLocationId())) {
            throw new BusinessException("目标库位与当前库位相同，无需转存");
        }

        String transferNo = nextBusinessNo("TF");
        return toKanbanView(moveOrSplitKanban(kanban, targetLocation, request.qty(), STATUS_INBOUND, "TRANSFER_OUT", "TRANSFER_IN", transferNo, defaultRemark(request.remark(), "自有移库")));
    }

    @Transactional
    public List<KanbanView> transferKanbans(BatchTransferKanbanRequest request) {
        List<Kanban> kanbans = resolveDistinctKanbans(request.barcodes());
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("目标库位不存在"));
        requireWarehouseType(targetLocation, "OWN", "自有移库目标必须是自有仓库");
        kanbans.forEach(kanban -> {
            requireOwnInboundStockKanban(kanban, "自有移库");
            if (targetLocation.getId().equals(kanban.getLocationId())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 目标库位与当前库位相同，无需移库");
            }
        });

        String transferNo = nextBusinessNo("TF");
        String remark = defaultRemark(request.remark(), "批量自有移库");
        List<Kanban> moved = kanbans.stream()
                .map(kanban -> moveOrSplitKanban(kanban, targetLocation, null, STATUS_INBOUND, "TRANSFER_OUT", "TRANSFER_IN", transferNo, remark))
                .toList();
        return toKanbanViews(moved, false);
    }

    @Transactional
    public KanbanView freezeKanban(FreezeKanbanRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        requireFreezeActionAllowed(kanban, request.frozen());
        String operationNo = nextBusinessNo("OP");
        return toKanbanView(applyFreezeAction(kanban, request.frozen(), operationNo, defaultRemark(request.remark(), request.frozen() ? "封存看板" : "解除封存")));
    }

    @Transactional
    public List<KanbanView> freezeKanbans(BatchFreezeKanbanRequest request) {
        List<Kanban> kanbans = resolveDistinctKanbans(request.barcodes());
        kanbans.forEach(kanban -> requireFreezeActionAllowed(kanban, request.frozen()));
        String operationNo = nextBusinessNo("OP");
        String remark = defaultRemark(request.remark(), request.frozen() ? "批量封存看板" : "批量解除封存");
        List<Kanban> changed = kanbans.stream()
                .map(kanban -> applyFreezeAction(kanban, request.frozen(), operationNo, remark))
                .toList();
        return toKanbanViews(changed, false);
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

        String transferNo = nextBusinessNo("TF");
        return toKanbanView(moveOrSplitKanban(kanban, targetLocation, request.qty(), STATUS_THIRD_PARTY_STOCK, "OUTSOURCE_TRANSFER_OUT", "OUTSOURCE_TRANSFER_IN", transferNo, defaultRemark(request.remark(), "转包到第三方")));
    }

    @Transactional
    public List<KanbanView> repackOutboundBatch(BatchRepackOutboundRequest request) {
        List<Kanban> kanbans = resolveDistinctKanbans(request.barcodes());
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("目标库位不存在"));
        requireWarehouseType(targetLocation, "THIRD_PARTY", "转包目标必须是第三方仓库");
        kanbans.forEach(kanban -> {
            requireOwnInboundStockKanban(kanban, "转包转出");
            if (targetLocation.getId().equals(kanban.getLocationId())) {
                throw new BusinessException("看板 " + kanban.getKanbanNo() + " 目标库位与当前库位相同，不能转包");
            }
        });

        String transferNo = nextBusinessNo("TF");
        String remark = defaultRemark(request.remark(), "批量转包到第三方");
        List<Kanban> moved = kanbans.stream()
                .map(kanban -> moveOrSplitKanban(kanban, targetLocation, null, STATUS_THIRD_PARTY_STOCK, "OUTSOURCE_TRANSFER_OUT", "OUTSOURCE_TRANSFER_IN", transferNo, remark))
                .toList();
        return toKanbanViews(moved, false);
    }

    @Transactional
    public KanbanView repackInbound(RepackInboundRequest request) {
        Kanban kanban = findKanbanByScanCode(request.barcode());
        Location location = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("库位不存在"));
        requireWarehouseType(location, "OWN", "转包返还目标必须是自有仓库");

        requireThirdPartyStockKanban(kanban);

        String transferNo = nextBusinessNo("TF");
        return toKanbanView(moveOrSplitKanban(kanban, location, request.qty(), STATUS_INBOUND, "OUTSOURCE_RETURN_OUT", "OUTSOURCE_RETURN_IN", transferNo, defaultRemark(request.remark(), "第三方返还")));
    }

    @Transactional
    public List<KanbanView> repackInboundBatch(BatchRepackInboundRequest request) {
        List<Kanban> kanbans = resolveDistinctKanbans(request.barcodes());
        Location targetLocation = locationRepository.findByLocationCode(request.locationCode())
                .orElseThrow(() -> new NotFoundException("库位不存在"));
        requireWarehouseType(targetLocation, "OWN", "转包返还目标必须是自有仓库");
        kanbans.forEach(this::requireThirdPartyStockKanban);

        String transferNo = nextBusinessNo("TF");
        String remark = defaultRemark(request.remark(), "批量第三方返还");
        List<Kanban> moved = kanbans.stream()
                .map(kanban -> moveOrSplitKanban(kanban, targetLocation, null, STATUS_INBOUND, "OUTSOURCE_RETURN_OUT", "OUTSOURCE_RETURN_IN", transferNo, remark))
                .toList();
        return toKanbanViews(moved, false);
    }
}
