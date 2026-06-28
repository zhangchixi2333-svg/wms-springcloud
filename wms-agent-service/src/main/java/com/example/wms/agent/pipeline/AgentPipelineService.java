/**
 * 本文件实现 Agent 问答总管线，串联 Planner、Memory、RAG、工具编排、回答生成和 Reflection。
 */
package com.example.wms.agent.pipeline;

import com.example.wms.agent.memory.AgentMemoryManager;
import com.example.wms.agent.model.AgentMemoryBundle;
import com.example.wms.agent.model.AgentPipelineAnswer;
import com.example.wms.agent.model.AgentPlan;
import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.AgentToolResult;
import com.example.wms.agent.model.RagContext;
import com.example.wms.agent.model.ReflectionResult;
import com.example.wms.agent.rag.AgentRagRetriever;
import com.example.wms.agent.reflection.AgentReflectionService;
import com.example.wms.agent.tool.AgentToolOrchestrator;
import com.example.wms.config.AgentProperties;
import com.example.wms.service.AgentAnalysisService;
import com.example.wms.service.AgentAnalysisService.AgentAskRequest;
import com.example.wms.service.AgentAnalysisService.AgentSuggestionDto;
import com.example.wms.service.AgentAnalysisService.ForecastRow;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentPipelineService {

    private final AgentProperties properties;
    private final AgentPlanner planner;
    private final AgentMemoryManager memoryManager;
    private final AgentRagRetriever ragRetriever;
    private final AgentToolOrchestrator toolOrchestrator;
    private final AgentReflectionService reflectionService;
    private final JdbcTemplate jdbcTemplate;

    public AgentPipelineService(
            AgentProperties properties,
            AgentPlanner planner,
            AgentMemoryManager memoryManager,
            AgentRagRetriever ragRetriever,
            AgentToolOrchestrator toolOrchestrator,
            AgentReflectionService reflectionService,
            JdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.planner = planner;
        this.memoryManager = memoryManager;
        this.ragRetriever = ragRetriever;
        this.toolOrchestrator = toolOrchestrator;
        this.reflectionService = reflectionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public AgentPipelineAnswer ask(AgentAskRequest request) {
        long started = System.currentTimeMillis();
        AgentRequestContext context = new AgentRequestContext(
                "TRACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                Optional.ofNullable(request.sessionId()).filter(value -> !value.isBlank()).orElse("default"),
                request.question().trim(),
                properties.getForecastDays(),
                properties.isCallApi(),
                properties.getRag().isEnabled(),
                properties.getRag().getProvider(),
                LocalDateTime.now()
        );
        AgentPlan plan = planner.plan(context);
        AgentMemoryBundle memory = safeLoadMemory(context);
        RagContext rag = safeRetrieveRag(context);
        List<AgentToolResult> toolResults = toolOrchestrator.execute(context, plan, rag);
        String answer = composeAnswer(context, plan, memory, rag, toolResults);
        ReflectionResult reflection = reflectionService.reflect(context, plan, toolResults, answer);
        List<AgentSuggestionDto> suggestions = collectSuggestions(toolResults);
        safePersist(context, answer, plan.intent());
        long latencyMs = System.currentTimeMillis() - started;
        safeTrace(context, plan, toolResults, reflection, latencyMs);
        return new AgentPipelineAnswer(
                answer,
                properties.isCallApi(),
                suggestions,
                context.traceNo(),
                plan,
                memory,
                rag,
                toolResults,
                reflection,
                latencyMs
        );
    }

    private AgentMemoryBundle safeLoadMemory(AgentRequestContext context) {
        try {
            return memoryManager.load(context);
        } catch (DataAccessException ex) {
            return new AgentMemoryBundle(
                    context.sessionId(),
                    "记忆表不可用，已降级为无历史记忆。",
                    List.of(),
                    List.of(),
                    null,
                    List.of()
            );
        }
    }

    private RagContext safeRetrieveRag(AgentRequestContext context) {
        try {
            return ragRetriever.retrieve(context, 5);
        } catch (DataAccessException ex) {
            return new RagContext(context.ragEnabled(), context.ragProvider(), "RAG 表不可用，已降级为无知识库上下文。", List.of());
        }
    }

    private void safePersist(AgentRequestContext context, String answer, String intent) {
        try {
            memoryManager.persistInteraction(context, answer, intent);
        } catch (DataAccessException ignored) {
            // 记忆失败不能影响主问答结果。
        }
    }

    private void safeTrace(
            AgentRequestContext context,
            AgentPlan plan,
            List<AgentToolResult> toolResults,
            ReflectionResult reflection,
            long latencyMs
    ) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO agent_pipeline_trace
                    (trace_no, session_id, question, route_level, intent, confidence, tool_count,
                     reflection_passed, warning_count, latency_ms, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(6))
                    """,
                    context.traceNo(),
                    context.sessionId(),
                    context.question(),
                    plan.routeLevel().name(),
                    plan.intent(),
                    plan.confidence(),
                    toolResults.size(),
                    reflection.passed(),
                    reflection.warnings().size(),
                    latencyMs
            );
        } catch (DataAccessException ignored) {
            // 追踪表缺失时允许降级，初始化 SQL 会补齐。
        }
    }

    private String composeAnswer(
            AgentRequestContext context,
            AgentPlan plan,
            AgentMemoryBundle memory,
            RagContext rag,
            List<AgentToolResult> toolResults
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("我按 ").append(plan.routeLabel()).append(" 处理了你的问题。");
        builder.append(plan.reason()).append("\n\n");
        List<String> summaries = toolResults.stream().map(AgentToolResult::summary).toList();
        if (!summaries.isEmpty()) {
            builder.append("工具结果：").append(String.join("；", summaries)).append("。\n");
        }
        if ("REPLENISHMENT".equals(plan.intent()) || "LOCAL_FALLBACK".equals(plan.intent())) {
            appendSuggestionSummary(builder, toolResults);
        } else if ("FORECAST".equals(plan.intent())) {
            appendForecastSummary(builder, toolResults, context.forecastDays());
        } else if ("SLOW_MOVING".equals(plan.intent())) {
            appendSuggestionSummary(builder, toolResults);
        } else if ("MENU_SECURITY".equals(plan.intent())) {
            builder.append("菜单和权限工具已读取当前可见菜单与启用角色，可用于检查用户为什么看不到某个页面。\n");
        } else if ("RAG_QA".equals(plan.intent())) {
            appendRagSummary(builder, rag);
        } else if ("RUN_ANALYSIS".equals(plan.intent())) {
            builder.append("本次分析会写入 Agent 自己的预测快照和建议表，不会直接修改入库、出库、库存或看板业务数据。\n");
        } else if ("HEALTH_CHECK".equals(plan.intent())) {
            builder.append("Agent 当前工作在").append(context.callApi() ? "外部模型预留模式" : "本地规则模式")
                    .append("，RAG 提供方为 ").append(context.ragProvider()).append("。\n");
        }
        if (!rag.snippets().isEmpty() && !"RAG_QA".equals(plan.intent())) {
            builder.append("另外，我从本地知识库命中 ").append(rag.snippets().size()).append(" 条相关切片，可作为解释依据。\n");
        }
        if (memory.profile() != null) {
            builder.append("记忆提示：").append(memory.profile().summary()).append("\n");
        }
        if (!context.callApi()) {
            builder.append("外部模型调用当前关闭，本次没有依赖第三方 AI API。");
        }
        return builder.toString().trim();
    }

    private void appendSuggestionSummary(StringBuilder builder, List<AgentToolResult> toolResults) {
        List<AgentSuggestionDto> suggestions = collectSuggestions(toolResults);
        if (suggestions.isEmpty()) {
            builder.append("当前没有明显的补货、低库存或呆滞库存建议。\n");
            return;
        }
        builder.append("优先关注：");
        suggestions.stream().limit(3).forEach(item -> builder
                .append(item.partCode() == null ? "未指定零件" : item.partCode())
                .append("（")
                .append(item.title())
                .append("）；"));
        builder.append("建议先到库存看板核对阈值，再创建对应入库计划。\n");
    }

    private void appendForecastSummary(StringBuilder builder, List<AgentToolResult> toolResults, int days) {
        List<ForecastRow> rows = collectForecastRows(toolResults);
        long riskCount = rows.stream().filter(row -> !"NORMAL".equals(row.riskLevel())).count();
        builder.append("按未来 ").append(days).append(" 天估算，参与预测零件 ")
                .append(rows.size()).append(" 个，其中存在风险 ")
                .append(riskCount).append(" 个。\n");
        rows.stream()
                .filter(row -> !"NORMAL".equals(row.riskLevel()))
                .limit(3)
                .forEach(row -> builder.append(row.partCode())
                        .append(" 预测库存 ")
                        .append(row.forecastQty())
                        .append("，状态 ")
                        .append(row.riskLabel())
                        .append("。\n"));
    }

    private void appendRagSummary(StringBuilder builder, RagContext rag) {
        if (rag.snippets().isEmpty()) {
            builder.append("本地知识库没有命中足够相关的内容，可以先在 RAG 知识库预留区补充规则文档。\n");
            return;
        }
        builder.append("知识库依据：");
        rag.snippets().stream().limit(3)
                .forEach(snippet -> builder.append(snippet.title()).append("（得分 ")
                        .append(String.format(Locale.ROOT, "%.2f", snippet.score())).append("）；"));
        builder.append("\n");
    }

    private List<AgentSuggestionDto> collectSuggestions(List<AgentToolResult> toolResults) {
        List<AgentSuggestionDto> result = new ArrayList<>();
        for (AgentToolResult toolResult : toolResults) {
            if (toolResult.data() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof AgentSuggestionDto suggestion) {
                        result.add(suggestion);
                    }
                }
            }
        }
        return result.stream().limit(Math.max(properties.getSuggestionLimit(), 1)).toList();
    }

    private List<ForecastRow> collectForecastRows(List<AgentToolResult> toolResults) {
        List<ForecastRow> result = new ArrayList<>();
        for (AgentToolResult toolResult : toolResults) {
            if (toolResult.data() instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof ForecastRow row) {
                        result.add(row);
                    }
                }
            }
        }
        return result;
    }
}
