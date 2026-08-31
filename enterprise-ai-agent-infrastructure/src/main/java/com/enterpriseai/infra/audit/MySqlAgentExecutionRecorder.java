package com.enterpriseai.infra.audit;

import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import com.enterpriseai.infra.persistence.entity.AgentActionExecutionEntity;
import com.enterpriseai.infra.persistence.entity.AgentExecutionEntity;
import com.enterpriseai.infra.persistence.repository.AgentActionExecutionRepository;
import com.enterpriseai.infra.persistence.repository.AgentExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 审计落库实现（MySQL）。
 *
 * 契约：审计失败绝不影响主流程——任何异常只记 WARN 日志。
 * 写入频度低（每次执行 2~N 次插入，且 Action 以秒级 LLM 调用为主），同步写入足够；
 * 若后续审计量级上升，可平滑切换 @Async + 专用线程池（接口不变）。
 */
@Component
public class MySqlAgentExecutionRecorder implements AgentExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(MySqlAgentExecutionRecorder.class);

    private final AgentExecutionRepository executions;
    private final AgentActionExecutionRepository actions;

    public MySqlAgentExecutionRecorder(AgentExecutionRepository executions,
                                       AgentActionExecutionRepository actions) {
        this.executions = executions;
        this.actions = actions;
    }

    @Override
    @Transactional
    public void onExecutionStart(AgentExecutionContext context) {
        try {
            AgentExecutionEntity entity = new AgentExecutionEntity(context.executionId());
            entity.setSessionId(context.sessionId());
            entity.setTenantId(context.user().tenantId());
            entity.setUserId(context.user().userId());
            entity.setUserInput(context.request().message());
            entity.setStatus(AgentExecutionContext.ExecutionStatus.RUNNING.name());
            entity.setStartTime(LocalDateTime.now());
            executions.save(entity);
        } catch (Exception e) {
            log.warn("audit: failed to persist execution start {}", context.executionId(), e);
        }
    }

    @Override
    @Transactional
    public void onActionComplete(String executionId, String actionName, String outcomeType,
                                 long latencyMs, String error) {
        try {
            actions.save(new AgentActionExecutionEntity(
                    executionId, actionName, outcomeType, latencyMs, error));
        } catch (Exception e) {
            log.warn("audit: failed to persist action {} of {}", actionName, executionId, e);
        }
    }

    @Override
    @Transactional
    public void onExecutionEnd(AgentExecutionContext context) {
        try {
            executions.findById(context.executionId()).ifPresent(entity -> {
                entity.setIntent(context.currentIntent() == null ? null : context.currentIntent().name());
                entity.setStatus(context.status().name());
                entity.setErrorMessage(context.errorMessage());
                entity.setEndTime(LocalDateTime.now());
                entity.setDurationMs(Duration.between(context.startTime(), Instant.now()).toMillis());
                executions.save(entity);
            });
        } catch (Exception e) {
            log.warn("audit: failed to persist execution end {}", context.executionId(), e);
        }
    }
}
