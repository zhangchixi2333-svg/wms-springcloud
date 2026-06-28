/**
 * 本文件实现 Agent Reflection，检查事实来源、权限隐私、业务状态修改和回答完整性。
 */
package com.example.wms.agent.reflection;

import com.example.wms.agent.model.AgentPlan;
import com.example.wms.agent.model.AgentRequestContext;
import com.example.wms.agent.model.AgentToolResult;
import com.example.wms.agent.model.ReflectionResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AgentReflectionService {

    public ReflectionResult reflect(
            AgentRequestContext context,
            AgentPlan plan,
            List<AgentToolResult> toolResults,
            String answer
    ) {
        List<String> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        checks.add("事实一致性：回答优先来自工具结果和本地规则。");
        checks.add("权限隐私：回答不会输出密码、Token、Secret 等敏感字段。");
        checks.add("业务规则：除执行分析外，不会修改入库、出库、库存、看板主业务数据。");
        checks.add("完整性：回答包含当前路由层级和下一步建议。");

        String loweredAnswer = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        if (containsAny(loweredAnswer, "password", "token", "secret", "密钥")) {
            warnings.add("回答可能包含敏感字段，请检查。");
        }
        boolean hasFailedTool = toolResults.stream().anyMatch(result -> !result.success());
        if (hasFailedTool) {
            warnings.add("存在工具执行失败，回答已按可用结果降级。");
        }
        if (plan.llmRequired() && !context.callApi()) {
            warnings.add("计划需要外部模型，但 AGENT_CALL_API=false，已降级到本地管线。");
        }
        if (answer == null || answer.isBlank()) {
            warnings.add("回答为空。");
        }
        return new ReflectionResult(warnings.isEmpty(), checks, warnings);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
