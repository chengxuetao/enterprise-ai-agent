package com.enterpriseai.application;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.internal.LlmOperations;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.integration.IntegrationTestUtils;
import com.embabel.chat.Message;
import com.enterpriseai.agent.assistant.EnterpriseAssistantAgent;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.memory.ConversationStore;
import com.enterpriseai.core.prompt.PromptRegistry;
import com.enterpriseai.core.prompt.PromptVersionProperties;
import com.enterpriseai.domain.model.AgentResponse;
import com.enterpriseai.domain.model.AnalysisResult;
import com.enterpriseai.domain.model.ConversationHistory;
import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;
import com.enterpriseai.domain.model.IntentResult;
import com.enterpriseai.domain.model.IntentType;
import com.enterpriseai.domain.model.UserRequest;
import com.enterpriseai.domain.service.AlarmService;
import com.enterpriseai.domain.service.EnterpriseService;
import com.enterpriseai.domain.service.KnowledgeSearchService;
import com.enterpriseai.domain.service.StatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent 端到端集成测试（离线，无真实 LLM、无数据库）。
 *
 * 平台与 LLM 桩均来自 Embabel 官方测试设施（IntegrationTestUtils / LlmOperations 打桩，
 * 参照 embabel-agent-test 的官方示例 EmbabelMockitoIntegrationTest）：
 * GOAP 规划、@Action(pre=...) 条件门控、Action 链、强类型黑板传递全部真实执行，
 * 仅 LLM 结构化输出被按【输出类型】精确打桩。
 *
 * 本测试同时关闭 Phase 4 遗留的运行时验证项：
 * @Condition 方法名即 pre 条件名 的语义在真实 GOAP 规划器下成立。
 */
class EnterpriseAssistantAgentIntegrationTest {

    private final EnterpriseService enterpriseService = mock(EnterpriseService.class);
    private final AlarmService alarmService = mock(AlarmService.class);
    private final StatisticsService statisticsService = mock(StatisticsService.class);
    private final KnowledgeSearchService knowledgeSearchService = mock(KnowledgeSearchService.class);
    private final ConversationStore conversationStore = mock(ConversationStore.class);
    private final LlmOperations llmOperations = mock(LlmOperations.class);

    private AgentPlatform platform;

    @BeforeEach
    void deployAgent() {
        platform = IntegrationTestUtils.dummyAgentPlatform(llmOperations);
        EnterpriseAssistantAgent agent = new EnterpriseAssistantAgent(
                enterpriseService, alarmService, statisticsService, knowledgeSearchService,
                conversationStore, new PromptRegistry(new PromptVersionProperties(Map.of())));

        AgentMetadataReader reader = new AgentMetadataReader();
        AgentScope scope = reader.createAgentMetadata(agent);
        assertNotNull(scope, "@Agent 注解应被识别并产出 AgentScope");
        platform.deploy((Agent) scope);
    }

    @AfterEach
    void cleanThreadLocals() {
        TenantContext.clear();
        AgentExecutionContext.clear();
    }

