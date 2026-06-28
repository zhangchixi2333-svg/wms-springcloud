/**
 * 本文件实现 Agent Tool Orchestrator，把路由计划转成库存、菜单、RAG 和 LLM 预留工具调用。
 */
package com.example.wms.agent.tool;

import com.example.wms.agent.model.AgentPlan;
import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.AgentToolCall;
import com.example.wms.agent.model.AgentToolResult;
import com.example.wms.agent.model.RagContext;
import com.example.wms.service.AgentAnalysisService;
import com.example.wms.service.AgentAnalysisService.AgentSuggestionDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AgentToolOrchestrator {

    private final AgentAnalysisService analysisService;
    private final JdbcTemplate jdbcTemplate;

    public AgentToolOrchestrator(AgentAnalysisService analysisService, JdbcTemplate jdbcTemplate) {
        this.analysisService = analysisService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AgentToolResult> execute(AgentRequestContext context, AgentPlan plan, RagContext ragContext) {
        return plan.toolCalls().stream()
                .map(call -> executeOne(context, call, ragContext))
                .toList();
    }

    private AgentToolResult executeOne(AgentRequestContext context, AgentToolCall call, RagContext ragContext) {
        long started = System.currentTimeMillis();
        try {
            Object data = switch (call.toolName()) {
                case "inventory.forecast" -> analysisService.forecast(context.forecastDays()).stream().limit(20).toList();
                case "inventory.suggestions" -> filterSuggestions(call, analysisService.suggestions());
                case "inventory.analyze" -> analysisService.analyze(context.forecastDays());
                case "system.health" -> analysisService.health();
                case "system.menu" -> menuOverview();
                case "rag.search" -> ragContext.snippets();
                case "llm.tool-call" -> Map.of(
                        "enabled", context.callApi(),
                        "message", context.callApi()
                                ? "外部模型工具调用接口已预留，当前未配置具体模型提供方。"
                                : "外部模型调用已关闭，本次使用本地管线回答。"
                );
                default -> Map.of("message", "未知工具：" + call.toolName());
            };
            return new AgentToolResult(call.toolName(), call.toolLabel(), true, summarize(call.toolName(), data), data,
                    System.currentTimeMillis() - started);
        } catch (Exception ex) {
            return new AgentToolResult(call.toolName(), call.toolLabel(), false,
                    "工具执行失败：" + safeMessage(ex), null, System.currentTimeMillis() - started);
        }
    }

    private List<AgentSuggestionDto> filterSuggestions(AgentToolCall call, List<AgentSuggestionDto> suggestions) {
        Object type = call.arguments().get("type");
        if (type == null) {
            return suggestions.stream().limit(10).toList();
        }
        return suggestions.stream()
                .filter(item -> type.equals(item.suggestionType()))
                .limit(10)
                .toList();
    }

    private Map<String, Object> menuOverview() {
        Integer menuCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM menu_item WHERE visible = b'1'", Integer.class);
        Integer roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_role WHERE enabled = b'1'", Integer.class);
        List<Map<String, Object>> menus = jdbcTemplate.queryForList("""
                SELECT menu_key AS menuKey, menu_name AS menuName, menu_type AS menuType, page_key AS pageKey
                FROM menu_item
                WHERE visible = b'1'
                ORDER BY sort_order, id
                LIMIT 20
                """);
        return Map.of(
                "menuCount", menuCount == null ? 0 : menuCount,
                "roleCount", roleCount == null ? 0 : roleCount,
                "sampleMenus", menus
        );
    }

    private String summarize(String toolName, Object data) {
        if (data instanceof List<?> list) {
            if ("rag.search".equals(toolName)) {
                return "命中知识切片 " + list.size() + " 条。";
            }
            return "返回记录 " + list.size() + " 条。";
        }
        if (data instanceof Map<?, ?> map) {
            return "返回字段 " + map.size() + " 个。";
        }
        if (data instanceof AgentAnalysisService.AgentRunDto run) {
            return "分析完成：" + run.runNo() + "，生成建议 " + run.suggestionCount() + " 条。";
        }
        return "工具执行完成。";
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 220 ? message.substring(0, 220) : message;
    }
}
