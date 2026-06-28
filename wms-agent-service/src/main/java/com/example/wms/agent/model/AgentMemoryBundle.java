/**
 * 本文件定义 Agent Memory Manager 聚合后的短期、历史、画像和语义记忆。
 */
package com.example.wms.agent.model;

import java.util.List;

public record AgentMemoryBundle(
        String sessionId,
        String recentSource,
        List<AgentMemoryMessage> recentMessages,
        List<AgentMemoryMessage> historicalMessages,
        AgentUserProfile profile,
        List<SemanticMemory> semanticMemories
) {
}
