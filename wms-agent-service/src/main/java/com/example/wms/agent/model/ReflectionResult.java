/**
 * 本文件定义 Agent Reflection 对事实一致性、权限隐私和业务规则的检查结果。
 */
package com.example.wms.agent.model;

import java.util.List;

public record ReflectionResult(
        boolean passed,
        List<String> checks,
        List<String> warnings
) {
}
