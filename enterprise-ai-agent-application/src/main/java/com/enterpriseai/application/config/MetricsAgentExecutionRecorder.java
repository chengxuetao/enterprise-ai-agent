package com.enterpriseai.application.config;

import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import com.enterpriseai.infra.audit.MySqlAgentExecutionRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer 指标装饰器：包住 MySQL 审计落库，一次执行同时产出
 * 持久化审计（MySqlAgentExecutionRecorder）+ Prometheus 指标（本类）。
 *
 * 指标设计（基数受控）：
 * - agent.execution{status, intent}      Timer   端到端执行耗时；intent 枚举 6 值
 * - agent.action{action, outcome}        Timer   单 Action 耗时；action 为静态方法名集合
 * - agent.action.errors{action}          Counter Action 失败次数
 * executionId / tenantId 一律不做 tag（无界基数）；租户级聚合交给审计表 SQL。
 */
@Component
@Primary
public class MetricsAgentExecutionRecorder implements AgentExecutionRecorder {

    private final MySqlAgentExecutionRecorder delegate;
    private final MeterRegistry registry;

    public MetricsAgentExecutionRecorder(MySqlAgentExecutionRecorder delegate,
                                         MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public void onExecutionStart(AgentExecutionContext context) {
        delegate.onExecutionStart(context);
    }

    @Override
    public void onActionComplete(String executionId, String actionName, String outcomeType,
                                 long latencyMs, String error) {
        delegate.onActionComplete(executionId, actionName, outcomeType, latencyMs, error);
        registry.timer("agent.action",
                        "action", actionName,
                        "outcome", outcomeType == null ? "none" : outcomeType)
                .record(latencyMs, TimeUnit.MILLISECONDS);
        if (error != null) {
            registry.counter("agent.action.errors", "action", actionName).increment();
        }
    }

    @Override
    public void onExecutionEnd(AgentExecutionContext context) {
        delegate.onExecutionEnd(context);
        registry.timer("agent.execution",
                        "status", context.status().name().toLowerCase(),
                        "intent", context.currentIntent() == null
                                ? "none" : context.currentIntent().name().toLowerCase())
                .record(Duration.between(context.startTime(), java.time.Instant.now()));
    }
}
