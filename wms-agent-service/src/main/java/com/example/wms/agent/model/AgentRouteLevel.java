/**
 * 本文件定义 Agent 路由层级，用于标识问题由精确命令、规则、语义匹配或外部模型工具调用处理。
 */
package com.example.wms.agent.model;

public enum AgentRouteLevel {
    L1_COMMAND("L1 精准命令"),
    L2_RULE("L2 规则匹配"),
    L3_LOCAL_SEMANTIC("L3 本地语义匹配"),
    L4_LLM_TOOL_CALL("L4 LLM 工具调用");

    private final String label;

    AgentRouteLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
