# Enterprise AI Agent — 实施计划

> 目标：基于 Spring Boot + Spring AI + Embabel 的企业级 AI Agent 系统
> （企业查询 / 告警查询 / 数据统计 / 知识库问答，多租户 + 权限 + 可观测）

## 版本基线（已核实，2026-08-28）

| 组件 | 版本 | 依据 |
|---|---|---|
| Java | 21（LTS） | Embabel 官方 java-agent-template `java.version=21`；Boot 4.1 最低 17 |
| Spring Boot | 4.1.x | Embabel 1.5.0 POM 依赖 `spring-boot-*-processor:4.1.0` |
| Spring AI | 2.0.0（spring-ai-bom） | Embabel 1.5.0 POM 依赖 `spring-ai-autoconfigure-model-chat-client:2.0.0` |
| Embabel | 1.5.0（com.embabel.agent:embabel-agent-starter） | Maven Central 最新稳定版（2026-08-13） |
| Vector Store | Elasticsearch（spring-ai-starter-vector-store-elasticsearch） | Maven Central 存在此 artifact |
| DB / Cache | MySQL(mysql-connector-j) / Redis(spring-boot-starter-data-redis) | — |
| 可观测 | Micrometer + micrometer-tracing-bridge-otel + OTLP exporter | — |

注意：
- Embabel GitHub README / java-agent-template 仍停留在 0.3.x + Boot 3.5，**以 Maven Central POM 为准**。
- Embabel 1.5.0 的 `@Action` 注解签名（已从 sources jar 核实）：description/pre/post/canRerun/readOnly/outputBinding/cost/value/trigger/actionRetryPolicy。
  README 中出现的 `@Action(toolGroups=...)` 在 api 注解签名中未见 → Phase 6 实现前需再核实工具暴露机制（embabel-agent-common / @ToolGroup(role)）。
- API 核实材料：`embabel-agent-api-1.5.0-sources.jar`（已下载解压于 /tmp/embabel-api-src）。

## 架构铁律（贯穿所有 Phase）

1. LLM 永不直接访问数据库 —— 只产生结构化对象与工具调用请求，DB 操作一律走 Java Service。
2. 权限控制只做在 Java 后端 —— 按用户权限过滤可暴露给 LLM 的工具集。
3. 一个 Action 只做一件事 —— 8 个单一职责 Action，由 GOAP Planner 串联。
4. 一个 Prompt 只做一个认知任务 —— intent / parameter / analysis / response 分离，独立 .st 文件 + 版本号。

## Phase 进度

- [x] Phase 0 — 版本兼容性分析（本文件上方）
- [x] Phase 1 — 系统架构设计（分层图 / Agent Flow / 数据流）
- [x] Phase 2 — Maven 多模块结构（8 模块，无循环依赖）
- [x] Phase 3 — pom.xml 全量 + application.yml + Spring AI 配置（ChatClient 装配；OTel bridge 留到 Phase 9）
- [x] Phase 4 — Embabel Agent：EnterpriseAssistantAgent（17 个 @Action 方法 + 5 个 @Condition + GOAP + 双路径 Goal）
  - 注意：8 个 Action 的实现已随 Phase 4 落地（Embabel 的 Action 即 Agent 的方法），Phase 5 聚焦基础设施实现
- [x] Phase 5 — 基础设施实现：JPA 实体 4 / Repository 4 / ServiceImpl 3（强制租户过滤）/ 审计落库 / schema.sql + data.sql（`mvn compile` 通过）
- [x] Phase 6 — Tools：4 个 Spring AI @Tool + ConfigurationBasedToolPermissionEvaluator + PermissionAwareToolRegistry（按请求过滤；`mvn compile` 通过）
- [x] Phase 7 — RAG：KnowledgeSearchServiceImpl（tenantId/documentType/visibility 过滤表达式）+ KnowledgeIngestionService（切块+元数据）（`mvn compile` 通过）
- [x] Phase 8 — Prompt 版本管理：PromptVersionProperties（agent.prompts.active-versions 灰度覆盖）+ PromptRegistry 升级 + prompts/README.md（灰度/回滚流程）
- [x] Phase 9 — Controller（POST /api/agent/chat）+ 鉴权过滤器 + 全局异常收口 + 租户上下文跨线程传播 + Redis 会话记忆 + Micrometer 指标 + OTel 追踪（`mvn compile` 通过）
- [x] Phase 10 — 测试：26 个测试全绿（`mvn test` BUILD SUCCESS）——单测 21 + Agent 集成 2 + HTTP 冒烟 3；
  集成测试用 Embabel 官方 `IntegrationTestUtils.dummyAgentPlatform` + `LlmOperations` 按输出类型打桩，
  真实 GOAP 规划器下验证意图路由与兜底路径
