/**
 * 本文件实现智能助手 API，提供库存预测、建议、问答和 RAG 文档入口。
 */
package com.example.wms.api;

import com.example.wms.common.ApiResponse;
import com.example.wms.service.AgentAnalysisService;
import com.example.wms.service.AgentAnalysisService.AgentAskRequest;
import com.example.wms.service.AgentAnalysisService.RagDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentAnalysisService agentAnalysisService;

    public AgentController(AgentAnalysisService agentAnalysisService) {
        this.agentAnalysisService = agentAnalysisService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(agentAnalysisService.health());
    }

    @GetMapping("/overview")
    public ApiResponse<AgentAnalysisService.AgentOverview> overview() {
        return ApiResponse.ok(agentAnalysisService.overview());
    }

    @GetMapping("/dashboard")
    public ApiResponse<AgentAnalysisService.AgentDashboard> dashboard(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(agentAnalysisService.dashboard(days));
    }

    @GetMapping("/forecast/inventory")
    public ApiResponse<java.util.List<AgentAnalysisService.ForecastRow>> inventoryForecast(
            @RequestParam(required = false) Integer days
    ) {
        return ApiResponse.ok(agentAnalysisService.forecast(days));
    }

    @GetMapping("/suggestions")
    public ApiResponse<java.util.List<AgentAnalysisService.AgentSuggestionDto>> suggestions() {
        return ApiResponse.ok(agentAnalysisService.suggestions());
    }

    @PostMapping("/analyze")
    public ApiResponse<AgentAnalysisService.AgentRunDto> analyze(@RequestParam(required = false) Integer days) {
        return ApiResponse.ok(agentAnalysisService.analyze(days));
    }

    @PostMapping("/ask")
    public ApiResponse<AgentAnalysisService.AgentAnswer> ask(@Valid @RequestBody AgentAskRequest request) {
        return ApiResponse.ok(agentAnalysisService.ask(request));
    }

    @GetMapping("/rag/documents")
    public ApiResponse<java.util.List<AgentAnalysisService.RagDocumentDto>> ragDocuments() {
        return ApiResponse.ok(agentAnalysisService.ragDocuments());
    }

    @PostMapping("/rag/documents")
    public ApiResponse<AgentAnalysisService.RagDocumentDto> createRagDocument(@Valid @RequestBody RagDocumentRequest request) {
        return ApiResponse.ok(agentAnalysisService.createRagDocument(request));
    }
}
