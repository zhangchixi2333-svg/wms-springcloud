/**
 * 本文件实现订单与看板接口，返回单据二维码、箱级看板和扫码所需字段。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.InboundOrderService;
import com.example.wms.service.KanbanQueryService;
import com.example.wms.service.OutboundOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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

@RestController
@RequestMapping("/api")
public class OrderController {

    private final InboundOrderService inboundOrderService;
    private final KanbanQueryService kanbanQueryService;
    private final OutboundOrderService outboundOrderService;

    public OrderController(InboundOrderService inboundOrderService,
                           KanbanQueryService kanbanQueryService,
                           OutboundOrderService outboundOrderService) {
        this.inboundOrderService = inboundOrderService;
        this.kanbanQueryService = kanbanQueryService;
        this.outboundOrderService = outboundOrderService;
    }

    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderView>> listInboundOrders(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Long supplierId,
                                                                 @RequestParam(required = false) String inboundNo) {
        return ApiResponse.ok(inboundOrderService.listInboundOrders(status, supplierId, inboundNo));
    }

    @GetMapping("/inbound-orders/page")
    public ApiResponse<PageView<InboundOrderView>> listInboundOrdersPage(@RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) Long supplierId,
                                                                         @RequestParam(required = false) String inboundNo,
                                                                         @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                         @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ApiResponse.ok(inboundOrderService.listInboundOrdersPage(status, supplierId, inboundNo, page, size));
    }

    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderView> createInboundOrder(@Valid @RequestBody InboundOrderCreateRequest request) {
        return ApiResponse.ok(inboundOrderService.createInboundOrder(request));
    }

    @PostMapping("/inbound-orders/{id}/kanbans")
    public ApiResponse<List<KanbanView>> generateKanbans(@PathVariable Long id) {
        return ApiResponse.ok(inboundOrderService.generateKanbans(id));
    }

    @PostMapping("/inbound-orders/{id}/return")
    public ApiResponse<InboundOrderView> returnInboundOrder(@PathVariable Long id) {
        return ApiResponse.ok(inboundOrderService.returnInboundOrder(id));
    }

    @GetMapping("/kanbans")
    public ApiResponse<List<KanbanView>> listKanbans(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String inboundNo,
                                                     @RequestParam(required = false) String outboundNo,
                                                     @RequestParam(required = false) String kanbanNo,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String partCode,
                                                     @RequestParam(required = false) String warehouseName,
                                                     @RequestParam(required = false) String zoneName,
                                                     @RequestParam(required = false) String warehouseType,
                                                     @RequestParam(defaultValue = "false") boolean includeChildren) {
        return ApiResponse.ok(kanbanQueryService.listKanbans(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode, warehouseName, zoneName, warehouseType, includeChildren));
    }

    @GetMapping("/kanbans/page")
    public ApiResponse<PageView<KanbanView>> listKanbansPage(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String inboundNo,
                                                             @RequestParam(required = false) String outboundNo,
                                                             @RequestParam(required = false) String kanbanNo,
                                                             @RequestParam(required = false) Long supplierId,
                                                             @RequestParam(required = false) String partCode,
                                                             @RequestParam(required = false) String warehouseName,
                                                             @RequestParam(required = false) String zoneName,
                                                             @RequestParam(required = false) String warehouseType,
                                                             @RequestParam(defaultValue = "false") boolean includeChildren,
                                                             @RequestParam(defaultValue = "1") @Min(1) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ApiResponse.ok(kanbanQueryService.listKanbansPage(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode, warehouseName, zoneName, warehouseType, includeChildren, page, size));
    }

    @GetMapping("/kanbans/{parentId}/children")
    public ApiResponse<List<KanbanView>> listKanbanChildren(@PathVariable Long parentId) {
        return ApiResponse.ok(kanbanQueryService.listKanbanChildren(parentId));
    }

    @GetMapping("/outbound-orders")
    public ApiResponse<List<OutboundOrderView>> listOutboundOrders(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) Long customerId,
                                                                   @RequestParam(required = false) String outboundNo) {
        return ApiResponse.ok(outboundOrderService.listOutboundOrders(status, customerId, outboundNo));
    }

    @GetMapping("/outbound-orders/page")
    public ApiResponse<PageView<OutboundOrderView>> listOutboundOrdersPage(@RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) Long customerId,
                                                                           @RequestParam(required = false) String outboundNo,
                                                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                                                           @RequestParam(defaultValue = "20") @Min(1) int size) {
        return ApiResponse.ok(outboundOrderService.listOutboundOrdersPage(status, customerId, outboundNo, page, size));
    }

    @PostMapping("/outbound-orders")
    public ApiResponse<OutboundOrderView> createOutboundOrder(@Valid @RequestBody OutboundOrderCreateRequest request) {
        return ApiResponse.ok(outboundOrderService.createOutboundOrder(request));
    }

    @PostMapping("/outbound-orders/{id}/cancel")
    public ApiResponse<OutboundOrderView> cancelOutboundOrder(@PathVariable Long id) {
        return ApiResponse.ok(outboundOrderService.cancelOutboundOrder(id));
    }

    public record InboundOrderCreateRequest(@NotNull Long supplierId, @NotEmpty List<InboundOrderItemRequest> items) {
    }

    public record InboundOrderItemRequest(@NotNull Long partId,
                                          @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                          Integer boxCount,
                                          boolean pendingRepack,
                                          String equipmentCode,
                                          BigDecimal unitPerBox,
                                          String warehouseZone) {
    }

    public record OutboundOrderCreateRequest(Long customerId,
                                             @NotEmpty List<OutboundOrderItemRequest> items) {
    }

    public record OutboundOrderItemRequest(@NotNull Long partId,
                                           @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                           Integer boxCount,
                                           String equipmentCode,
                                           BigDecimal unitPerBox,
                                           String locationCode) {
    }

    public record PageView<T>(List<T> records,
                              long total,
                              int page,
                              int size,
                              int totalPages) {
    }

    public record InboundOrderView(Long id,
                                   String inboundNo,
                                   String qrContent,
                                   Long supplierId,
                                   String supplierName,
                                   String status,
                                   LocalDateTime createdAt,
                                   List<ItemView> items) {

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
                               BigDecimal unitPerBox,
                               String warehouseZone) {
        }
    }

    public record OutboundOrderView(Long id,
                                    String outboundNo,
                                    String qrContent,
                                    Long customerId,
                                    String customerName,
                                    List<String> inboundOrderNos,
                                    String status,
                                    LocalDateTime createdAt,
                                    List<ItemView> items) {

        public record ItemView(Long id,
                               Long kanbanId,
                               String kanbanNo,
                               String allocationDetail,
                               Long partId,
                               String partCode,
                               String partName,
                               String unit,
                               BigDecimal plannedQty,
                               BigDecimal scannedQty,
                               Integer boxCount,
                               String equipmentCode,
                               BigDecimal unitPerBox,
                               String locationCode,
                               String warehouseName,
                               String zoneName) {
        }
    }

    public record KanbanView(Long id,
                             String kanbanNo,
                             String barcode,
                             String qrContent,
                             Long parentKanbanId,
                             boolean parentKanban,
                             Integer boxIndex,
                             String inboundNo,
                             String outboundNo,
                             Long partId,
                             String partCode,
                             String partName,
                             String unit,
                             Long supplierId,
                             String supplierName,
                             String batchNo,
                             BigDecimal qty,
                             BigDecimal availableQty,
                             BigDecimal reservedQty,
                             BigDecimal reservedTransferQty,
                             BigDecimal outboundQty,
                             Long sourceKanbanId,
                             String transferOrderNo,
                             String frozenPreviousStatus,
                             Integer boxCount,
                             boolean pendingRepack,
                             String equipmentCode,
                             String equipmentModel,
                             BigDecimal unitPerBox,
                             String warehouseName,
                             String zoneName,
                             String warehouseType,
                             String status,
                             Long locationId,
                             String locationCode,
                             LocalDateTime createdAt,
                             LocalDateTime inboundTime,
                             LocalDateTime outboundTime,
                             List<KanbanView> children) {
    }
}
