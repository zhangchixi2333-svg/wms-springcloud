/**
 * 本文件定义 Agent 管线计划执行的工具调用描述。
 */
package com.example.wms.agent.model;

import java.util.Map;

public record AgentToolCall(
        String toolName,
        String toolLabel,
        Map<String, Object> arguments
) {
}