- [x] Phase 11 — Web 界面：`static/index.html` 单文件（零新依赖，Boot 默认欢迎页机制托管），
  已随 `mvn package` 打入 BOOT-INF/classes/static/

## Review

### Phase 12 续 — LLM 真实联调打通（2026-08-28，端到端 SUCCEEDED + 审计 + 租户隔离全部实证）

- **网关协议判定**：用户密钥为 Kimi For Coding 网关（`https://api.kimi.com/coding`，OpenAI 兼容）。
  探测确认 `/v1/chat/completions` 与 `/v1/embeddings` 均通；kimi-k2-0905-preview 为推理模型
  （reasoning 占 token，max_tokens 过小会 content 空，易误判为网关故障）。
- **真实联调修复 3 个问题（全部日志/字节码实证，非猜测）**：
  1. **Embabel 走官方 openai-java SDK 而非 Spring AI 客户端**（404 根因）：URL=`baseUrl+chat/completions`
     （SDK 固定路径）→ embabel base-url 必须补 `/v1`；yml 中两通道写法分叉
     （Spring AI 通道不带 /v1）。`completions`/`embeddings-path` 属性是废弃空操作（jar 日志原文自证）。
  2. **ES 向量维度 1536→1024**（Kimi 网关 embedding 实测 1024 维）+ 删旧索引重建（已验 mapping dims=1024）。
  3. **租户上下文跨虚拟线程丢失**（架构级）：Embabel 1.5 的 SIMPLE 模式同样把 Action 跑在
     `embabel-virtual` 虚拟线程（newThreadPerTaskExecutor，每任务新建）→ 修正此前"SIMPLE 在调用线程执行"
     的结论；解法 = TenantContext 改 **InheritableThreadLocal**（每任务新建线程自动继承，无池化脏读风险），
     同时把 TenantContextAgentProcessCallback 改为无实例态（按 AgentProcess 键控 ConcurrentMap，
     消除共享实例下 captured 字段的跨请求竞态——多租户铁律加固）。
- **端到端实证（全链路：鉴权→意图识别→参数提取→权限过滤→DB 查询→分析→应答）**：
  - tenant-a"查询华东地区有哪些企业"→ `SUCCEEDED`/`ENTERPRISE_QUERY`，回答基于 8 家真实种子数据
    （含城市/行业/注册资本结构化分析 + SUSPENDED 风险提示），耗时 117s（推理模型多轮 Action 各一次 LLM 调用）；
  - **审计落库**：agent_execution 记录 SUCCEEDED + intent + 117s（两轮失败历史亦完整留痕）；
  - **租户隔离**：同问题以 tenant-b 身份 → 只见自己 2 家企业（tenant-a 的 8 家完全不可见）；
    种子数据刻意让"北京中科智联"两租户各有一行，双方各自只拿自己行——隔离铁律实证。
  - 回归：全模块测试 BUILD SUCCESS（core 6 / infra 4 / tool 5 / rag 8 / api 3 / application 5）。
- **遗留小项**（不阻塞）：LLM 失败分类宜为 LLM_ERROR（现为 BUSINESS_ERROR 兜底）；
  HttpMessageNotReadableException 宜映射 400；3 分钟退避重试对交互场景偏长
  （`embabel...max-attempts` 可调小）。

### Phase 12 — 真实启动（2026-08-28，服务 UP + 界面已打开，回归 26/26 全绿）

- **环境装配**：docker-compose.yml（MySQL 8.4 + Redis 7 + **Elasticsearch 9.1.0**）；
  schema/data.sql 已导入（tenant-a: 8企业/10告警，tenant-b: 2企业/1告警）。
