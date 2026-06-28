/**
 * 本文件定义 Agent Planner 输出的路由结果、意图和后续工具调用计划。
 */
package com.example.wms.agent.model;

import java.util.List;

public record AgentPlan(
        String intent,
        AgentRouteLevel routeLevel,
        String routeLabel,
        double confidence,
        String reason,
        boolean llmRequired,
        List<AgentToolCall> toolCalls
) {
}
