package com.enterpriseai.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Agent 单个 Action 的执行审计明细。 */
@Entity
@Table(name = "agent_action_execution", indexes = {
        @Index(name = "idx_agent_action_exec_id", columnList = "execution_id")
})
public class AgentActionExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, length = 36)
    private String executionId;

    @Column(name = "action_name", nullable = false, length = 64)
    private String actionName;

    @Column(name = "outcome_type", length = 128)
    private String outcomeType;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AgentActionExecutionEntity() {
    }

    public AgentActionExecutionEntity(String executionId, String actionName,
                                       String outcomeType, Long latencyMs, String error) {
        this.executionId = executionId;
        this.actionName = actionName;
        this.outcomeType = outcomeType;
        this.latencyMs = latencyMs;
        this.error = error;
    }

    public Long getId() {
        return id;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getActionName() {
        return actionName;
    }

    public String getOutcomeType() {
        return outcomeType;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getError() {
        return error;
    }
}