    @Test
    void enterpriseQueryPathRunsIntentExtractQueryAnalyzeCompose() {
        when(llmOperations.createObject(any(), any(), eq(IntentResult.class), any(), any()))
                .thenReturn(new IntentResult(IntentType.ENTERPRISE_QUERY, 0.95, "用户想查企业"));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseQuery.class), any(), any()))
                .thenReturn(new EnterpriseQuery("科技", null, null, null, 10));
        when(llmOperations.createObject(any(), any(), eq(AnalysisResult.class), any(), any()))
                .thenReturn(new AnalysisResult("共 1 家企业", List.of("均为软件业"), List.of(), List.of()));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseAssistantAgent.FinalAnswer.class), any(), any()))
                .thenReturn(new EnterpriseAssistantAgent.FinalAnswer("已为您找到 1 家相关企业。"));
        when(enterpriseService.queryEnterprises(any(EnterpriseQuery.class)))
                .thenReturn(new EnterpriseResult(List.of(new EnterpriseResult.EnterpriseSummary(
                        1L, "甲科技有限公司", "北京", "软件", BigDecimal.valueOf(500), "张三", "正常")), 1, false));

        AgentResponse response = AgentInvocation.create(platform, AgentResponse.class)
                .invoke(new UserInput("帮我查一下北京的科技公司"));

        assertNotNull(response);
        assertEquals("已为您找到 1 家相关企业。", response.answer());
        assertEquals(IntentType.ENTERPRISE_QUERY, response.intent());

        // 意图条件门控：只有企业查询分支被执行，其余三类查询服务零调用
        verify(enterpriseService).queryEnterprises(any(EnterpriseQuery.class));
        verifyNoInteractions(alarmService, statisticsService, knowledgeSearchService);
    }

    @Test
    void followUpQuestionInjectsConversationHistoryIntoIntentPrompt() {
        // 上一轮对话在会话记忆中；本轮追问"那深圳呢"依赖历史才可理解
        when(conversationStore.recentMessages(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new ConversationHistory.ConversationMessage("user", "帮我查北京的企业"),
                        new ConversationHistory.ConversationMessage("assistant", "已为您找到北京的 1 家企业。")));
        TenantContext.set(new UserContext("u1", "tenant-a", "u1", Set.of(), Set.of()));
        AgentExecutionContext.begin(TenantContext.require(),
                new UserRequest("那深圳呢", "session-42"));

        when(llmOperations.createObject(any(), any(), eq(IntentResult.class), any(), any()))
                .thenReturn(new IntentResult(IntentType.ENTERPRISE_QUERY, 0.9, "追问继承企业查询"));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseQuery.class), any(), any()))
                .thenReturn(new EnterpriseQuery(null, "深圳", null, null, null));
        when(llmOperations.createObject(any(), any(), eq(AnalysisResult.class), any(), any()))
                .thenReturn(new AnalysisResult("共 1 家企业", List.of(), List.of(), List.of()));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseAssistantAgent.FinalAnswer.class), any(), any()))
                .thenReturn(new EnterpriseAssistantAgent.FinalAnswer("已为您找到深圳的 1 家企业。"));
        when(enterpriseService.queryEnterprises(any(EnterpriseQuery.class)))
                .thenReturn(new EnterpriseResult(List.of(new EnterpriseResult.EnterpriseSummary(
                        2L, "乙科技有限公司", "深圳", "软件", BigDecimal.valueOf(800), "李四", "正常")), 1, false));

        AgentResponse response = AgentInvocation.create(platform, AgentResponse.class)
                .invoke(new UserInput("那深圳呢"));

        assertNotNull(response);
        assertEquals(IntentType.ENTERPRISE_QUERY, response.intent());

        // 会话记忆按上下文读取（tenant + session），历史注入意图识别 Prompt
        verify(conversationStore).recentMessages("tenant-a", "session-42", 10);
        ArgumentCaptor<List<com.embabel.chat.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmOperations).createObject(messagesCaptor.capture(), any(), eq(IntentResult.class), any(), any());
        // Message.toString() 会省略长内容中段，须取完整 content 拼接后断言
        String intentPrompt = messagesCaptor.getValue().stream()
                .map(Message::getContent)
                .collect(Collectors.joining("\n"));
        assertTrue(intentPrompt.contains("帮我查北京的企业"),
                "意图识别 Prompt 应包含历史对话");
        assertTrue(intentPrompt.contains("那深圳呢"),
                "意图识别 Prompt 应包含当前追问");
        verify(enterpriseService).queryEnterprises(any(EnterpriseQuery.class));
    }

    @Test
    void unknownIntentFallsBackToClarificationWithoutTouchingData() {
        when(llmOperations.createObject(any(), any(), eq(IntentResult.class), any(), any()))
                .thenReturn(new IntentResult(IntentType.UNKNOWN, 0.9, "与业务域无关"));
        when(llmOperations.createObject(any(), any(), eq(EnterpriseAssistantAgent.FinalAnswer.class), any(), any()))
                .thenReturn(new EnterpriseAssistantAgent.FinalAnswer(
                        "抱歉，我目前支持企业查询、告警查询、数据统计与知识库问答。"));

        AgentResponse response = AgentInvocation.create(platform, AgentResponse.class)
                .invoke(new UserInput("今天天气怎么样"));

        assertNotNull(response);
        assertEquals(IntentType.UNKNOWN, response.intent());

        // 兜底路径不触碰任何数据服务（未知意图不产生查询副作用）
        verifyNoInteractions(enterpriseService, alarmService, statisticsService, knowledgeSearchService);
    }
}
