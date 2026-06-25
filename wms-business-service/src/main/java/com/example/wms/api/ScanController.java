/**
 * 本文件实现 ScanController 控制器。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.WmsService;
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

    private final WmsService wmsService;

    public ScanController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    @PostMapping("/inbound")
    public ApiResponse<ScanResultView> inbound(@Valid @RequestBody ScanInboundRequest request) {
        return ApiResponse.ok(wmsService.scanInbound(request));
    }

    @PostMapping("/outbound")
    public ApiResponse<ScanResultView> outbound(@Valid @RequestBody ScanOutboundRequest request) {
        return ApiResponse.ok(wmsService.scanOutbound(request));
    }

    public record ScanInboundRequest(@NotBlank String barcode, String locationCode) {
    }

    public record ScanOutboundRequest(@NotBlank String barcode, String outboundOrderNo) {
    }

    public record ScanResultView(String code,
                                 String message,
                                 String barcode,
                                 String scannedKanbanNo,
                                 String parentKanbanNo,
                                 String outboundOrderNo,
                                 String status,
                                 int affectedCount,
                                 List<String> affectedKanbanNos) {
    }
}
