package com.enterpriseai.core.context;

import com.enterpriseai.domain.model.IntentType;
import com.enterpriseai.domain.model.UserRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单次 Agent 执行的可追踪上下文。
 * executionId 作为日志、审计、Micrometer 指标的统一关联键（Phase 9 打通 OTel TraceId）。
 */
public class AgentExecutionContext {

    public enum ExecutionStatus { RUNNING, SUCCEEDED, FAILED }

    /**
     * InheritableThreadLocal（与 TenantContext 同一选型与论证，见 README §5.2.1）：
     * Embabel 1.5 的 AgentPlatform.start 经 Asyncer.async 把每个任务提交到新建的
     * embabel-virtual 虚拟线程执行——普通 ThreadLocal 在 Action 线程不可见，
     * 可继承快照使 Action 内可读取 executionId/sessionId（多轮历史加载、审计事件
     * 均依赖此行为）；每任务新建线程、不复用，无线程池脏读风险。
     */
    private static final InheritableThreadLocal<AgentExecutionContext> CURRENT = new InheritableThreadLocal<>();

    private final String executionId;
    private final String sessionId;
    private final UserContext user;
    private final UserRequest request;
    private final Instant startTime = Instant.now();

    private IntentType currentIntent;
    private String currentAction;
    private final Map<String, Object> actionResults = new LinkedHashMap<>();
    private final Map<String, Object> toolResults = new LinkedHashMap<>();
    private ExecutionStatus status = ExecutionStatus.RUNNING;
    private String errorMessage;

    private AgentExecutionContext(String executionId, String sessionId, UserContext user, UserRequest request) {
        this.executionId = executionId;
        this.sessionId = sessionId;
        this.user = user;
        this.request = request;
    }

    /** 在请求线程上开启一次执行，并绑定到 ThreadLocal。 */
    public static AgentExecutionContext begin(UserContext user, UserRequest request) {
        AgentExecutionContext ctx = new AgentExecutionContext(
                UUID.randomUUID().toString(), request.sessionId(), user, request);
        CURRENT.set(ctx);
        return ctx;
    }

    public static AgentExecutionContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public void setCurrentIntent(IntentType intent) {
        this.currentIntent = intent;
    }

    public void setCurrentAction(String action) {
        this.currentAction = action;
    }

    public void recordActionResult(String actionName, Object result) {
        this.actionResults.put(actionName, result);
    }

    public void recordToolResult(String toolName, Object result) {
        this.toolResults.put(toolName, result);
    }

    public void markSucceeded() {
        this.status = ExecutionStatus.SUCCEEDED;
    }

    public void markFailed(String message) {
        this.status = ExecutionStatus.FAILED;
        this.errorMessage = message;
    }

    public String executionId() {
        return executionId;
    }

    public String sessionId() {
        return sessionId;
    }

    public UserContext user() {
        return user;
    }

    public UserRequest request() {
        return request;
    }

    public Instant startTime() {
        return startTime;
    }

    public IntentType currentIntent() {
        return currentIntent;
    }

    public String currentAction() {
        return currentAction;
    }

    public Map<String, Object> actionResults() {
        return actionResults;
    }

    public Map<String, Object> toolResults() {
        return toolResults;
    }

    public ExecutionStatus status() {
        return status;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