- **启动过程修复 5 个真实装配 bug**（全部为编译/离线测试无法覆盖、只有真实启动才暴露）：
  1. 主类缺 `scanBasePackages="com.enterpriseai"` —— 默认只扫 application 子包，7 个模块 bean 全丢；
  2. ObjectMapper 注入类型错误：Boot 4 自动装配的是 **Jackson 3（tools.jackson）**，fasterxml 注入无 bean
     （过滤器与两处测试改用 tools.jackson.databind.ObjectMapper）；
  3. JPA 仓库缺 `@EnableJpaRepositories(basePackages=infra仓库包)`（与组件扫描相互独立）；
  4. 实体未纳管：Boot 4 的 `@EntityScan` 迁包至 `org.springframework.boot.persistence.autoconfigure`；
  5. pom 缺 `spring-boot-starter-elasticsearch`（Rest5Client bean 来源）+ **ES 服务端须 9.x**
     （rest5 客户端对 ES 8 的 indices.exists HEAD 返回 400 无体；旧数据卷不可原地升级，已删卷重建）；
  6. ToolPermissionProperties 上 `@Component`+`@ConfigurationProperties` 并存致启动崩（去掉 @Component）。
- **端到端实测**（curl）：静态页 200；缺头 401 PERMISSION_DENIED；无 LLM 密钥时对话得优雅错误且
  响应携带 executionId；**审计表落库成功**（agent_execution 记录 tenant/user/session/status=FAILED）——
  JPA 实体/仓库/审计链路在真实运行时全部打通。
- **遗留小项**（不阻塞）：HttpMessageNotReadableException 宜映射 400 VALIDATION_ERROR（现为 500 兜底）；
  无密钥时 LLM 失败分类为 BUSINESS_ERROR（宜为 LLM_ERROR）。
- **对话功能待配**：`LLM_API_KEY`（OpenAI 兼容端点）配置后重启即可，界面其余功能（身份/租户切换、
  权限勾选、健康灯、会话管理）已可用。

### Phase 11（2026-08-28 完成，`mvn package` 通过，页面已入 boot jar）

- **方案**：单文件 `application/src/main/resources/static/index.html`（约 420 行，内嵌 CSS/JS），
  零新依赖——符合"禁止为展示 AI 能力引入不必要复杂架构"。Boot 默认欢迎页机制托管在 `/`，
  与 `/api/agent/chat` 同源，无 CORS。
- **功能**：
  1. 对话区：气泡消息、Ctrl+Enter 发送、思考中计时器、错误气泡（错误码+用户信息+可重试+executionId）；
  2. 身份面板（本地演示专用，生产由网关注入信任头）：租户切换（tenant-a/b——即多租户隔离的
     可视化验证工具）、用户 ID、5 个权限点勾选（后端真实生效：工具可见性与 RAG restricted 过滤）；
  3. 会话管理：UUID 自动生成/一键新会话；
  4. 每条回答带 元数据行：意图徽章 + executionId + 耗时——把可观测性键直接暴露给使用者；
  5. 健康状态灯（/actuator/health，非鉴权路径）、4 个贴合种子数据的示例问题。
- **安全边界不变**：页面只是"信任头的本地来源"，鉴权/租户/权限逻辑全部仍在后端
  （AuthenticationFilter → TenantContext → 工具/RAG/Service 过滤链），页面无任何旁路。
- **极简 Markdown 渲染**：先 HTML 转义再做加粗/行内代码替换，无 innerHTML 注入面。

### Phase 10（2026-08-28 完成，`mvn test` 全模块 BUILD SUCCESS，26/26 全绿）

- **测试设施（全部来自已核实的官方 API，离线运行：无真实 LLM/MySQL/ES/Redis）**：
  - `IntegrationTestUtils.dummyAgentPlatform(llmOperations)`（Embabel api jar 自带）构独立平台；
    `AgentMetadataReader`（全默认参构造 → Kotlin 自动生成无参构造，Java 可直接 new）把
    @Agent 注解实例转为 Agent，`platform.deploy()` 注册；
  - LLM 打桩遵循 embabel-agent-test 官方示例 `EmbabelMockitoIntegrationTest` 的签名：
    `llmOperations.createObject(messages, llmInteraction, eq(OutputClass), any, any)` 按输出类型精确返回；
  - HTTP 冒烟用 MockMvc standalone（不启动容器）：过滤器/异常收口/审计/记忆逐件显式装配。
- **集成测试抓出并修复一个真实架构级 bug（本阶段最大产出）**：
  `@Condition` 命名条件出现在 `@Action(pre=...)` 中时，**必须有某个 Action 在 `post` 中声明该条件**，
  否则 AStarGoapPlanner 的可达性检查（isGoalReachable）判死 → 任何意图分支都规划失败 → invoke 返回 null。
  修复：`recognizeIntent` 增加 `post = {五个意图条件}`（GOAP 乐观声明 + 运行时按黑板实况重规划收敛）。
  修复前：集成/冒烟测试 500 + NPE；修复后全绿——若无集成测试，此问题要到真实环境联调才暴露。
