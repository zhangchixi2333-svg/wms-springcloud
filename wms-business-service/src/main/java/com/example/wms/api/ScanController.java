/**
 * 本文件实现 ScanController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.ScanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/scan")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping("/inbound")
    public ApiResponse<ScanResultView> inbound(@Valid @RequestBody ScanInboundRequest request) {
        return ApiResponse.ok(scanService.scanInbound(request));
    }

    @PostMapping("/inbound-batch")
    public ApiResponse<ScanResultView> inboundBatch(@Valid @RequestBody ScanInboundBatchRequest request) {
        return ApiResponse.ok(scanService.scanInboundBatch(request));
    }

    @PostMapping("/outbound")
    public ApiResponse<ScanResultView> outbound(@Valid @RequestBody ScanOutboundRequest request) {
        return ApiResponse.ok(scanService.scanOutbound(request));
    }

    public record ScanInboundRequest(@NotBlank String barcode, String locationCode) {
    }

    public record ScanInboundBatchRequest(String scanCode,
                                          String parentBarcode,
                                          String locationCode,
                                          List<Long> childKanbanIds) {
    }

    public record ScanOutboundRequest(@NotBlank String barcode, String outboundOrderNo) {
    }

    public record ScanResultView(String code,
                                 String message,
                                 String barcode,
                                 String scannedKanbanNo,
                                 String inboundOrderNo,
                                 String outboundOrderNo,
                                 String status,
                                 int affectedCount,
                                 List<String> affectedKanbanNos) {
    }
}
