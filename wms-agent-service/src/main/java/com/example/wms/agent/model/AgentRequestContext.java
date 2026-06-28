/**
 * 本文件定义一次 Agent 问答请求在管线中流转时使用的核心上下文。
 */
package com.example.wms.agent.model;

import java.time.LocalDateTime;

public record AgentRequestContext(
        String traceNo,
        String sessionId,
        String question,
        int forecastDays,
        boolean callApi,
        boolean ragEnabled,
        String ragProvider,
        LocalDateTime requestedAt
) {
}
