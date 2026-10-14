package com.enterpriseai.application.config;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgenticEventListener;
import com.embabel.agent.core.ActionStatusCode;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 把 Embabel 的 Action 执行结果事件接入审计链路（Phase 13 补线）。
 *
 * 此前 AgentExecutionRecorder.onActionComplete 在生产代码中无调用方——
 * agent_action_execution 明细表与 agent.action Timer 指标实际从未触发。
 * 本监听器经官方 SPI（AgenticEventListener，平台通过
 * eventListener(listeners: List&lt;AgenticEventListener&gt;) 自动收集容器内
 * 全部实现 Bean）把 ActionExecutionResultEvent 翻译为审计回调。
 *
 * executionId 取 AgentExecutionContext（InheritableThreadLocal，Action 线程
 * 自动继承请求线程快照）；无执行上下文（如测试直调 AgentPlatform）时跳过。
 * 审计失败绝不影响主流程（try/catch WARN，与 MySqlAgentExecutionRecorder 契约一致）。
 */
@Component
public class AgentAuditEventListener implements AgenticEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgentAuditEventListener.class);

    private final AgentExecutionRecorder recorder;

    public AgentAuditEventListener(AgentExecutionRecorder recorder) {
        // 注入 @Primary 的 MetricsAgentExecutionRecorder（指标装饰 MySQL 审计）
        this.recorder = recorder;
    }

    @Override
    public void onProcessEvent(AgentProcessEvent event) {
        if (!(event instanceof ActionExecutionResultEvent actionEvent)) {
            return;
        }
        try {
            AgentExecutionContext context = AgentExecutionContext.current();
            if (context == null) {
                log.debug("Action 事件无执行上下文，跳过审计 action={}",
                        actionEvent.getAction().getName());
                return;
            }
            boolean failed = actionEvent.getActionStatus().getStatus() == ActionStatusCode.FAILED;
            // IoBinding 是 Kotlin 值类：Java 侧统一经 toString() 取 "name:Type" 原文（擦除为
            // String 或装箱形态均成立），再剥掉绑定名前缀取产出类型
            String outcomeType = actionEvent.getAction().getOutputs().stream()
                    .map(Object::toString)
                    .map(AgentAuditEventListener::typePart)
                    .collect(Collectors.joining(","));
            recorder.onActionComplete(
                    context.executionId(),
                    actionEvent.getAction().getName(),
                    outcomeType,
                    actionEvent.getRunningTime().toMillis(),
                    failed ? "Action failed (see server logs)" : null);
        } catch (Exception e) {
            log.warn("Action 审计事件处理失败（不影响主流程）: {}", e.getMessage(), e);
        }
    }

    /** "name:Type" → "Type"；无绑定名时原样返回。（包私有供直测） */
    static String typePart(String binding) {
        int separator = binding.indexOf(':');
        return separator >= 0 ? binding.substring(separator + 1) : binding;
    }
}
