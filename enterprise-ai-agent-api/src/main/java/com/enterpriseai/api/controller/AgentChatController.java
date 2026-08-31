package com.enterpriseai.api.controller;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import com.enterpriseai.api.dto.ChatRequest;
import com.enterpriseai.api.dto.ChatResponse;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import com.enterpriseai.core.memory.ConversationStore;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import com.enterpriseai.domain.model.AgentResponse;
import com.enterpriseai.domain.model.UserRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Agent 对话入口：POST /api/agent/chat
 *
 * 请求 → 鉴权过滤器（TenantContext 已就绪）→ 本控制器：
 * 1. 开启 AgentExecutionContext（executionId = 审计/日志/指标/Trace 关联键）；
 * 2. 以阻塞方式调用 Embabel AgentPlatform（SIMPLE 模式，Action 在本线程执行，
 *    TenantContext 全程有效；见 TenantContextAgentProcessCallback 的并发防御）；
 * 3. 按成功/失败收口上下文并落审计，会话记忆按 tenantId+sessionId 隔离写入；
 * 4. 异常统一交给 GlobalExceptionHandler，映射为用户安全（无内部细节）的 ErrorResponse。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    /** 供 GlobalExceptionHandler 回填 executionId 的请求属性名。 */
    public static final String ATTR_EXECUTION_ID = "agent.executionId";

    private static final Logger log = LoggerFactory.getLogger(AgentChatController.class);

    private final AgentPlatform agentPlatform;
    private final AgentExecutionRecorder recorder;
    private final ConversationStore conversationStore;

    public AgentChatController(AgentPlatform agentPlatform,
                               AgentExecutionRecorder recorder,
                               ConversationStore conversationStore) {
        this.agentPlatform = agentPlatform;
        this.recorder = recorder;
        this.conversationStore = conversationStore;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest chatRequest,
                             HttpServletRequest servletRequest) {
        String sessionId = chatRequest.sessionId() == null || chatRequest.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : chatRequest.sessionId();

        AgentExecutionContext context = AgentExecutionContext.begin(
                TenantContext.require(), new UserRequest(chatRequest.message(), sessionId));
        servletRequest.setAttribute(ATTR_EXECUTION_ID, context.executionId());
        recorder.onExecutionStart(context);

        try {
            AgentResponse response = AgentInvocation
                    .create(agentPlatform, AgentResponse.class)
                    .invoke(new UserInput(chatRequest.message()));

            if (response == null) {
                // 规划器未能走到任何 @AchievesGoal 路径（理论上双路径兜底不会发生）
                throw new AgentException(AgentErrorCode.PLANNING_ERROR,
                        "未能规划出达成目标的执行路径");
            }

            context.setCurrentIntent(response.intent());
            context.markSucceeded();

            conversationStore.appendMessage(context.user().tenantId(), sessionId,
                    "user", chatRequest.message());
            conversationStore.appendMessage(context.user().tenantId(), sessionId,
                    "assistant", response.answer());

            log.info("Agent 执行成功 executionId={} sessionId={} intent={}",
                    context.executionId(), sessionId, response.intent());
            return new ChatResponse(context.executionId(), response.answer(),
                    response.intent() == null ? null : response.intent().name(), "SUCCEEDED");
        } catch (AgentException e) {
            context.markFailed(e.getMessage());
            throw e;
        } catch (Exception e) {
            context.markFailed(e.getMessage());
            log.error("Agent 执行异常 executionId={}", context.executionId(), e);
            throw new AgentException(AgentErrorCode.BUSINESS_ERROR,
                    "Agent 执行过程中发生未预期错误", e);
        } finally {
            recorder.onExecutionEnd(context);
            AgentExecutionContext.clear();
        }
    }
}
