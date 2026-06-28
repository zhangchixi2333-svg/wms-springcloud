/**
 * 本文件实现 InventoryController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.api.OrderController.KanbanView;
import com.example.wms.service.WmsService;
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

    private final WmsService wmsService;

    public InventoryController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    @GetMapping("/inventory")
    public ApiResponse<List<InventorySummaryView>> getInventory(@RequestParam(required = false) String warehouseName,
                                                                @RequestParam(required = false) String zoneName,
                                                                @RequestParam(required = false) String materialKeyword,
                                                                @RequestParam(required = false) Long supplierId) {
        return ApiResponse.ok(wmsService.getInventorySummary(warehouseName, zoneName, materialKeyword, supplierId));
    }

    @PostMapping("/inventory/manual-entries")
    public ApiResponse<InventorySummaryView> manualEntry(@Valid @RequestBody ManualInventoryEntryRequest request) {
        return ApiResponse.ok(wmsService.manualInventoryEntry(request));
    }

    @PostMapping("/inventory/kanbans/transfer")
    public ApiResponse<KanbanView> transferKanban(@Valid @RequestBody TransferKanbanRequest request) {
        return ApiResponse.ok(wmsService.transferKanban(request));
    }

    @PostMapping("/inventory/kanbans/freeze")
    public ApiResponse<KanbanView> freezeKanban(@Valid @RequestBody FreezeKanbanRequest request) {
        return ApiResponse.ok(wmsService.freezeKanban(request));
    }

    @PostMapping("/inventory/kanbans/repack-outbound")
    public ApiResponse<KanbanView> repackOutbound(@Valid @RequestBody RepackOutboundRequest request) {
        return ApiResponse.ok(wmsService.repackOutbound(request));
    }

    @PostMapping("/inventory/kanbans/repack-inbound")
    public ApiResponse<KanbanView> repackInbound(@Valid @RequestBody RepackInboundRequest request) {
        return ApiResponse.ok(wmsService.repackInbound(request));
    }

    @PostMapping("/inventory/kanbans/balance")
    public ApiResponse<KanbanView> adjustKanbanBalance(@Valid @RequestBody KanbanBalanceRequest request) {
        return ApiResponse.ok(wmsService.adjustKanbanBalance(request));
    }

    @GetMapping("/inventory/transactions")
    public ApiResponse<List<InventoryTransactionView>> getTransactions() {
        return ApiResponse.ok(wmsService.getTransactions());
    }

    @GetMapping("/inventory/transactions/version")
    public ApiResponse<InventoryTransactionVersionView> getTransactionVersion() {
        return ApiResponse.ok(wmsService.getTransactionVersion());
    }

    public record ManualInventoryEntryRequest(@NotNull Long partId,
                                              @NotNull Long locationId,
                                              @NotNull @DecimalMin("0.001") BigDecimal qty,
                                              @NotBlank String remark) {
    }

    public record TransferKanbanRequest(@NotBlank String barcode,
                                        @NotBlank String inboundOrderNo,
                                        @NotBlank String locationCode,
                                        String remark) {
    }

    public record FreezeKanbanRequest(@NotBlank String barcode,
                                      boolean frozen,
                                      String remark) {
    }

    public record RepackOutboundRequest(@NotBlank String barcode,
                                        @NotBlank String locationCode,
                                        String remark) {
    }

    public record RepackInboundRequest(@NotBlank String barcode,
                                       @NotBlank String locationCode,
                                       @NotNull @DecimalMin("0.001") BigDecimal qty,
                                       String remark) {
    }

    public record KanbanBalanceRequest(@NotBlank String barcode,
                                       @NotNull @DecimalMin("0.001") BigDecimal qty,
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

    public record InventoryTransactionView(Long id,
                                           String transactionNo,
                                           String businessType,
                                           String businessNo,
                                           String barcode,
                                           String partCode,
                                           String locationCode,
                                           BigDecimal qtyChange,
                                           String remark,
                                           LocalDateTime createdAt) {
    }

    public record InventoryTransactionVersionView(long total,
                                                  Long latestId,
                                                  String latestTransactionNo,
                                                  LocalDateTime latestCreatedAt) {
    }
}
