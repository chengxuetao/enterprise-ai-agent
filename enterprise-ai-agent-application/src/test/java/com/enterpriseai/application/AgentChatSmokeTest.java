package com.enterpriseai.application;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.test.integration.IntegrationTestUtils;
import com.enterpriseai.agent.assistant.EnterpriseAssistantAgent;
import com.enterpriseai.api.advice.GlobalExceptionHandler;
import com.enterpriseai.api.controller.AgentChatController;
import com.enterpriseai.api.security.AuthenticationFilter;
import com.enterpriseai.core.memory.ConversationStore;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import com.enterpriseai.core.prompt.PromptRegistry;
import com.enterpriseai.core.prompt.PromptVersionProperties;
import com.enterpriseai.domain.model.AnalysisResult;
import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;
import com.enterpriseai.domain.model.IntentResult;
import com.enterpriseai.domain.model.IntentType;
import com.enterpriseai.domain.service.AlarmService;
import com.enterpriseai.domain.service.EnterpriseService;
import com.enterpriseai.domain.service.KnowledgeSearchService;
import com.enterpriseai.domain.service.StatisticsService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 冒烟测试：鉴权过滤器 → Controller → 真实 GOAP Agent（LLM 打桩）→ 响应。
 *
 * 不启动 Spring 容器（MockMvc standalone），横切件逐个显式装配：
 * AuthenticationFilter / GlobalExceptionHandler / AgentExecutionContext /
 * ConversationStore / 审计——验证 Phase 9 的完整请求链路与多租户装配。
 */
class AgentChatSmokeTest {

    private final EnterpriseService enterpriseService = mock(EnterpriseService.class);
    private final AlarmService alarmService = mock(AlarmService.class);
    private final StatisticsService statisticsService = mock(StatisticsService.class);
    private final KnowledgeSearchService knowledgeSearchService = mock(KnowledgeSearchService.class);
    private final LlmOperations llmOperations = mock(LlmOperations.class);
    private final AgentExecutionRecorder recorder = mock(AgentExecutionRecorder.class);
    private final ConversationStore conversationStore = mock(ConversationStore.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        AgentPlatform platform = IntegrationTestUtils.dummyAgentPlatform(llmOperations);
        EnterpriseAssistantAgent agent = new EnterpriseAssistantAgent(
                enterpriseService, alarmService, statisticsService, knowledgeSearchService,
                new PromptRegistry(new PromptVersionProperties(Map.of())));
        AgentMetadataReader reader = new AgentMetadataReader();
        AgentScope scope = reader.createAgentMetadata(agent);
        platform.deploy((Agent) scope);

        AgentChatController controller = new AgentChatController(platform, recorder, conversationStore);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new AuthenticationFilter(new ObjectMapper()))
                .build();

        when(llmOperations.createObject(any(), any(), eq(IntentResult.class), any(), any()))
                .thenReturn(new IntentResult(IntentType.ENTERPRISE_QUERY, 0.95, "用户想查企业"));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseQuery.class), any(), any()))
                .thenReturn(new EnterpriseQuery("科技", "北京", null, null, 10));
        when(llmOperations.createObject(any(), any(), eq(AnalysisResult.class), any(), any()))
                .thenReturn(new AnalysisResult("共 1 家", List.of(), List.of(), List.of()));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseAssistantAgent.FinalAnswer.class), any(), any()))
                .thenReturn(new EnterpriseAssistantAgent.FinalAnswer("已为您找到 1 家北京科技公司。"));
        when(enterpriseService.queryEnterprises(any(EnterpriseQuery.class)))
                .thenReturn(new EnterpriseResult(List.of(new EnterpriseResult.EnterpriseSummary(
                        1L, "甲科技有限公司", "北京", "软件", BigDecimal.valueOf(500), "张三", "正常")), 1, false));
    }

    @Test
    void authenticatedChatReturnsSuccessWithTrackedExecution() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(APPLICATION_JSON)
                        .header("X-User-Id", "u1")
                        .header("X-Tenant-Id", "tenant-a")
                        .header("X-Permissions", "enterprise:query")
                        .content("{\"message\":\"帮我查一下北京的科技公司\",\"sessionId\":\"s1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.intent").value("ENTERPRISE_QUERY"))
                .andExpect(jsonPath("$.answer").value("已为您找到 1 家北京科技公司。"))
                .andExpect(jsonPath("$.executionId").isNotEmpty());

        // 会话记忆按租户+会话隔离写入
        verify(conversationStore).appendMessage(eq("tenant-a"), eq("s1"), eq("user"), any(String.class));
        verify(conversationStore).appendMessage(eq("tenant-a"), eq("s1"), eq("assistant"), any(String.class));
        // 审计钩子在执行生命周期内被调用
        verify(recorder).onExecutionStart(any());
        verify(recorder).onExecutionEnd(any());
    }

    @Test
    void unauthenticatedRequestIsRejectedBeforeReachingController() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"查企业\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void invalidBodyFailsValidationWith400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(APPLICATION_JSON)
                        .header("X-User-Id", "u1")
                        .header("X-Tenant-Id", "tenant-a")
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
