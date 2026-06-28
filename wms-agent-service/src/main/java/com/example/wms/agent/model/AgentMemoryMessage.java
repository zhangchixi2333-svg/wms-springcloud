/**
 * 本文件定义 Agent 读取最近对话和历史消息时使用的消息结构。
 */
package com.example.wms.agent.model;

import java.time.LocalDateTime;

public record AgentMemoryMessage(
        String role,
        String content,
        LocalDateTime createdAt
) {
}
