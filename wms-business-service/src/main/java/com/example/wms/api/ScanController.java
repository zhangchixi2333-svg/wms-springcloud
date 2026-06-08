package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.WmsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扫码操作控制器
 * 为移动端PDA扫码提供接口，支持入库扫码和出库扫码操作
 */
@RestController
@RequestMapping("/api/mobile/scan")
public class ScanController {

    /**
     * WMS核心服务层
     */
    private final WmsService wmsService;

    /**
     * 构造函数，注入WmsService
     * @param wmsService WMS核心服务实例
     */
    public ScanController(WmsService wmsService) {
        this.wmsService = wmsService;
    }

    /**
     * 入库扫码接口
     * 扫码看板条码并绑定到指定库位，完成入库操作
     * @param request 入库扫码请求
     * @return 扫码结果视图ApiResponse
     */
    @PostMapping("/inbound")
    public ApiResponse<ScanResultView> inbound(@Valid @RequestBody ScanInboundRequest request) {
        return ApiResponse.ok(wmsService.scanInbound(request));
    }

    /**
     * 出库扫码接口
     * 扫码看板条码并关联到出库单，完成出库操作
     * @param request 出库扫码请求
     * @return 扫码结果视图ApiResponse
     */
    @PostMapping("/outbound")
    public ApiResponse<ScanResultView> outbound(@Valid @RequestBody ScanOutboundRequest request) {
        return ApiResponse.ok(wmsService.scanOutbound(request));
    }

    /**
     * 入库扫码请求记录
     * @param barcode 看板条码
     * @param locationCode 目标库位编码
     */
    public record ScanInboundRequest(@NotBlank String barcode, @NotBlank String locationCode) {
    }

    /**
     * 出库扫码请求记录
     * @param barcode 看板条码
     * @param outboundOrderNo 关联出库单编号
     */
    public record ScanOutboundRequest(@NotBlank String barcode, @NotBlank String outboundOrderNo) {
    }

    /**
     * 扫码结果视图记录
     * @param code 响应码
     * @param message 响应消息
     * @param barcode 扫码条码
     * @param status 操作状态
     */
    public record ScanResultView(String code, String message, String barcode, String status) {
    }
}
