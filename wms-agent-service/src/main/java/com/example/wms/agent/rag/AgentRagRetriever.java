/**
 * 本文件实现 Agent RAG Retriever，当前使用 MySQL 文档切片做本地语义检索，Qdrant 作为可选扩展点。
 */
package com.example.wms.agent.rag;

import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.RagContext;
import com.example.wms.agent.model.RagSnippet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AgentRagRetriever {

    private final JdbcTemplate jdbcTemplate;

    public AgentRagRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RagContext retrieve(AgentRequestContext context, int topK) {
        List<String> tokens = tokenize(context.question());
        List<RagSnippet> candidates = jdbcTemplate.query("""
                SELECT d.id AS document_id, c.id AS chunk_id, d.doc_key, d.title,
                       c.content, c.metadata_json
                FROM agent_rag_chunk c
                JOIN agent_rag_document d ON d.id = c.document_id
                WHERE d.enabled = b'1'
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT 200
                """, (rs, rowNum) -> {
            String content = rs.getString("content");
            String title = rs.getString("title");
            double score = score(tokens, title + "\n" + content);
            return new RagSnippet(
                    rs.getLong("document_id"),
                    rs.getLong("chunk_id"),
                    rs.getString("doc_key"),
                    title,
                    content,
                    score,
                    rs.getString("metadata_json")
            );
        });
        List<RagSnippet> snippets = candidates.stream()
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingDouble(RagSnippet::score).reversed())
                .limit(Math.max(topK, 1))
                .toList();
        String mode = context.ragEnabled() && "qdrant".equalsIgnoreCase(context.ragProvider())
                ? "Qdrant 未启用 Java 客户端时自动降级到 MySQL 本地切片检索。"
                : "MySQL 本地切片检索。";
        return new RagContext(context.ragEnabled(), context.ragProvider(), mode, snippets);
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("[\\s,，。！？；;:：/\\\\|]+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        if (normalized.contains("补货")) tokens.add("补货");
        if (normalized.contains("低库存")) tokens.add("低库存");
        if (normalized.contains("预测")) tokens.add("预测");
        if (normalized.contains("出库")) tokens.add("出库");
        if (normalized.contains("入库")) tokens.add("入库");
        if (normalized.contains("看板")) tokens.add("看板");
        if (normalized.contains("权限")) tokens.add("权限");
        if (tokens.isEmpty() && normalized.length() >= 2) {
            tokens.add(normalized.substring(0, Math.min(12, normalized.length())));
        }
        return new ArrayList<>(tokens);
    }

    private double score(List<String> tokens, String text) {
        if (tokens.isEmpty() || text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase(Locale.ROOT))) {
                score += Math.min(1.0, token.length() / 6.0);
            }
        }
        return score / tokens.size();
    }
}
