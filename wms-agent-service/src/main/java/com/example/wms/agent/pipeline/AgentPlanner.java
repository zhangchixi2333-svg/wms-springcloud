/**
 * 本文件实现 Agent Planner/Router，按精准命令、规则、本地语义和 LLM 工具调用四层选择处理路径。
 */
package com.example.wms.agent.pipeline;

import com.example.wms.agent.model.AgentPlan;
import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.AgentRouteLevel;
import com.example.wms.agent.model.AgentToolCall;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AgentPlanner {

    public AgentPlan plan(AgentRequestContext context) {
        String question = context.question().trim();
        String normalized = question.toLowerCase(Locale.ROOT);
        if (matchesExact(question, "刷新库存预测", "执行分析", "重新分析", "生成建议")) {
            return new AgentPlan(
                    "RUN_ANALYSIS",
                    AgentRouteLevel.L1_COMMAND,
                    AgentRouteLevel.L1_COMMAND.getLabel(),
                    0.98,
                    "命中精准命令，需要立即执行一次库存分析。",
                    false,
                    List.of(new AgentToolCall("inventory.analyze", "执行库存分析", Map.of("days", context.forecastDays())))
            );
        }
        if (matchesExact(question, "健康检查", "服务状态", "检查agent")) {
            return new AgentPlan(
                    "HEALTH_CHECK",
                    AgentRouteLevel.L1_COMMAND,
                    AgentRouteLevel.L1_COMMAND.getLabel(),
                    0.98,
                    "命中精准命令，只读取 Agent 健康状态。",
                    false,
                    List.of(new AgentToolCall("system.health", "Agent 健康检查", Map.of()))
            );
        }

        List<AgentToolCall> calls = new ArrayList<>();
        if (containsAny(normalized, "缺货", "低库存", "补货", "不足", "预警")) {
            calls.add(new AgentToolCall("inventory.suggestions", "库存建议", Map.of()));
            calls.add(new AgentToolCall("inventory.forecast", "库存预测", Map.of("days", context.forecastDays())));
            return new AgentPlan("REPLENISHMENT", AgentRouteLevel.L2_RULE, AgentRouteLevel.L2_RULE.getLabel(),
                    0.88, "问题包含补货/低库存关键词，使用规则匹配到库存建议工具。", false, calls);
        }
        if (containsAny(normalized, "预测", "未来", "消耗", "耗尽", "趋势")) {
            calls.add(new AgentToolCall("inventory.forecast", "库存预测", Map.of("days", context.forecastDays())));
            return new AgentPlan("FORECAST", AgentRouteLevel.L2_RULE, AgentRouteLevel.L2_RULE.getLabel(),
                    0.86, "问题包含预测/趋势关键词，使用库存预测工具。", false, calls);
        }
        if (containsAny(normalized, "呆滞", "长期未动", "周转", "占用")) {
            calls.add(new AgentToolCall("inventory.suggestions", "库存建议", Map.of("type", "SLOW_MOVING")));
            return new AgentPlan("SLOW_MOVING", AgentRouteLevel.L2_RULE, AgentRouteLevel.L2_RULE.getLabel(),
                    0.84, "问题包含呆滞/周转关键词，使用库存建议工具筛选风险项。", false, calls);
        }
        if (containsAny(normalized, "菜单", "权限", "角色", "用户")) {
            calls.add(new AgentToolCall("system.menu", "菜单与权限概览", Map.of()));
            return new AgentPlan("MENU_SECURITY", AgentRouteLevel.L2_RULE, AgentRouteLevel.L2_RULE.getLabel(),
                    0.80, "问题涉及菜单或权限，调用系统菜单工具读取当前配置。", false, calls);
        }
        if (containsAny(normalized, "知识", "规则", "文档", "怎么", "如何", "为什么")) {
            calls.add(new AgentToolCall("rag.search", "知识库检索", Map.of("question", context.question(), "topK", 5)));
            return new AgentPlan("RAG_QA", AgentRouteLevel.L3_LOCAL_SEMANTIC, AgentRouteLevel.L3_LOCAL_SEMANTIC.getLabel(),
                    0.72, "问题更像知识问答，优先走本地语义/RAG 检索。", false, calls);
        }
        if (context.callApi()) {
            calls.add(new AgentToolCall("llm.tool-call", "外部模型工具调用", Map.of("question", context.question())));
            return new AgentPlan("LLM_TOOL_CALL", AgentRouteLevel.L4_LLM_TOOL_CALL, AgentRouteLevel.L4_LLM_TOOL_CALL.getLabel(),
                    0.62, "本地规则未命中且允许外部模型，进入 LLM 工具调用预留路径。", true, calls);
        }
        calls.add(new AgentToolCall("inventory.suggestions", "库存建议", Map.of()));
        calls.add(new AgentToolCall("rag.search", "知识库检索", Map.of("question", context.question(), "topK", 3)));
        return new AgentPlan("LOCAL_FALLBACK", AgentRouteLevel.L3_LOCAL_SEMANTIC, AgentRouteLevel.L3_LOCAL_SEMANTIC.getLabel(),
                0.55, "本地规则未强命中，外部模型关闭，使用建议工具和本地知识库兜底。", false, calls);
    }

    private boolean matchesExact(String text, String... commands) {
        for (String command : commands) {
            if (command.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
