/**
 * 本文件定义 Agent RAG 检索命中的文档切片。
 */
package com.example.wms.agent.model;

public record RagSnippet(
        Long documentId,
        Long chunkId,
        String docKey,
        String title,
        String content,
        double score,
        String metadataJson
) {
}
