/**
 * 本文件定义 Agent 工具执行后的统一结果结构。
 */
package com.example.wms.agent.model;

public record AgentToolResult(
        String toolName,
        String toolLabel,
        boolean success,
        String summary,
        Object data,
        long latencyMs
) {
}