- **测试覆盖清单（26 个）**：
  - core 6：TenantContext（require 失败方向=PERMISSION_DENIED/清理）、PromptRegistry（基线/灰度覆盖/缺模板快速失败）
  - tool 5：权限评估（未登记 DENY、显式 ALLOW、* 通配、按权限点判定）
  - infrastructure 4：统计口径（COUNT 总量单行/MAX 默认 top1 降序/MIN 升序/ENTERPRISE_COUNT 不支持组合快速失败）
  - rag 8：隔离表达式（租户等值必有、documentType 可选、无 restricted 权限强制 public、注入清洗）、
    请求构造（topK/查询词/过滤表达式）、摄入（tenantId 只取自上下文、切块边界、visibility 规范化）
  - api 3：鉴权过滤器（头解析/401/ThreadLocal 清理/非 API 路径放行）
  - application 5：Agent 集成（ENTERPRISE_QUERY 全链路 7 Action + 其余服务零调用；UNKNOWN 兜底零副作用）、
    HTTP 冒烟（200 响应结构+审计+记忆写入 / 401 / 400 参数校验）
- **顺手修正**：api 模块补 `spring-boot-starter-validation`（否则 @Valid 无实现、400 语义不成立）；
  rag 模块 `buildFilter` 抽为包私有静态方法（隔离规则可直接断言）。
- **Phase 4 遗留验证项全部关闭**：`@Action(pre=...)` 按条件名引用 + @Condition 方法名即条件名，
  已在真实 AStarGoapPlanner 下验证成立（且发现 post 缺失问题并修复）。

### Phase 8+9（2026-08-28 完成，`mvn compile` 全模块通过）

- **实现前核实（源码级，杜绝臆造 API）**：
  1. Embabel `AgentPlatformProperties.processType` 默认 **SIMPLE** —— SimpleAgentProcess
     **不调用** callbacks（grep 零调用点），Action 在调用线程执行，TenantContext 天然有效；
     ConcurrentAgentProcess 经 `asyncer.async{}` 在独立线程执行 Action，会丢 ThreadLocal。
  2. 官方为此提供 `AgentProcessCallback` 钩子（KDoc 示例即 Spring Security 上下文传播，
     与租户上下文同构）：`beforeActionLaunched` 捕获 → `onActionLaunched` 恢复（每 Action）→
     `onActionCompleted` 清理；经 `@Autowired List<AgentProcessCallback>` 自动注入 platform。
  3. `AgentInvocation.create(platform, Class<T>)` 为 companion `@JvmStatic`；`invoke(obj, vararg): T`
     阻塞返回、无受检异常。
  4. Embabel `ActionException.Permanent/Transient`（Transient=官方可重试标记）。
  5. Boot 4.1.1 BOM：`spring-boot-starter-opentelemetry` + `spring-boot-micrometer-tracing-opentelemetry`
     原生管理（otel-bom@1.62.0 / micrometer-tracing@1.7.1）——替代 Boot 3 时代手工拼
     `micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp` 的做法，零版本号风险。
- **Phase 8 交付**：`agent.prompts.active-versions.{category/name}: N` 配置化灰度
  （代码声明基线版本，配置覆盖生效版本；回滚=删一行）；prompts/README.md 固化
  版本约定与上线流程。
- **Phase 9 交付**：
  1. `AuthenticationFilter`（信任头模式，网关注入 X-User-Id/X-Tenant-Id/X-Permissions，
     缺失即 401；请求结束清理 ThreadLocal）；
  2. `AgentChatController`：executionId 贯穿（请求属性→异常处理→审计），
     sessionId 空则生成；成功后按 tenantId+sessionId 写会话记忆；
  3. `GlobalExceptionHandler`：7 类错误码→403/400/500；ActionException→TOOL_ERROR
     （Transient 可重试）；任何异常不外泄堆栈/SQL/内部类名；
  4. `TenantContextAgentProcessCallback`（prototype）：SIMPLE 下零开销，CONCURRENT 下
     按官方钩子传播租户上下文——多租户铁律在两种执行模式下的正确性都有保障；
  5. `RedisConversationStore`（短期记忆，key 带租户隔离、TTL 7 天、上限 100 条）；
  6. `MetricsAgentExecutionRecorder`（@Primary 装饰 MySQL 审计）：
     agent.execution{status,intent} / agent.action{action,outcome} / agent.action.errors{action}，
     executionId/tenantId 不做 tag（无界基数），租户级聚合走审计表；
  7. 日志 pattern 注入 traceId/spanId，与 executionId 双键关联。
