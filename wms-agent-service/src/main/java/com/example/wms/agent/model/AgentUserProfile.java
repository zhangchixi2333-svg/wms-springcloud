/**
 * 本文件定义 Agent 在 MySQL 中维护的长期用户画像摘要。
 */
package com.example.wms.agent.model;

import java.time.LocalDateTime;

public record AgentUserProfile(
        String sessionId,
        String preferredTopic,
        String lastIntent,
        int totalMessages,
        String summary,
        LocalDateTime updatedAt
) {
}
