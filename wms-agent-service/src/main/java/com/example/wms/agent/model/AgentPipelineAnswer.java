/**
 * 本文件定义 Agent 管线最终返回给前端的完整问答结果。
 */
package com.example.wms.agent.model;

import com.example.wms.service.AgentAnalysisService.AgentSuggestionDto;

import java.util.List;

public record AgentPipelineAnswer(
        String answer,
        boolean callApi,
        List<AgentSuggestionDto> suggestions,
        String traceNo,
        AgentPlan plan,
        AgentMemoryBundle memory,
        RagContext rag,
        List<AgentToolResult> toolResults,
        ReflectionResult reflection,
        long latencyMs
) {
}
