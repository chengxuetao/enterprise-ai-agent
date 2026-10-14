package com.enterpriseai.agent.assistant;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Condition;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.domain.io.UserInput;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.memory.ConversationStore;
import com.enterpriseai.core.prompt.PromptRegistry;
import com.enterpriseai.domain.model.AgentResponse;
import com.enterpriseai.domain.model.AlarmQuery;
import com.enterpriseai.domain.model.AlarmResult;
import com.enterpriseai.domain.model.AnalysisData;
import com.enterpriseai.domain.model.AnalysisResult;
import com.enterpriseai.domain.model.ConversationHistory;
import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;
import com.enterpriseai.domain.model.IntentResult;
import com.enterpriseai.domain.model.IntentType;
import com.enterpriseai.domain.model.KnowledgeQuery;
import com.enterpriseai.domain.model.KnowledgeResult;
import com.enterpriseai.domain.model.StatisticsQuery;
import com.enterpriseai.domain.model.StatisticsResult;
import com.enterpriseai.domain.service.AlarmService;
import com.enterpriseai.domain.service.EnterpriseService;
import com.enterpriseai.domain.service.KnowledgeSearchService;
import com.enterpriseai.domain.service.StatisticsService;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 企业智能助手 Agent。
 *
 * 规划：GOAP（Embabel 默认 Planner）。目标 = {@code GOAL}（产出 AgentResponse 即达成）。
 * 路由：5 个 {@code @Condition} 依据 IntentResult 把流程锁定到唯一分支，
 *       分支内的 Action 由类型数据流自然串联（GOAP 按黑板上的类型选择可用 Action）。
 * 多轮：loadConversationHistory（纯 Java）先于意图识别执行，历史注入意图/参数提取
 *       Prompt——追问（如"那深圳呢"）依赖上文才可理解；上下文缺失时降级为空历史。
 * 职责：每个 @Action 只做一件事；查询类 Action 为纯 Java（无 LLM、无数据库直连），
 *       LLM 只出现在意图识别、参数提取、数据分析、最终应答四个认知节点。
 *
 * 线程安全：无实例可变状态，Spring 单例。
 */
