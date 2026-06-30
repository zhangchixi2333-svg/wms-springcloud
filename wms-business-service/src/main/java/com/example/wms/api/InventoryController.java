/**
 * 本文件实现 InventoryController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.api.OrderController.PageView;
import com.example.wms.service.InventoryOperationService;
import com.example.wms.service.InventoryQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryOperationService inventoryOperationService;

    public InventoryController(InventoryQueryService inventoryQueryService,
                               InventoryOperationService inventoryOperationService) {
        this.inventoryQueryService = inventoryQueryService;
        this.inventoryOperationService = inventoryOperationService;
    }

    @GetMapping("/inventory")
    public ApiResponse<List<InventorySummaryView>> getInventory(@RequestParam(required = false) String warehouseName,
                                                                @RequestParam(required = false) String zoneName,
                                                                @RequestParam(required = false) String materialKeyword,
                                                                @RequestParam(required = false) Long supplierId) {
        return ApiResponse.ok(inventoryQueryService.getInventorySummary(warehouseName, zoneName, materialKeyword, supplierId));
    }

    @GetMapping("/inventory/page")
    public ApiResponse<PageView<InventoryPartSummaryView>> getInventoryPage(@RequestParam(required = false) String warehouseName,
                                                                            @RequestParam(required = false) String zoneName,
                                                                            @RequestParam(required = false) String materialKeyword,
                                                                            @RequestParam(required = false) Long supplierId,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryQueryService.getInventoryPartSummaryPage(warehouseName, zoneName, materialKeyword, supplierId, page, size));
    }

    @GetMapping("/inventory/details/page")
    public ApiResponse<PageView<InventorySummaryView>> getInventoryDetailsPage(@RequestParam(required = false) String partCode,
                                                                               @RequestParam(required = false) String warehouseName,
                                                                               @RequestParam(required = false) String zoneName,
                                                                               @RequestParam(required = false) String materialKeyword,
                                                                               @RequestParam(required = false) Long supplierId,
                                                                               @RequestParam(defaultValue = "1") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryQueryService.getInventoryLocationPage(partCode, warehouseName, zoneName, materialKeyword, supplierId, page, size));
    }

    @GetMapping("/inventory/kanbans/page")
    public ApiResponse<PageView<KanbanView>> getInventoryKanbansPage(@RequestParam(required = false) String partCode,
                                                                     @RequestParam(required = false) String warehouseName,
                                                                     @RequestParam(required = false) String zoneName,
                                                                     @RequestParam(required = false) String kanbanNo,
                                                                     @RequestParam(required = false) Long supplierId,
                                                                     @RequestParam(defaultValue = "1") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryQueryService.getInventoryKanbanPage(partCode, warehouseName, zoneName, kanbanNo, supplierId, page, size));
    }

    @PostMapping("/inventory/manual-entries")
    public ApiResponse<InventorySummaryView> manualEntry(@Valid @RequestBody ManualInventoryEntryRequest request) {
        return ApiResponse.ok(inventoryOperationService.manualInventoryEntry(request));
    }

    @PostMapping("/inventory/kanbans/transfer")
    public ApiResponse<KanbanView> transferKanban(@Valid @RequestBody TransferKanbanRequest request) {
        return ApiResponse.ok(inventoryOperationService.transferKanban(request));
    }

    @PostMapping("/inventory/kanbans/transfer-batch")
    public ApiResponse<List<KanbanView>> transferKanbans(@Valid @RequestBody BatchTransferKanbanRequest request) {
        return ApiResponse.ok(inventoryOperationService.transferKanbans(request));
    }

    @PostMapping("/inventory/kanbans/freeze")
    public ApiResponse<KanbanView> freezeKanban(@Valid @RequestBody FreezeKanbanRequest request) {
        return ApiResponse.ok(inventoryOperationService.freezeKanban(request));
    }

    @PostMapping("/inventory/kanbans/freeze-batch")
    public ApiResponse<List<KanbanView>> freezeKanbans(@Valid @RequestBody BatchFreezeKanbanRequest request) {
        return ApiResponse.ok(inventoryOperationService.freezeKanbans(request));
    }

    @PostMapping("/inventory/kanbans/repack-outbound")
    public ApiResponse<KanbanView> repackOutbound(@Valid @RequestBody RepackOutboundRequest request) {
        return ApiResponse.ok(inventoryOperationService.repackOutbound(request));
    }

    @PostMapping("/inventory/kanbans/repack-outbound-batch")
    public ApiResponse<List<KanbanView>> repackOutboundBatch(@Valid @RequestBody BatchRepackOutboundRequest request) {
        return ApiResponse.ok(inventoryOperationService.repackOutboundBatch(request));
    }

    @PostMapping("/inventory/kanbans/repack-inbound")
    public ApiResponse<KanbanView> repackInbound(@Valid @RequestBody RepackInboundRequest request) {
        return ApiResponse.ok(inventoryOperationService.repackInbound(request));
    }

    @PostMapping("/inventory/kanbans/repack-inbound-batch")
    public ApiResponse<List<KanbanView>> repackInboundBatch(@Valid @RequestBody BatchRepackInboundRequest request) {
        return ApiResponse.ok(inventoryOperationService.repackInboundBatch(request));
    }

    @GetMapping("/inventory/transactions")
    public ApiResponse<List<InventoryTransactionView>> getTransactions() {
        return ApiResponse.ok(inventoryQueryService.getTransactions());
    }

    @GetMapping("/inventory/transactions/page")
    public ApiResponse<PageView<InventoryTransactionView>> getTransactionsPage(@RequestParam(required = false) String partCode,
                                                                               @RequestParam(required = false) String businessType,
                                                                               @RequestParam(required = false) String businessNo,
                                                                               @RequestParam(required = false) String operationNo,
                                                                               @RequestParam(required = false) String barcode,
                                                                               @RequestParam(required = false) String locationCode,
                                                                               @RequestParam(defaultValue = "1") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryQueryService.getTransactionsPage(partCode, businessType, businessNo, operationNo, barcode, locationCode, page, size));
    }

    @GetMapping("/inventory/operation-orders")
    public ApiResponse<List<InventoryOperationOrderView>> getOperationOrders(@RequestParam String operationNo,
                                                                             @RequestParam(required = false) String barcode) {
        return ApiResponse.ok(inventoryQueryService.getOperationOrders(operationNo, barcode));
    }

    @GetMapping("/inventory/transactions/version")
    public ApiResponse<InventoryTransactionVersionView> getTransactionVersion() {
        return ApiResponse.ok(inventoryQueryService.getTransactionVersion());
    }

    public record ManualInventoryEntryRequest(@NotNull Long partId,
                                              @NotNull Long locationId,
                                              @NotNull @DecimalMin("0.001") BigDecimal qty,
                                              @NotBlank String remark) {
    }

    public record TransferKanbanRequest(@NotBlank String barcode,
                                        @NotBlank String inboundOrderNo,
                                        @NotBlank String locationCode,
                                        @DecimalMin("0.001") BigDecimal qty,
                                        String remark) {
    }

    public record BatchTransferKanbanRequest(@NotNull List<@NotBlank String> barcodes,
                                             @NotBlank String locationCode,
                                             String remark) {
    }

    public record FreezeKanbanRequest(@NotBlank String barcode,
                                      boolean frozen,
                                      String remark) {
    }

    public record BatchFreezeKanbanRequest(@NotNull List<@NotBlank String> barcodes,
                                           boolean frozen,
                                           String remark) {
    }

    public record RepackOutboundRequest(@NotBlank String barcode,
                                        @NotBlank String locationCode,
                                        @DecimalMin("0.001") BigDecimal qty,
                                        String remark) {
    }

    public record BatchRepackOutboundRequest(@NotNull List<@NotBlank String> barcodes,
                                             @NotBlank String locationCode,
                                             String remark) {
    }

    public record RepackInboundRequest(@NotBlank String barcode,
                                       @NotBlank String locationCode,
                                       @NotNull @DecimalMin("0.001") BigDecimal qty,
                                       String remark) {
    }

    public record BatchRepackInboundRequest(@NotNull List<@NotBlank String> barcodes,
                                            @NotBlank String locationCode,
                                            String remark) {
    }

    public record InventorySummaryView(Long id,
                                       Long partId,
                                       String partCode,
                                       String partName,
                                       String supplierName,
                                       Long locationId,
                                       String locationCode,
                                       String warehouseName,
                                       String zoneName,
                                       BigDecimal qty,
                                       LocalDateTime updatedAt) {
    }

    public record InventoryPartSummaryView(Long partId,
                                           String partCode,
                                           String partName,
                                           Long supplierId,
                                           String supplierName,
                                           BigDecimal totalQty,
                                           Long locationCount,
                                           LocalDateTime latestUpdatedAt) {
    }

    public record InventoryTransactionView(Long id,
                                           String transactionNo,
                                           String businessType,
                                           String businessNo,
                                           String operationNo,
                                           String barcode,
                                           String partCode,
                                           String locationCode,
                                           BigDecimal qtyChange,
                                           String remark,
                                           LocalDateTime createdAt) {
    }

    public record InventoryOperationOrderView(Long id,
                                              String operationNo,
                                              String operationType,
                                              String businessNo,
                                              String sourceKanbanNo,
                                              String targetKanbanNo,
                                              String sourceBarcode,
                                              String targetBarcode,
                                              Long partId,
                                              String partCode,
                                              String sourceLocationCode,
                                              String targetLocationCode,
                                              BigDecimal qty,
                                              String sourceStatus,
                                              String targetStatus,
                                              String remark,
                                              LocalDateTime createdAt) {
    }

    public record InventoryTransactionVersionView(long total,
                                                  Long latestId,
                                                  String latestTransactionNo,
                                                  LocalDateTime latestCreatedAt) {
    }
}
