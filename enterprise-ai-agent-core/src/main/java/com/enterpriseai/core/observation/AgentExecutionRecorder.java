package com.enterpriseai.core.observation;

import com.enterpriseai.core.context.AgentExecutionContext;

/**
 * Agent 执行审计接口。
 *
 * 实现方（Phase 5/9，infrastructure 模块）负责：
 * - 将 AgentExecution / ActionExecution / Token 用量 / 延迟 / 错误落库（MySQL）；
 * - 同步上报 Micrometer 指标（agent.execution.duration、agent.action.duration 等）。
 */
public interface AgentExecutionRecorder {

    void onExecutionStart(AgentExecutionContext context);

    void onActionComplete(String executionId, String actionName, String outcomeType,
                          long latencyMs, String error);

    void onExecutionEnd(AgentExecutionContext context);
}
