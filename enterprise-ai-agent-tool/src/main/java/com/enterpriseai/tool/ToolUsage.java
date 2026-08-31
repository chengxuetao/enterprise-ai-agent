package com.enterpriseai.tool;

import com.enterpriseai.core.context.AgentExecutionContext;

/** 工具使用记录辅助：把工具调用结果挂到当前执行上下文（无上下文则忽略）。 */
public final class ToolUsage {

    private ToolUsage() {
    }

    public static void record(String toolName, Object result) {
        AgentExecutionContext ctx = AgentExecutionContext.current();
        if (ctx != null) {
            ctx.recordToolResult(toolName, result);
        }
    }
}
