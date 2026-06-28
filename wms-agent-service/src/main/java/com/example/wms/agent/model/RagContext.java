/**
 * 本文件定义 Agent RAG Retriever 输出的上下文集合与降级状态。
 */
package com.example.wms.agent.model;

import java.util.List;

public record RagContext(
        boolean enabled,
        String provider,
        String mode,
        List<RagSnippet> snippets
) {
}
