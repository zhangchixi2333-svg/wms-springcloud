/**
 * 本文件实现 Agent Memory Manager，优先使用 MySQL 保存历史和长期画像，Redis/Qdrant 暂按可选能力降级。
 */
package com.example.wms.agent.memory;

import com.example.wms.agent.model.AgentMemoryBundle;
import com.example.wms.agent.model.AgentMemoryMessage;
import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.AgentUserProfile;
import com.example.wms.agent.model.SemanticMemory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AgentMemoryManager {

    private final JdbcTemplate jdbcTemplate;

    public AgentMemoryManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentMemoryBundle load(AgentRequestContext context) {
        List<AgentMemoryMessage> recentMessages = jdbcTemplate.query("""
                SELECT role, content, created_at
                FROM agent_chat_message
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 8
                """, (rs, rowNum) -> new AgentMemoryMessage(
                rs.getString("role"),
                rs.getString("content"),
                rs.getObject("created_at", LocalDateTime.class)
        ), context.sessionId()).reversed();

        List<AgentMemoryMessage> historicalMessages = jdbcTemplate.query("""
                SELECT role, content, created_at
                FROM agent_chat_message
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 30
                """, (rs, rowNum) -> new AgentMemoryMessage(
                rs.getString("role"),
                rs.getString("content"),
                rs.getObject("created_at", LocalDateTime.class)
        ), context.sessionId()).reversed();

        AgentUserProfile profile = loadProfile(context.sessionId())
                .orElse(new AgentUserProfile(context.sessionId(), "库存分析", "LOCAL_FALLBACK", 0, "暂无长期画像。", null));
        List<SemanticMemory> semanticMemories = searchSemanticMemory(context.question(), 5);
        return new AgentMemoryBundle(
                context.sessionId(),
                "MySQL 历史消息；Redis 最近对话为预留能力，未启用时自动降级。",
                recentMessages,
                historicalMessages,
                profile,
                semanticMemories
        );
    }

    @Transactional
    public void persistInteraction(AgentRequestContext context, String answer, String intent) {
        jdbcTemplate.update("""
                INSERT INTO agent_chat_message (session_id, role, content, call_api, created_at)
                VALUES (?, 'USER', ?, ?, NOW(6)), (?, 'ASSISTANT', ?, ?, NOW(6))
                """,
                context.sessionId(),
                context.question(),
                context.callApi(),
                context.sessionId(),
                answer,
                context.callApi()
        );
        upsertProfile(context, answer, intent);
    }

    private Optional<AgentUserProfile> loadProfile(String sessionId) {
        return jdbcTemplate.query("""
                SELECT session_id, preferred_topic, last_intent, total_messages, summary, updated_at
                FROM agent_user_profile
                WHERE session_id = ?
                """, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new AgentUserProfile(
                    rs.getString("session_id"),
                    rs.getString("preferred_topic"),
                    rs.getString("last_intent"),
                    rs.getInt("total_messages"),
                    rs.getString("summary"),
                    rs.getObject("updated_at", LocalDateTime.class)
            ));
        }, sessionId);
    }

    private void upsertProfile(AgentRequestContext context, String answer, String intent) {
        String preferredTopic = inferTopic(context.question());
        String summary = "最近关注：" + preferredTopic + "；上次意图：" + intent + "；最近回答摘要：" + left(answer, 160);
        jdbcTemplate.update("""
                INSERT INTO agent_user_profile
                (session_id, preferred_topic, last_intent, total_messages, summary, updated_at)
                VALUES (?, ?, ?, 2, ?, NOW(6))
                ON DUPLICATE KEY UPDATE
                  preferred_topic = VALUES(preferred_topic),
                  last_intent = VALUES(last_intent),
                  total_messages = total_messages + 2,
                  summary = VALUES(summary),
                  updated_at = NOW(6)
                """, context.sessionId(), preferredTopic, intent, summary);
    }

    private List<SemanticMemory> searchSemanticMemory(String question, int limit) {
        String like = "%" + normalizeLike(question) + "%";
        return jdbcTemplate.query("""
                SELECT d.title, c.content, c.metadata_json
                FROM agent_rag_chunk c
                JOIN agent_rag_document d ON d.id = c.document_id
                WHERE d.enabled = b'1'
                  AND (c.content LIKE ? OR d.title LIKE ?)
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new SemanticMemory(
                "MySQL RAG",
                rs.getString("title"),
                rs.getString("content"),
                0.40,
                Map.of("metadataJson", Optional.ofNullable(rs.getString("metadata_json")).orElse("{}"))
        ), like, like, limit);
    }

    private String inferTopic(String question) {
        if (question.contains("补货") || question.contains("低库存")) return "补货预警";
        if (question.contains("预测") || question.contains("趋势")) return "库存预测";
        if (question.contains("呆滞") || question.contains("周转")) return "呆滞库存";
        if (question.contains("菜单") || question.contains("权限")) return "系统管理";
        return "仓储问答";
    }

    private String normalizeLike(String text) {
        String trimmed = Optional.ofNullable(text).orElse("").trim();
        if (trimmed.length() <= 24) return trimmed;
        return trimmed.substring(0, 24);
    }

    private String left(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength);
    }
}
