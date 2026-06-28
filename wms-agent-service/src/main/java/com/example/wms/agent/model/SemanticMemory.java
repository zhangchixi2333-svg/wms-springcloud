/**
 * 本文件定义 Agent 语义记忆的本地表示，后续可映射到 Qdrant 向量检索结果。
 */
package com.example.wms.agent.model;

import java.util.Map;

public record SemanticMemory(
        String source,
        String title,
        String content,
        double score,
        Map<String, Object> metadata
) {
}