- **修复 Phase 6 隐患**：`@ConfigurationProperties` 类此前未注册（无 Scan/Enable 注解），
  运行时会因缺 bean 启动失败——主类补 `@ConfigurationPropertiesScan`。

### Phase 6+7（2026-08-28 完成，`mvn compile` 通过）

- **核实结论（重要）**：
  1. Embabel 1.5.0 工具机制 = `@LlmTool`（api/annotation/LlmTool.kt，含 Param 子注解）+ `PromptRunner.withToolChainingFrom(type)`
     （ToolChaining 接口实证）；README 的 `@Action(toolGroups=...)` 是已废弃的旧 API。
  2. Spring AI 2.0 工具 API（从本地 2.0.0 jar javap 核实）：
     `org.springframework.ai.tool.annotation.Tool/ToolParam`、`org.springframework.ai.support.ToolCallbacks.from(Object...)`、
     `ToolCallback.getToolDefinition().name()`、`SearchRequest.builder().query().topK().filterExpression(String).build()`、
     `Document(String, Map)`。
  3. ES VectorStore 连接走 Boot 原生 `spring.elasticsearch.uris`（autoconfigure 元数据中无 uris 属性）——已修正 yml。
- **Phase 6 交付**：4 个 @Tool（契约完整：name/description/input/output/permission）+ 权限评估器（缺省 DENY，
  未登记权限点的新工具默认不可见）+ 注册中心（**每次调用**过滤，非启动时快照）。
- **Phase 7 交付**：检索端 filterExpression 硬隔离（tenantId 等值 + documentType 可选 + visibility 按
  knowledge:restricted 权限）+ 摄入端切块与元数据契约（与过滤条件一一对应）。
- **修复**：StringBuilder 无 isBlank()。

### Phase 5（2026-08-28 完成，`mvn compile` 通过）

- **交付**：4 个 JPA 实体、4 个 Repository（动态条件走 Specification，统计聚合走 JPQL）、
  3 个 ServiceImpl、MySqlAgentExecutionRecorder、schema.sql + data.sql（双租户样例）、统计 Prompt 口径修订、虚拟线程开启。
- **关键决策**：
  1. 租户隔离 = 每个查询显式 `tenantId` 条件（取自 `TenantContext.require()`），不用 Hibernate filter/拦截器——审计可见、无魔法；
  2. `escapeLike` 转义 LIKE 通配符（`%_\`），防通配符注入；
  3. 统计口径：COUNT=分组计数（groupBy 可空=总量单行）、MAX/MIN=计数后按值排序取 topN、
     ENTERPRISE_COUNT 仅支持 groupBy=null/DAY（其余组合快速失败 VALIDATION_ERROR）——Prompt 与实现口径一致；
  4. 时间参数由服务层兜底为极值，JPQL 不出现 null 参数（避开 Hibernate null 参数坑）；
  5. 审计失败只记 WARN 不影响主流程。
- **修复**：`Instant.now()` 笔误、`groupBy().name()` 空安全（总量统计场景）。

### Phase 3+4（2026-08-28 完成，`mvn compile` 全模块通过）

- **交付**：9 个 pom、application.yml、启动类、SpringAiConfig、17 个 Domain 类型、8 个 core 类、
  EnterpriseAssistantAgent（340 行）、8 个 Prompt 模板初版。
- **验证**：JDK 21 + Maven 3.8.9 下 `mvn -DskipTests compile` BUILD SUCCESS。
  期间修复 2 类真实错误：record 组件上误用 `@JsonClassDescription`（应为 `@JsonPropertyDescription`，共 8 处）。
- **关键决策**：
  1. LLM 双通道配置：`embabel.agent.platform.models.openai.*`（Embabel Agent 走）+ `spring.ai.openai.*`（Spring AI EmbeddingModel/RAG 走），
     同一环境变量喂两处，保证端点一致——属性名均核实自 autoconfigure jar 的 spring-configuration-metadata.json。
  2. Boot 4 起 web starter 采用新名 `spring-boot-starter-webmvc`（旧名 `web` 仍存在）。
  3. OTel bridge（micrometer-tracing-bridge-otel + otlp exporter）延迟到 Phase 9，避免未验证版本号。
- **待运行时验证（Phase 10）**：`@Action(pre=...)` 按条件名引用的语义（编译已验证类型正确）；
  `spring.ai.vectorstore.elasticsearch.*` 属性名（Phase 7 复核）。
