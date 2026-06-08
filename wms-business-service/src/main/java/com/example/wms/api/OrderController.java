package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.WmsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单管理控制器
 * 提供入库单、出库单、看板的创建、查询等功能
 * 基础路径：/api
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    /**
     * WMS核心业务服务实例
     */
    private final WmsService wmsService;

    /**
     * 构造函数注入WmsService
     * @param wmsService WMS核心业务服务
     */
    public OrderController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    /**
     * 获取入库单列表
     * 支持根据状态、供应商ID、入库单编号筛选
     * @param status 入库单状态（可选）
     * @param supplierId 供应商ID（可选）
     * @param inboundNo 入库单编号（可选，模糊搜索）
     * @return 入库单视图列表ApiResponse
     */
    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderView>> listInboundOrders(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Long supplierId,
                                                                 @RequestParam(required = false) String inboundNo) {
        return ApiResponse.ok(wmsService.listInboundOrders(status, supplierId, inboundNo));
    }

    /**
     * 创建入库单
     * @param request 入库单创建请求参数
     * @return 创建后的入库单视图ApiResponse
     */
    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderView> createInboundOrder(@Valid @RequestBody InboundOrderCreateRequest request) {
        return ApiResponse.ok(wmsService.createInboundOrder(request));
    }

    /**
     * 为指定入库单生成看板（标签）
     * 用于打印物料标签，每个箱子生成一个看板记录
     * @param id 入库单ID
     * @return 生成的看板列表ApiResponse
     */
    @PostMapping("/inbound-orders/{id}/kanbans")
    public ApiResponse<List<KanbanView>> generateKanbans(@PathVariable Long id) {
        return ApiResponse.ok(wmsService.generateKanbans(id));
    }

    /**
     * 获取看板列表
     * 支持多条件筛选看板数据
     * @param status 看板状态（可选）
     * @param inboundNo 关联入库单编号（可选）
     * @param outboundNo 关联出库单编号（可选）
     * @param kanbanNo 看板编号（可选，模糊搜索）
     * @param supplierId 供应商ID（可选）
     * @param partCode 物料编码（可选）
     * @return 看板视图列表ApiResponse
     */
    @GetMapping("/kanbans")
    public ApiResponse<List<KanbanView>> listKanbans(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String inboundNo,
                                                     @RequestParam(required = false) String outboundNo,
                                                     @RequestParam(required = false) String kanbanNo,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String partCode) {
        return ApiResponse.ok(wmsService.listKanbans(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode));
    }

    /**
     * 获取出库单列表
     * 支持根据状态、客户ID、出库单编号筛选
     * @param status 出库单状态（可选）
     * @param customerId 客户ID（可选）
     * @param outboundNo 出库单编号（可选，模糊搜索）
     * @return 出库单视图列表ApiResponse
     */
    @GetMapping("/outbound-orders")
    public ApiResponse<List<OutboundOrderView>> listOutboundOrders(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) Long customerId,
                                                                   @RequestParam(required = false) String outboundNo) {
        return ApiResponse.ok(wmsService.listOutboundOrders(status, customerId, outboundNo));
    }

    /**
     * 创建出库单
     * @param request 出库单创建请求参数
     * @return 创建后的出库单视图ApiResponse
     */
    @PostMapping("/outbound-orders")
    public ApiResponse<OutboundOrderView> createOutboundOrder(@Valid @RequestBody OutboundOrderCreateRequest request) {
        return ApiResponse.ok(wmsService.createOutboundOrder(request));
    }

    /**
     * 入库单创建请求参数记录
     * @param supplierId 供应商ID，非空
     * @param items 入库单行项目列表，非空，至少包含一个物料
     */
    public record InboundOrderCreateRequest(@NotNull Long supplierId, @NotEmpty List<InboundOrderItemRequest> items) {
    }

    /**
     * 入库单行项目请求参数记录
     * @param partId 物料ID，非空
     * @param plannedQty 计划入库数量，必须大于0.001，非空
     * @param boxCount 箱子数量，非空
     * @param pendingRepack 是否需要转包
     * @param equipmentCode 使用的器具编码
     * @param packageCapacity 包装容量，每个箱子的容量，必须大于0.001，非空
     * @param warehouseZone 入库库区，非空
     */
    public record InboundOrderItemRequest(@NotNull Long partId,
                                          @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                          @NotNull Integer boxCount,
                                          boolean pendingRepack,
                                          String equipmentCode,
                                          @NotNull @DecimalMin("0.001") BigDecimal packageCapacity,
                                          @NotBlank String warehouseZone) {
    }

    /**
     * 出库单创建请求参数记录
     * @param customerId 客户ID
     * @param inboundOrderNos 来源入库单编号列表
     * @param items 出库单行项目列表，非空，至少包含一个物料
     */
    public record OutboundOrderCreateRequest(Long customerId,
                                             @NotEmpty List<String> inboundOrderNos,
                                             @NotEmpty List<OutboundOrderItemRequest> items) {
    }

    /**
     * 出库单行项目请求参数记录
     * @param partId 物料ID，非空
     * @param plannedQty 计划出库数量，必须大于0.001，非空
     * @param warehouseName 出库仓库名称，非空
     * @param zoneName 出库库区名称，非空
     */
    public record OutboundOrderItemRequest(@NotNull Long partId,
                                           @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                           @NotBlank String warehouseName,
                                           @NotBlank String zoneName) {
    }

    /**
     * 入库单视图记录
     * 用于前端展示入库单的详细信息
     * @param id 入库单ID
     * @param inboundNo 入库单编号
     * @param supplierId 供应商ID
     * @param supplierName 供应商名称
     * @param status 入库单状态
     * @param createdAt 创建时间
     * @param items 入库单行项目列表
     */
    public record InboundOrderView(Long id,
                                   String inboundNo,
                                   Long supplierId,
                                   String supplierName,
                                   String status,
                                   LocalDateTime createdAt,
                                   List<ItemView> items) {
        /**
         * 入库单行项目视图记录
         * @param id 行项目ID
         * @param partId 物料ID
         * @param partCode 物料编码
         * @param partName 物料名称
         * @param unit 计量单位
         * @param plannedQty 计划入库数量
         * @param receivedQty 实际已入库数量
         * @param boxCount 箱子数量
         * @param pendingRepack 是否有待转包
         * @param equipmentCode 使用的器具编码
         * @param packageCapacity 包装容量
         * @param warehouseZone 入库库区
         */
        public record ItemView(Long id,
                               Long partId,
                               String partCode,
                               String partName,
                               String unit,
                               BigDecimal plannedQty,
                               BigDecimal receivedQty,
                               Integer boxCount,
                               boolean pendingRepack,
                               String equipmentCode,
                               BigDecimal packageCapacity,
                               String warehouseZone) {
        }
    }

    /**
     * 出库单视图记录
     * 用于前端展示出库单的详细信息
     * @param id 出库单ID
     * @param outboundNo 出库单编号
     * @param customerId 客户ID
     * @param customerName 客户名称
     * @param inboundOrderNos 来源入库单编号列表
     * @param status 出库单状态
     * @param createdAt 创建时间
     * @param items 出库单行项目列表
     */
    public record OutboundOrderView(Long id,
                                    String outboundNo,
                                    Long customerId,
                                    String customerName,
                                    List<String> inboundOrderNos,
                                    String status,
                                    LocalDateTime createdAt,
                                    List<ItemView> items) {
        /**
         * 出库单行项目视图记录
         * @param id 行项目ID
         * @param partId 物料ID
         * @param partCode 物料编码
         * @param partName 物料名称
         * @param unit 计量单位
         * @param plannedQty 计划出库数量
         * @param scannedQty 实际已扫描出库数量
         * @param warehouseName 出库仓库名称
         * @param zoneName 出库库区名称
         */
        public record ItemView(Long id,
                               Long partId,
                               String partCode,
                               String partName,
                               String unit,
                               BigDecimal plannedQty,
                               BigDecimal scannedQty,
                               String warehouseName,
                               String zoneName) {
        }
    }

    /**
     * 看板视图记录
     * 用于展示仓库物料看板（标签）的详细信息
     * @param id 看板ID
     * @param kanbanNo 看板编号
     * @param barcode 条码内容
     * @param qrContent 二维码内容
     * @param inboundNo 关联入库单编号
     * @param outboundNo 关联出库单编号
     * @param partCode 物料编码
     * @param partName 物料名称
     * @param unit 计量单位
     * @param supplierId 供应商ID
     * @param supplierName 供应商名称
     * @param batchNo 批次号
     * @param qty 看板承载数量
     * @param boxCount 箱子数量
     * @param pendingRepack 是否需要转包
     * @param equipmentCode 使用的器具编码
     * @param equipmentModel 器具规格型号
     * @param packageCapacity 包装容量
     * @param warehouseName 所属仓库名称
     * @param zoneName 所属库区名称
     * @param status 看板状态
     * @param locationCode 绑定的库位编码
     * @param createdAt 创建时间
     * @param inboundTime 入库时间
     * @param outboundTime 出库时间
     */
    public record KanbanView(Long id,
                             String kanbanNo,
                             String barcode,
                             String qrContent,
                             String inboundNo,
                             String outboundNo,
                             String partCode,
                             String partName,
                             String unit,
                             Long supplierId,
                             String supplierName,
                             String batchNo,
                             BigDecimal qty,
                             Integer boxCount,
                             boolean pendingRepack,
                             String equipmentCode,
                             String equipmentModel,
                             BigDecimal packageCapacity,
                             String warehouseName,
                             String zoneName,
                             String status,
                             String locationCode,
                             LocalDateTime createdAt,
                             LocalDateTime inboundTime,
                             LocalDateTime outboundTime) {
    }
}
