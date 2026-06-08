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

/**
 * 库存管理控制器
 * 提供库存查询、手动调整入库、库存交易记录查询等功能
 * 基础路径：/api
 */
@RestController
@RequestMapping("/api")
public class InventoryController {

    /**
     * WMS核心业务服务实例
     */
    private final WmsService wmsService;

    /**
     * 构造函数注入WmsService
     * @param wmsService WMS核心业务服务
     */
    public InventoryController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    /**
     * 获取库存汇总列表
     * 支持根据仓库名称、库区名称、物料关键词、供应商ID筛选库存数据
     * @param warehouseName 仓库名称（可选）
     * @param zoneName 库区名称（可选）
     * @param materialKeyword 物料搜索关键词（可选，支持物料编码/名称模糊搜索）
     * @param supplierId 供应商ID（可选）
     * @return 库存汇总视图列表ApiResponse
     */
    @GetMapping("/inventory")
    public ApiResponse<List<InventorySummaryView>> getInventory(@RequestParam(required = false) String warehouseName,
                                                                @RequestParam(required = false) String zoneName,
                                                                @RequestParam(required = false) String materialKeyword,
                                                                @RequestParam(required = false) Long supplierId) {
        return ApiResponse.ok(wmsService.getInventorySummary(warehouseName, zoneName, materialKeyword, supplierId));
    }

    /**
     * 手动创建库存条目
     * 用于手动调整库存数量，记录入库操作
     * @param request 手动库存录入请求参数
     * @return 更新后的库存汇总视图ApiResponse
     */
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

    /**
     * 获取所有库存交易记录
     * @return 库存交易记录视图列表ApiResponse
     */
    @GetMapping("/inventory/transactions")
    public ApiResponse<List<InventoryTransactionView>> getTransactions() {
        return ApiResponse.ok(wmsService.getTransactions());
    }

    /**
     * 手动库存录入请求参数记录
     * @param partId 物料ID，非空
     * @param locationId 库位ID，非空
     * @param qty 入库数量，必须大于0.001，非空
     * @param remark 操作备注，非空
     */
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

    /**
     * 库存汇总视图记录
     * 用于前端展示库存的聚合数据
     * @param id 库存记录ID
     * @param partId 物料ID
     * @param partCode 物料编码
     * @param partName 物料名称
     * @param supplierName 供应商名称
     * @param locationId 库位ID
     * @param locationCode 库位编码
     * @param warehouseName 仓库名称
     * @param zoneName 库区名称
     * @param qty 当前库存数量
     * @param updatedAt 最后更新时间
     */
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

    /**
     * 库存交易记录视图记录
     * 用于展示库存的变动历史
     * @param id 交易记录ID
     * @param transactionNo 交易流水号
     * @param businessType 业务类型（INBOUND/OUTBOUND/MANUAL等）
     * @param businessNo 关联业务单号（入库单/出库单编号）
     * @param barcode 条码
     * @param partCode 物料编码
     * @param locationCode 库位编码
     * @param qtyChange 变动数量（正数为入库，负数为出库）
     * @param remark 备注
     * @param createdAt 创建时间
     */
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
}