@Agent(description = "Enterprise assistant that answers questions about enterprises, alarms, statistics and internal knowledge")
public class EnterpriseAssistantAgent {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseAssistantAgent.class);

    /** 目标描述：两个收尾 Action 共享同一目标，Planner 依据条件选择路径。 */
    private static final String GOAL =
            "Answer the user's enterprise-related request accurately and safely";

    /** 分析 Prompt 中最多携带的数据行数，保护上下文窗口与 token 成本。 */
    private static final int MAX_ANALYSIS_ROWS = 20;

    /** 注入 Prompt 的最近历史消息条数（约 5 轮对话，存储侧已有单条截断）。 */
    private static final int HISTORY_MESSAGES = 10;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EnterpriseService enterpriseService;
    private final AlarmService alarmService;
    private final StatisticsService statisticsService;
    private final KnowledgeSearchService knowledgeSearchService;
    private final ConversationStore conversationStore;
    private final PromptRegistry prompts;

    public EnterpriseAssistantAgent(
            EnterpriseService enterpriseService,
            AlarmService alarmService,
            StatisticsService statisticsService,
            KnowledgeSearchService knowledgeSearchService,
            ConversationStore conversationStore,
            PromptRegistry prompts) {
        this.enterpriseService = enterpriseService;
        this.alarmService = alarmService;
        this.statisticsService = statisticsService;
        this.knowledgeSearchService = knowledgeSearchService;
        this.conversationStore = conversationStore;
        this.prompts = prompts;
    }

    // ==================================================================
    // 0. 历史加载（纯 Java）：多轮追问的上下文来源。
    //    意图识别与参数提取按类型依赖本产物，GOAP 自然保证先于二者执行。
    // ==================================================================

    @Action(description = "Load recent conversation history for multi-turn context", readOnly = true)
    public ConversationHistory loadConversationHistory(UserInput userInput) {
        // 历史加载是增强路径而非安全边界：租户防线在数据服务层（require()），
        // 此处上下文缺失（如测试直调 Agent）或会话记忆暂不可用时降级为空历史，
        // 单轮对话不受影响。
        UserContext user = TenantContext.get();
        AgentExecutionContext execution = AgentExecutionContext.current();
        if (user == null || execution == null || execution.sessionId() == null
                || execution.sessionId().isBlank()) {
            return ConversationHistory.empty();
        }
        try {
            List<ConversationHistory.ConversationMessage> recent = conversationStore.recentMessages(
                    user.tenantId(), execution.sessionId(), HISTORY_MESSAGES);
            return new ConversationHistory(recent);
        } catch (Exception e) {
            log.warn("会话历史加载失败，按首轮对话处理 sessionId={}", execution.sessionId(), e);
            return ConversationHistory.empty();
        }
    }

    // ==================================================================
    // 1. 意图识别（LLM，单一认知任务：分类；携带历史以理解追问）
    // ==================================================================

    @Action(description = "Recognize the user intent from the request", readOnly = true,
            // GOAP 乐观后置：本 Action 产出 IntentResult 后，五个意图条件之一可能成立；
            // 规划器据此建立分支可达性，运行时按黑板实况（@Condition 方法求值）重规划收敛到真实分支
            post = {"intentIsEnterpriseQuery", "intentIsAlarmQuery",
                    "intentIsStatisticsQuery", "intentIsKnowledgeQa", "intentIsUnknown"})
    public IntentResult recognizeIntent(UserInput userInput, ConversationHistory history, Ai ai) {
        String prompt = prompts.render("intent", "intent-recognition", 2,
                Map.of("question", userInput.getContent(),
                        "history", history.transcript()));
        return ai.withDefaultLlm().createObject(prompt, IntentResult.class);
    }

    // ==================================================================
    // 意图路由条件（纯 Java）。GOAP 依据这些命名条件选择唯一分支。
    // ==================================================================

    @Condition
    public boolean intentIsEnterpriseQuery(IntentResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.ENTERPRISE_QUERY;
    }

    @Condition
    public boolean intentIsAlarmQuery(IntentResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.ALARM_QUERY;
    }

    @Condition
    public boolean intentIsStatisticsQuery(IntentResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.STATISTICS_QUERY;
    }

    @Condition
    public boolean intentIsKnowledgeQa(IntentResult intentResult) {
        return intentResult != null && intentResult.intent() == IntentType.KNOWLEDGE_QA;
    }

    @Condition
    public boolean intentIsUnknown(IntentResult intentResult) {
        return intentResult == null || intentResult.intent() == IntentType.UNKNOWN;
    }

    // ==================================================================
    // 2. 参数提取（LLM，按意图分别提取；未提供的字段必须为 null）
    // ==================================================================

    @Action(description = "Extract enterprise query conditions", pre = {"intentIsEnterpriseQuery"}, readOnly = true)
    public EnterpriseQuery extractEnterpriseQuery(UserInput userInput, ConversationHistory history, Ai ai) {
        String prompt = prompts.render("parameter", "enterprise-query", 2, Map.of(
                "question", userInput.getContent(),
                "history", history.transcript()));
        return ai.withDefaultLlm().createObject(prompt, EnterpriseQuery.class);
    }

    @Action(description = "Extract alarm query conditions", pre = {"intentIsAlarmQuery"}, readOnly = true)
    public AlarmQuery extractAlarmQuery(UserInput userInput, ConversationHistory history, Ai ai) {
        String prompt = prompts.render("parameter", "alarm-query", 2, Map.of(
                "question", userInput.getContent(),
                "history", history.transcript(),
                "current_time", LocalDateTime.now().format(TIME_FORMATTER)));
        return ai.withDefaultLlm().createObject(prompt, AlarmQuery.class);
    }

    @Action(description = "Extract statistics query conditions", pre = {"intentIsStatisticsQuery"}, readOnly = true)
    public StatisticsQuery extractStatisticsQuery(UserInput userInput, ConversationHistory history, Ai ai) {
        String prompt = prompts.render("parameter", "statistics-query", 2, Map.of(
                "question", userInput.getContent(),
                "history", history.transcript(),
                "current_time", LocalDateTime.now().format(TIME_FORMATTER)));
        return ai.withDefaultLlm().createObject(prompt, StatisticsQuery.class);
    }

    @Action(description = "Extract knowledge search question", pre = {"intentIsKnowledgeQa"}, readOnly = true)
    public KnowledgeQuery extractKnowledgeQuery(UserInput userInput, ConversationHistory history, Ai ai) {
        String prompt = prompts.render("parameter", "knowledge-query", 2, Map.of(
                "question", userInput.getContent(),
                "history", history.transcript()));
        return ai.withDefaultLlm().createObject(prompt, KnowledgeQuery.class);
    }

    // ==================================================================
    // 3. 业务查询（纯 Java：Service -> Repository -> DB，LLM 不参与）
    // ==================================================================

    @Action(description = "Query enterprises via Java service", readOnly = true)
    public EnterpriseResult queryEnterprises(EnterpriseQuery query) {
        return enterpriseService.queryEnterprises(query);
    }

    @Action(description = "Query alarms via Java service", readOnly = true)
    public AlarmResult queryAlarms(AlarmQuery query) {
        return alarmService.queryAlarms(query);
    }

    @Action(description = "Compute statistics via Java service", readOnly = true)
    public StatisticsResult computeStatistics(StatisticsQuery query) {
        return statisticsService.computeStatistics(query);
    }

    @Action(description = "Search knowledge base via RAG service", readOnly = true)
    public KnowledgeResult searchKnowledge(KnowledgeQuery query) {
        return knowledgeSearchService.search(query);
    }

    // ==================================================================
    // 4. 结果归一化（纯 Java）：四条分支统一为 AnalysisData，
    //    截断行数并把 total/truncated 如实传递给分析环节。
    // ==================================================================

    @Action(description = "Normalize enterprise result for analysis", readOnly = true)
    public AnalysisData prepareEnterpriseData(EnterpriseResult result) {
        List<AnalysisData.DataRow> rows = result.enterprises().stream()
                .limit(MAX_ANALYSIS_ROWS)
                .map(e -> new AnalysisData.DataRow(
                        nvl(e.name(), "未命名企业"),
                        "城市=%s, 行业=%s, 注册资本=%s万元, 法人=%s, 状态=%s".formatted(
                                nvl(e.city(), "未知"),
                                nvl(e.industry(), "未知"),
                                e.registeredCapitalWanYuan() == null
                                        ? "未知" : e.registeredCapitalWanYuan().toPlainString(),
                                nvl(e.legalPerson(), "未知"),
                                nvl(e.status(), "未知"))))
                .toList();
        return new AnalysisData(
                IntentType.ENTERPRISE_QUERY,
                result.total() > 0,
                result.total(),
                result.truncated() || result.enterprises().size() > MAX_ANALYSIS_ROWS,
                "企业信息查询结果（共 %d 家）".formatted(result.total()),
                rows);
    }

    @Action(description = "Normalize alarm result for analysis", readOnly = true)
    public AnalysisData prepareAlarmData(AlarmResult result) {
        List<AnalysisData.DataRow> rows = result.alarms().stream()
                .limit(MAX_ANALYSIS_ROWS)
                .map(a -> new AnalysisData.DataRow(
                        nvl(a.enterpriseName(), "未知企业") + " / " + a.occurredAt(),
                        "类型=%s, 等级=%s, 状态=%s, 内容=%s".formatted(
                                nvl(a.alarmType(), "未知"),
                                a.alarmLevel() == null ? "未知" : a.alarmLevel().name(),
                                nvl(a.status(), "未知"),
                                nvl(a.message(), ""))))
                .toList();
        return new AnalysisData(
                IntentType.ALARM_QUERY,
                result.total() > 0,
                result.total(),
                result.truncated() || result.alarms().size() > MAX_ANALYSIS_ROWS,
                "告警查询结果（共 %d 条）".formatted(result.total()),
                rows);
    }

    @Action(description = "Normalize statistics result for analysis", readOnly = true)
    public AnalysisData prepareStatisticsData(StatisticsResult result) {
        List<AnalysisData.DataRow> rows = result.rows().stream()
                .limit(MAX_ANALYSIS_ROWS)
                .map(r -> new AnalysisData.DataRow(
                        nvl(r.dimension(), "未知"),
                        "%s=%d".formatted(result.metric().name(), r.value())))
                .toList();
        return new AnalysisData(
                IntentType.STATISTICS_QUERY,
                result.totalRows() > 0,
                result.totalRows(),
                result.rows().size() > MAX_ANALYSIS_ROWS,
                "统计结果（%s 按 %s 分组，共 %d 组）".formatted(
                        result.metric().name(),
                        result.groupBy() == null ? "总量" : result.groupBy().name(),
                        result.totalRows()),
                rows);
    }

    @Action(description = "Normalize knowledge result for analysis", readOnly = true)
    public AnalysisData prepareKnowledgeData(KnowledgeResult result) {
        List<AnalysisData.DataRow> rows = result.snippets().stream()
                .limit(MAX_ANALYSIS_ROWS)
                .map(s -> new AnalysisData.DataRow(
                        nvl(s.title(), "无标题知识片段"),
                        truncate(nvl(s.content(), ""), 500) + "（来源：" + nvl(s.source(), "知识库") + "）"))
                .toList();
        return new AnalysisData(
                IntentType.KNOWLEDGE_QA,
                !rows.isEmpty(),
                rows.size(),
                false,
                "知识库检索结果（问题：%s，命中 %d 段）".formatted(result.question(), rows.size()),
                rows);
    }

    // ==================================================================
    // 5. 数据分析（LLM：只允许分析给定数据，禁止编造）
    // ==================================================================

    @Action(description = "Analyze the queried business data strictly from provided rows", readOnly = true)
    public AnalysisResult analyzeData(AnalysisData data, Ai ai) {
        String prompt = prompts.render("analysis", "data-analysis", 1, Map.of(
                "source", data.sourceDescription(),
                "data_present", String.valueOf(data.dataPresent()),
                "total", String.valueOf(data.totalRecords()),
                "rows", formatRows(data)));
        return ai.withDefaultLlm().createObject(prompt, AnalysisResult.class);
    }

    // ==================================================================
    // 6. 最终应答（LLM）与未知意图兜底 —— 二者共享同一 Goal
    // ==================================================================

    @AchievesGoal(description = GOAL)
    @Action(description = "Compose the final user-facing answer", readOnly = true)
    public AgentResponse composeResponse(
            AnalysisResult analysis, IntentResult intentResult, UserInput userInput, Ai ai) {
        String analysisText = """
                摘要：%s
                主要发现：%s
                风险：%s
                建议：%s""".formatted(
                analysis.summary(),
                String.join("；", analysis.findings()),
                String.join("；", analysis.risks()),
                String.join("；", analysis.recommendations()));
        String prompt = prompts.render("response", "final-response", 1, Map.of(
                "question", userInput.getContent(),
                "intent", intentResult.intent().name(),
                "analysis", analysisText));
        FinalAnswer answer = ai.withDefaultLlm().createObject(prompt, FinalAnswer.class);
        return new AgentResponse(answer.answer(), intentResult.intent());
    }

    @AchievesGoal(description = GOAL)
    @Action(description = "Politely clarify when the intent is unknown", pre = {"intentIsUnknown"}, readOnly = true)
    public AgentResponse unknownIntentResponse(UserInput userInput, IntentResult intentResult, Ai ai) {
        String prompt = prompts.render("response", "unknown-response", 1, Map.of(
                "question", userInput.getContent(),
                "reason", intentResult == null ? "" : nvl(intentResult.reason(), "")));
        FinalAnswer answer = ai.withDefaultLlm().createObject(prompt, FinalAnswer.class);
        return new AgentResponse(answer.answer(), IntentType.UNKNOWN);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** LLM 仅产出 answer 文本；intent 由 Java 侧确定性回填，避免模型复读出错。 */
    @JsonClassDescription("Final user-facing answer")
    public record FinalAnswer(
            @JsonPropertyDescription("User-facing answer in Markdown, Chinese")
            String answer) {
    }

    private static String formatRows(AnalysisData data) {
        if (data.rows().isEmpty()) {
            return "（无数据行）";
        }
        StringBuilder sb = new StringBuilder();
        for (AnalysisData.DataRow row : data.rows()) {
            sb.append("- ").append(row.label()).append("：").append(row.details()).append('\n');
        }
        return sb.toString();
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }
}
