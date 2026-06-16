/**
 * 本文件实现 OrderController 控制器。
 */
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

@RestController
@RequestMapping("/api")
public class OrderController {

    private final WmsService wmsService;

    public OrderController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderView>> listInboundOrders(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) Long supplierId,
                                                                 @RequestParam(required = false) String inboundNo) {
        return ApiResponse.ok(wmsService.listInboundOrders(status, supplierId, inboundNo));
    }

    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderView> createInboundOrder(@Valid @RequestBody InboundOrderCreateRequest request) {
        return ApiResponse.ok(wmsService.createInboundOrder(request));
    }

    @PostMapping("/inbound-orders/{id}/kanbans")
    public ApiResponse<List<KanbanView>> generateKanbans(@PathVariable Long id) {
        return ApiResponse.ok(wmsService.generateKanbans(id));
    }

    @GetMapping("/kanbans")
    public ApiResponse<List<KanbanView>> listKanbans(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String inboundNo,
                                                     @RequestParam(required = false) String outboundNo,
                                                     @RequestParam(required = false) String kanbanNo,
                                                     @RequestParam(required = false) Long supplierId,
                                                     @RequestParam(required = false) String partCode) {
        return ApiResponse.ok(wmsService.listKanbans(status, inboundNo, outboundNo, kanbanNo, supplierId, partCode));
    }

    @GetMapping("/outbound-orders")
    public ApiResponse<List<OutboundOrderView>> listOutboundOrders(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) Long customerId,
                                                                   @RequestParam(required = false) String outboundNo) {
        return ApiResponse.ok(wmsService.listOutboundOrders(status, customerId, outboundNo));
    }

    @PostMapping("/outbound-orders")
    public ApiResponse<OutboundOrderView> createOutboundOrder(@Valid @RequestBody OutboundOrderCreateRequest request) {
        return ApiResponse.ok(wmsService.createOutboundOrder(request));
    }

    public record InboundOrderCreateRequest(@NotNull Long supplierId, @NotEmpty List<InboundOrderItemRequest> items) {
    }

    public record InboundOrderItemRequest(@NotNull Long partId,
                                          @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                          @NotNull Integer boxCount,
                                          boolean pendingRepack,
                                          String equipmentCode,
                                          @NotNull @DecimalMin("0.001") BigDecimal packageCapacity,
                                          @NotBlank String warehouseZone) {
    }

    public record OutboundOrderCreateRequest(Long customerId,
                                             @NotEmpty List<String> inboundOrderNos,
                                             @NotEmpty List<OutboundOrderItemRequest> items) {
    }

    public record OutboundOrderItemRequest(@NotNull Long partId,
                                           @NotNull @DecimalMin("0.001") BigDecimal plannedQty,
                                           @NotBlank String warehouseName,
                                           @NotBlank String zoneName) {
    }

    public record InboundOrderView(Long id,
                                   String inboundNo,
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
                               BigDecimal packageCapacity,
                               String warehouseZone) {
        }
    }

    public record OutboundOrderView(Long id,
                                    String outboundNo,
                                    Long customerId,
                                    String customerName,
                                    List<String> inboundOrderNos,
                                    String status,
                                    LocalDateTime createdAt,
                                    List<ItemView> items) {

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
