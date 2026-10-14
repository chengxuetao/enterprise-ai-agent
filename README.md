# 企业级 AI Agent 系统（Enterprise AI Agent）

> 基于 **Spring Boot 4.1 / Spring AI 2.0.1 / Embabel 1.5.3** 构建的企业级智能助手参考实现。
> 一个 Java 21 + Maven 多模块工程，把"LLM 编排框架（Embabel GOAP）+ 结构化输出 + 多租户 + 工具权限 + RAG + 全链路可观测"按生产标准整合在一起。

---

## 目录

1. [项目简介](#1-项目简介)
2. [技术栈与版本基线](#2-技术栈与版本基线)
3. [系统架构](#3-系统架构)
4. [核心设计原则（架构铁律）](#4-核心设计原则架构铁律)
5. [模块详解](#5-模块详解)
6. [Agent 执行流程详解](#6-agent-执行流程详解)
7. [数据模型](#7-数据模型)
8. [Prompt 工程与版本管理](#8-prompt-工程与版本管理)
9. [安全设计](#9-安全设计)
10. [可观测性](#10-可观测性)
11. [使用说明](#11-使用说明)
12. [配置参考](#12-配置参考)
13. [测试体系](#13-测试体系)
14. [已知限制与踩坑记录](#14-已知限制与踩坑记录)
15. [目录结构总览](#15-目录结构总览)

---

## 1. 项目简介

### 1.1 这是什么

一个**企业智能助手 Agent**：用户用中文自然语言提问，系统自动完成"意图识别 → 参数提取 → 业务查询 → 数据分析 → 应答组织"的完整认知流水线，最终返回结构化、可追溯的中文 Markdown 回答。

支持的四类业务能力：

| 能力 | 意图 | 数据来源 | 示例问题 |
|---|---|---|---|
| 企业信息查询 | `ENTERPRISE_QUERY` | MySQL `enterprise` 表 | "查询华东地区有哪些企业" |
| 告警查询 | `ALARM_QUERY` | MySQL `alarm` 表 | "今天的严重告警有哪些" |
| 数据统计 | `STATISTICS_QUERY` | MySQL 聚合查询 | "按告警类型统计数量，哪个最多" |
| 知识库问答 | `KNOWLEDGE_QA` | Elasticsearch 向量检索（RAG） | "环保超标告警的处理流程是什么" |
| 无法识别 | `UNKNOWN` | — | 澄清引导（不产生任何数据查询副作用） |

### 1.2 企业级特性

- **多租户硬隔离**：租户上下文贯穿 HTTP → Agent 虚拟线程 → SQL / 向量检索 / 会话记忆，跨租户数据在存储层即不可达（种子数据刻意含两租户同名企业，用于验证隔离）。
- **后端工具权限**：默认 `DENY`，未授权工具**不注册**（LLM 根本看不见），权限判定纯 Java，与 Prompt 无关。
- **全链路审计**：每次执行落库 `agent_execution`（主表）+ `agent_action_execution`（每个 Action 明细），`executionId` 贯穿日志、指标、Trace、API 响应。
- **Prompt 版本灰度**：一个文件一个认知任务，`{name}-v{version}.st` 命名，配置一行即可灰度/回滚，模板不可变、永不原地修改。
- **可观测性**：Micrometer 指标（Prometheus）+ OpenTelemetry Tracing（OTLP）+ traceId/spanId 注入日志行。
- **LLM 供应商无关**：OpenAI 兼容端点均可接入（实测 Kimi For Coding 网关；DeepSeek/Qwen/vLLM 可覆写配置接入）。

### 1.3 交付状态（2026-10-13 实证）

- ✅ 53 个测试全绿（Phase 13：升级 Spring AI 2.0.1 / Embabel 1.5.3 + 多轮对话 + 错误分类修复 + 审计补线；基线 31 个复核修正），`mvn test` BUILD SUCCESS
- ✅ 真实 LLM 端到端联调 SUCCEEDED（意图识别→参数提取→权限过滤→DB 查询→分析→应答全链路，117s，推理模型多轮 Action 各一次 LLM 调用）
- ✅ 审计落库、租户隔离（tenant-b 只见自己 2 家企业，tenant-a 的 8 家完全不可见）实证通过
- ✅ Web 聊天界面（单文件 `index.html`，随 Boot jar 托管在 `/`）

---

## 2. 技术栈与版本基线

### 2.1 版本矩阵（2026-10-13 逐项从 Maven Central POM / sources jar 核实）

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | **21（LTS）** | 启用虚拟线程（`spring.threads.virtual.enabled=true`），适配 LLM 长阻塞 IO |
| Spring Boot | **4.1.1** | Maven Central 最新 GA；Boot 4 有多处破坏性变更（见 §14） |
| Spring AI | **2.0.1**（spring-ai-bom） | 提供 EmbeddingModel、ChatClient、ES VectorStore、`@Tool` 注解；2.0.1 升级说明中的破坏性变更（Redis 记忆模块重命名/Mistral/MCP）均不涉及本项目 |
| Embabel | **1.5.3**（`com.embabel.agent:embabel-agent-starter`） | Agent 编排框架：`@Agent`/`@Action`/`@Condition`/`@AchievesGoal` + GOAP 规划器；构建于 Spring AI 2.0.1 + Boot 4.1.1 之上 |
| 数据库 | MySQL 8.4 | 业务数据 + 审计落库，JPA（Hibernate），schema 手动 SQL 管理 |
| 缓存/记忆 | Redis 7 | 会话短期记忆（TTL 7 天） |
| 向量库 | Elasticsearch 9.1.0 | Spring AI 2.0 的 rest5 客户端（elasticsearch-java 9.x）**要求 ES 9.x 服务端** |
| 可观测 | Micrometer + micrometer-tracing（OTel 桥） | Boot 4.1 BOM 原生管理 otel-bom@1.62.0 |
| 构建 | Maven 多模块（8 模块） | 无循环依赖，`enterprise-ai-agent-application` 为可执行聚合模块 |

> ⚠️ **版本核实说明**：Embabel 的 GitHub README / java-agent-template 长期停留在 0.3.x + Boot 3.5，与 Maven Central 实际发布严重脱节。本项目所有 Embabel API 用法均从 `embabel-agent-api-1.5.3-sources.jar` 源码核实（1.5.0→1.5.3 逐文件 diff 证实 API 只增不删），**以 Maven Central 为准，勿信官方 README**。

### 2.2 模块依赖关系

```
                        ┌─────────────────────────────┐
                        │  application（可执行启动模块）│ ← embabel-agent-starter-openai
                        │  Spring AI OpenAI starter    │   spring-ai-starter-model-openai
                        │  ES client / actuator / OTel │   spring-boot-starter-elasticsearch
                        └──────────┬──────────────────┘   micrometer-registry-prometheus
                                   │ 聚合全部模块
                ┌──────────────────┼──────────────────┐
                ▼                  ▼                  │
        ┌──────────────┐  ┌──────────────┐            │
        │ api（REST）   │  │ agent（Agent）│            │
        │ webmvc+valid │  │ 依赖 tool+rag │            │
        └──────┬───────┘  └──┬───────┬───┘            │
               │             │       │                │
               │   ┌─────────▼─┐  ┌──▼──────────┐     │
               │   │ tool      │  │ rag         │     │
               │   │ @Tool×4   │  │ ES 向量检索  │     │
               │   │ 权限评估器 │  │ 摄入+查询   │     │
               │   └─────┬─────┘  └──────┬──────┘     │
               │         │               │            │
               ▼         ▼               ▼            ▼
        ┌──────────────────────────────────────────────┐
        │ core（上下文/异常/记忆契约/观测契约/权限契约/  │
        │       PromptRegistry）← 依赖 → domain         │
        ├──────────────────────────────────────────────┤
        │ infrastructure（JPA/Redis/审计/ServiceImpl）  │
        │  依赖 core + domain + data-jpa/redis/mysql    │
        └──────────────────────────────────────────────┘
        domain：零框架依赖（仅 jakarta.validation-api +
                 jackson-annotations），纯 record 模型 + 服务接口
```

依赖方向铁律：`domain ← core ← {infrastructure, tool, rag} ← agent ← api ← application`，**domain 与 core 完全不 import Embabel / Spring AI 类**——底层与 AI 框架解耦，唯一的 LLM 契约通过 Jackson 注解表达。

---

## 3. 系统架构

### 3.1 分层架构总览

```
 用户浏览器（static/index.html，本地演示）
        │  POST /api/agent/chat  + 信任头（X-User-Id / X-Tenant-Id / X-Permissions）
        ▼
┌─────────────────────────── API 网关（生产环境，JWT 校验后注入信任头） ──────────────────────────┐
│                                                                                              │
│  ┌──────────────────────────── application 模块（Spring Boot 4.1，虚拟线程）────────────────┐  │
│  │                                                                                          │  │
│  │  AuthenticationFilter (@Order(1))                                                        │  │
│  │    ├─ /api/** 必须携带身份头，缺失 401 PERMISSION_DENIED                                  │  │
│  │    ├─ 构建 UserContext → TenantContext.set()（InheritableThreadLocal）                    │  │
│  │    └─ finally TenantContext.clear()（防线程复用串租户）                                    │  │
│  │                                                                                          │  │
│  │  AgentChatController (POST /api/agent/chat)                                              │  │
│  │    ├─ AgentExecutionContext.begin()（executionId=UUID，贯穿全链路）                        │  │
│  │    ├─ AgentInvocation.create(platform, AgentResponse.class).invoke(UserInput)   ←──────┐  │  │
│  │    ├─ 成功：写会话记忆（Redis）+ 返回 ChatResponse                                      │  │  │
│  │    └─ finally：审计收尾 + ThreadLocal 清理                                              │  │  │
│  │                                                                                          │  │  │
│  │  ┌─────────────────── agent 模块：EnterpriseAssistantAgent（Embabel GOAP）──────────┐   │  │  │
│  │  │  @Action × 17 + @Condition × 5 + @AchievesGoal × 2                                │   │  │  │
│  │  │  意图识别(LLM) → 意图路由(纯Java) → 参数提取(LLM) → 业务查询(纯Java)                │   │  │  │
│  │  │  → 结果归一化(纯Java) → 数据分析(LLM) → 最终应答(LLM) / 未知兜底(LLM)               │   │  │  │
│  │  └──────┬───────────────────────────────────────────────────────────────────┬───────┘   │  │  │
│  │         │ 强类型 Query record（LLM 结构化输出）                     │           │  │  │
│  └─────────┼───────────────────────────────────────────────────────────┼───────────┘  │  │
│            ▼                                                           ▼              │  │
│  ┌── tool 模块 ──────────────┐                    ┌── rag 模块 ───────────────┐        │  │
│  │ Spring AI @Tool × 4        │                    │ KnowledgeSearchServiceImpl│        │  │
│  │ PermissionAwareToolRegistry│                    │  （ES 向量检索 + 租户     │        │  │
│  │ ConfigurationBased 权限评估 │                    │    filterExpression 硬隔离)│        │  │
│  └──────────┬────────────────┘                    └──────────┬────────────────┘        │  │
│             │                                                 │                      │  │
│  ┌──────────▼────────────────────────────────────────────────▼──────────────────────┐  │  │
│  │ domain 服务接口 ← infrastructure 实现                                               │  │  │
│  │  EnterpriseServiceImpl / AlarmServiceImpl / StatisticsServiceImpl                 │  │  │
│  │  （每个查询强制 TenantContext.require() 租户条件，Specification 动态拼装）          │  │  │
│  │  MySqlAgentExecutionRecorder（审计落库）← MetricsAgentExecutionRecorder(@Primary 装饰)│ │  │
│  │  RedisConversationStore（会话记忆，key 天然带租户）                                 │  │  │
│  └──────────┬───────────────────┬──────────────────┬────────────────────────────────┘  │  │
└─────────────┼───────────────────┼──────────────────┼────────────────────────────────────┘
              ▼                   ▼                  ▼
        MySQL 8.4            Redis 7          Elasticsearch 9.1
   （业务表×2+审计表×2）  （会话记忆 TTL 7d）  （enterprise-knowledge 向量索引，1024 维）
```

### 3.2 请求处理全链路（时序）

以一次成功的对话请求为例：

```
浏览器/客户端
  │ POST /api/agent/chat {message, sessionId}
  │ Headers: X-User-Id / X-Tenant-Id / X-Permissions
  ▼
AuthenticationFilter ── 解析信任头 → UserContext → TenantContext.set()
  │                        （InheritableThreadLocal，子线程自动继承）
  ▼
AgentChatController
  │ 1. sessionId 兜底生成 UUID
  │ 2. AgentExecutionContext.begin() → executionId（UUID）
  │    挂到请求属性 + recorder.onExecutionStart()
  │ 3. AgentInvocation.create(platform, AgentResponse.class)
  │    .invoke(new UserInput(message))        ← 阻塞式，虚拟线程上执行
  ▼
Embabel AgentPlatform（GOAP 规划器）
  │ 读取 @Agent 元数据 → AStarGoapPlanner 规划 → 在 embabel-virtual 虚拟线程逐个执行 Action
  │ （黑板 blackboard：强类型对象在 Action 之间按类型匹配自动传递）
  ▼
EnterpriseAssistantAgent 的 17 个 @Action（见 §6；首个为纯 Java 的历史加载
  loadConversationHistory：读 Redis 会话记忆 → ConversationHistory 注入意图/提取 Prompt）
  │ LLM 节点：ai.withDefaultLlm().createObject(prompt, X.class)  ← 结构化输出
  │ Java 节点：领域服务查询 / 归一化转换
  ▼
AgentResponse {answer(Markdown), intent}
  │ 4. context.markSucceeded()
  │ 5. conversationStore.appendMessage(tenant, session, user/assistant)
  │ 6. 返回 ChatResponse {executionId, answer, intent, status=SUCCEEDED}
  │ finally: recorder.onExecutionEnd()（审计落库+指标） + AgentExecutionContext.clear()
  ▼
客户端收到 JSON；审计表/Micrometer/OTel 均以 executionId 关联留痕
```

失败路径：任一环节抛 `AgentException` → Controller `markFailed` 重抛 → `GlobalExceptionHandler` 按错误码映射 403/400/500，对外只暴露 `{code, userMessage, retryable, executionId}`，堆栈仅进服务端日志。

---

## 4. 核心设计原则（架构铁律）

以下原则贯穿全部模块，是本项目的"宪法"：

1. **LLM 永不直接访问数据库。** LLM 只产出强类型结构化对象（Query）与文本分析（Analysis/Answer），所有 DB 操作一律走 Java Service。SQL 由 Java 侧按强类型 Query 动态拼装，LLM 不接触 SQL。

2. **权限控制只做在 Java 后端。** 按用户权限点过滤可暴露给 LLM 的工具集；未授权工具**不注册**（让 LLM 看不见），而非注册后靠 Prompt 提示拒绝——Prompt 约束永远不是安全边界。

3. **一个 Action 只做一件事。** 17 个单一职责 `@Action`，由 GOAP Planner 按类型数据流自动串联；历史加载/查询/归一化 Action 是纯 Java（无 LLM），LLM 只出现在 4 类认知节点（意图识别、参数提取、数据分析、最终应答）。

4. **一个 Prompt 只做一个认知任务。** intent / parameter / analysis / response 四层分离，各自独立 `.st` 文件 + 版本号，禁止"万能 Prompt"。

5. **强类型传递（Structured Output）。** 所有 LLM 调用均 `createObject(prompt, Record.class)` 产出 Java record；输出契约 = record 的 Jackson schema（`@JsonClassDescription` / `@JsonPropertyDescription`），枚举闭集防幻觉。

6. **租户隔离三道防线。** ① `TenantContext`（InheritableThreadLocal + `require()` 快速失败 PERMISSION_DENIED）；② 每个 ServiceImpl 查询显式追加 tenantId 条件（不用 Hibernate filter 等魔法，审计可见）；③ 存储 key / 向量过滤天然带租户（跨租户读取在存储层不可达）。

7. **对外错误信息脱敏。** 7 类错误码携带面向用户的中文文案；任何异常不外泄堆栈、SQL、内部类名；内部细节只进日志与审计表。

---

## 5. 模块详解

### 5.1 enterprise-ai-agent-domain —— 领域模型（零框架依赖）

**定位**：强类型领域模型 + 领域服务接口。仅依赖 `jakarta.validation-api`（注解 API）与 `jackson-annotations`（LLM schema 描述），**不 import 任何 Spring/Embabel/Spring AI 类**——这使领域层可以脱离 AI 框架独立演进与测试。

#### 5.1.1 意图与枚举闭集

| 枚举 | 取值 | 用途 |
|---|---|---|
| `IntentType` | `ENTERPRISE_QUERY` / `ALARM_QUERY` / `STATISTICS_QUERY` / `KNOWLEDGE_QA` / `UNKNOWN` | 系统支持的意图闭集，LLM 只能从中选择 |
| `AlarmLevel` | `CRITICAL` / `HIGH` / `MEDIUM` / `LOW` | 告警等级闭集 |
| `StatisticsQuery.AggregationType` | `COUNT` / `MAX` / `MIN` | 聚合方式闭集 |
| `StatisticsQuery.StatisticsMetric` | `ALARM_COUNT` / `ENTERPRISE_COUNT` | 统计指标闭集 |
| `StatisticsQuery.GroupByDimension` | `ENTERPRISE` / `ALARM_TYPE` / `ALARM_LEVEL` / `DAY` | 分组维度闭集 |

#### 5.1.2 模型清单（全部为 Java record）

| Record | 组件 | 契约要点 |
|---|---|---|
| `UserRequest(message, sessionId)` | API 入口请求 | message 为空抛 IAE；sessionId 为空默认 `"default"`；**与 Embabel 的 UserInput 解耦**，Controller 负责转换 |
| `IntentResult(intent, confidence, reason)` | 意图识别 LLM 输出 | confidence 0.0–1.0；低于阈值必须 UNKNOWN（Prompt 约束） |
| `EnterpriseQuery(nameKeyword, city, industry, minRegisteredCapitalWanYuan, limit)` | 企业查询条件 | **所有字段可空，未提供必须 null，禁止猜测**；带 `@Size`/`@DecimalMin`/`@Max(200)` 校验 |
| `EnterpriseResult(enterprises, total, truncated)` | 企业查询结果 | 内嵌 `EnterpriseSummary(id,name,city,industry,registeredCapitalWanYuan,legalPerson,status)` |
| `AlarmQuery(startTime, endTime, enterpriseName, alarmType, alarmLevel, limit)` | 告警查询条件 | 相对时间（"今天"/"最近 7 天"）由 LLM 依据 Prompt 注入的 `current_time` 换算为 ISO-8601 绝对时间 |
| `AlarmResult(alarms, total, truncated)` | 告警查询结果 | 内嵌 `AlarmRecord(id,enterpriseName,alarmType,alarmLevel,occurredAt,message,status)` |
| `StatisticsQuery(aggType, metric, groupBy, startTime, endTime, topN)` | 统计口径 | 三个闭集枚举防 LLM 发明不支持的口径；topN ∈ [1,50] |
| `StatisticsResult(..., rows, totalRows)` | 统计结果 | 回显查询三要素 + `StatisticsRow(dimension, value)`；聚合计算全部在 DB/Java 侧，LLM 只解释 |
| `KnowledgeQuery(question, documentType, topK)` | RAG 检索条件 | question 是**改写后的检索问句**（非原文照抄）；topK ∈ [1,10] 默认 5 |
| `KnowledgeResult(question, snippets)` | RAG 检索结果 | `KnowledgeSnippet(title, content, source, score)`，已按租户隔离 |
| `AnalysisData(intent, dataPresent, totalRecords, truncated, sourceDescription, rows)` | 归一化分析输入 | 四条业务分支统一转换为此类型；行数已在转换时截断（保护上下文窗口）；内嵌 `DataRow(label, details)` |
| `AnalysisResult(summary, findings, risks, recommendations)` | 数据分析 LLM 输出 | **结论只能来自 AnalysisData，禁止编造**；null List 归一为空 |
| `AgentResponse(answer, intent)` | Agent 最终输出（Goal 类型） | answer 为中文 Markdown；`@AchievesGoal` 挂在产出此类型的 Action 上 |
| `ConversationHistory(messages)` | 多轮上下文（黑板数据流类型） | 内嵌 `ConversationMessage(role, content)`；`transcript()` 渲染为 Prompt 注入文本（"用户：…/助手：…"，单条截断 300 字）；非 LLM 输出类型，由纯 Java Action 从会话记忆加载 |

#### 5.1.3 领域服务接口（单方法接口 + 租户隔离契约）

| 接口 | 方法 | 实现方 | 接口 Javadoc 强制契约 |
|---|---|---|---|
| `EnterpriseService` | `queryEnterprises(EnterpriseQuery)` | infrastructure | 必须 `TenantContext.currentTenantId()` 追加租户条件；禁止接收外部 SQL 片段 |
| `AlarmService` | `queryAlarms(AlarmQuery)` | infrastructure | 同上，强制租户隔离 |
| `StatisticsService` | `computeStatistics(StatisticsQuery)` | infrastructure | 聚合全部在 DB/Java 侧完成，LLM 不参与计算 |
| `KnowledgeSearchService` | `search(KnowledgeQuery)` | rag | 向量检索按 tenantId 元数据过滤，禁止跨租户检索 |

### 5.2 enterprise-ai-agent-core —— 核心契约层

**定位**：执行上下文、租户/用户上下文、权限契约、Prompt 注册中心、异常体系、观测契约。依赖 domain + `spring-boot-starter` + `micrometer-core`。

#### 5.2.1 `TenantContext` —— 多租户铁律的核心载体

线程绑定持有器（`final` 工具类）：

```java
private static final InheritableThreadLocal<UserContext> HOLDER = new InheritableThreadLocal<>();
```

- **为什么是 InheritableThreadLocal 而非普通 ThreadLocal**（Phase 12 实测发现，架构级）：Embabel 1.5 的 `AgentPlatform.start` 把整个进程经 `Asyncer.async` 提交执行；Java 21 下 asyncer = `Executors.newThreadPerTaskExecutor(embabel-virtual 虚拟线程工厂)`，SIMPLE/CONCURRENT 两种模式的 Action **都不在请求线程上跑**，普通 ThreadLocal 全部丢失（症状：`require()` 抛 "No authenticated user context bound to current thread"）。每任务新建线程 ⇒ 子线程创建时自动继承快照；线程不复用 ⇒ 无线程池脏读风险。
- `UserContext` 必须为**不可变 record**（引用共享无变异风险）。
- API：`set(UserContext)` / `get()`（可 null）/ `require()`（无上下文抛 `AgentException(PERMISSION_DENIED)`）/ `currentTenantId()` / `clear()`。
- 生命周期约定：鉴权过滤器在请求入口 `set`，请求结束（finally）必须 `clear`；**数据层与 RAG 层一律通过本类获取 tenantId，禁止从用户输入或 LLM 输出取租户**。

#### 5.2.2 `UserContext` —— 不可变身份快照

record：`userId, tenantId, username, roles(Set), permissions(Set)`；`hasPermission(p)` / `hasRole(r)`（null 安全）。由鉴权过滤器构建，随线程传播。

#### 5.2.3 `AgentExecutionContext` —— 单次执行的追踪上下文

字段：`executionId`（UUID，**日志/审计/指标/Trace 的统一关联键**）、`sessionId`、`user`、`request`、`startTime`、`currentIntent`（识别后回填）、`currentAction`、`actionResults`/`toolResults`（LinkedHashMap）、`status`（RUNNING→SUCCEEDED/FAILED）、`errorMessage`。

- 工厂：`AgentExecutionContext.begin(user, request)`（绑定当前线程 **InheritableThreadLocal**——Phase 13 与 TenantContext 统一选型，理由同上：Action 线程需读取 executionId/sessionId，历史加载 Action 与审计事件监听器均依赖此行为）
- 状态推进：`setCurrentIntent` / `setCurrentAction` / `recordActionResult` / `recordToolResult` / `markSucceeded` / `markFailed`
- `current()` / `clear()` 存取清理

#### 5.2.4 异常体系

`AgentErrorCode`（7 类，每项含用户文案 + retryable 标志）：

| 错误码 | 用户文案 | retryable |
|---|---|---|
| `LLM_ERROR` | AI 服务暂时不可用，请稍后重试 | ✅ |
| `TOOL_ERROR` | 业务查询暂时不可用，请稍后重试 | ✅ |
| `VALIDATION_ERROR` | 请求参数不合法，请检查后重试 | ❌ |
| `PERMISSION_DENIED` | 您没有执行此操作的权限 | ❌ |
| `RAG_ERROR` | 知识库检索暂时不可用，请稍后重试 | ✅ |
| `PLANNING_ERROR` | 暂时无法理解您的请求，请换一种说法试试 | ❌ |
| `BUSINESS_ERROR` | 业务处理失败，请稍后重试 | ✅ |

`AgentException extends RuntimeException`：持有 `final AgentErrorCode code`；对外只暴露 `code.getUserMessage()` / `code.isRetryable()`，内部诊断信息只进日志/审计。（注：Prompt 模板缺失复用 `LLM_ERROR` 快速失败——配置错误归入 LLM 域。）

#### 5.2.5 `PromptRegistry` + `PromptVersionProperties` —— Prompt 版本管理

- **模板定位约定**：classpath `/prompts/{category}/{name}-v{version}.st`
- **占位符**：`{question}` 风格，用 `String.replace` 显式替换（**不引入模板引擎依赖**）；null 值替换为空串
- **版本解析**：调用方代码显式声明基线版本（如 `render("intent","intent-recognition",1,vars)`）→ 实际生效版本 = 配置 `agent.prompts.active-versions.{category/name}` 覆盖优先，否则基线版本
- **缓存**：`ConcurrentHashMap.computeIfAbsent`，key = `category/name-vN.st`
- **快速失败**：模板缺失/读取失败抛 `AgentException(LLM_ERROR)`（配置错误不允许静默降级）
- **灰度/回滚语义**：加 v2 文件 + 改一行配置 = 灰度；删掉配置行 = 回滚；模板不可变、永不原地修改

#### 5.2.6 其余契约接口

| 接口/类 | 职责 |
|---|---|
| `ConversationStore` | 会话短期记忆：`appendMessage(tenantId, sessionId, role, content)` / `recentMessages(tenantId, sessionId, limit)`；**key 天然带 tenantId，跨租户读取在存储层即不可达**；长期记忆（企业/人员画像）走 RAG 向量库，不属于本接口 |
| `AgentExecutionRecorder` | 审计观察者契约：`onExecutionStart` / `onActionComplete(executionId, actionName, outcomeType, latencyMs, error)` / `onExecutionEnd` |
| `ToolPermissionEvaluator` | 工具权限门控：`canUse(UserContext, toolName)` + `default filterAllowed(...)`；判断依据只能是后端权限数据，**未授权 = 不注册** |

### 5.3 enterprise-ai-agent-infrastructure —— 基础设施实现

**定位**：MySQL 持久化（JPA）、Redis 会话记忆、审计落库。依赖 core + domain + `spring-boot-starter-data-jpa` + `spring-boot-starter-data-redis` + `mysql-connector-j`。

#### 5.3.1 `MySqlAgentExecutionRecorder` —— 审计落库（实现 core 的 `AgentExecutionRecorder`）

- **核心契约：审计失败绝不影响主流程**——三个方法均 `@Transactional` 且整体 try/catch，异常只记 WARN 日志
- `onExecutionStart`：先落一条 `RUNNING` 主记录（executionId/sessionId/tenantId/userId/userInput/startTime）
- `onActionComplete`：每个 Action 落明细（名称/产出类型/latencyMs/error）
- `onExecutionEnd`：`findById` 回填主记录的 intent、终态、errorMessage、endTime、durationMs
- 写频度低（每次执行 2~N 次插入），同步写足够；后续可平滑切 `@Async`（接口不变）

#### 5.3.2 `RedisConversationStore` —— 会话记忆（实现 core 的 `ConversationStore`）

基于 `StringRedisTemplate` 的 **Redis List**：

- Key：`conv:{tenantId}:{sessionId}`（租户隔离在 key 层面成立）
- 元素：纯字符串 `role|content`（如 `user|你好`），无 JSON 序列化开销；**该编码是 Redis 实现私有细节**——`recentMessages` 返回类型化的 `List<ConversationHistory.ConversationMessage>`（core 契约不暴露存储格式，首次 `|` 分割，内容中的 `|` 不受影响，畸形行跳过并 WARN）
- 写入：`rightPush` 追加 → `expire` 刷新 **7 天滑动 TTL** → `size` 超过 **100 条**则 `trim(-100, -1)` 只保留最近 100 条（防长会话无界增长）
- 单条内容截断 **2000 字符**（null 置空串）
- 读取：`range(key, -limit, -1)` 取尾部 limit 条，时间正序；limit ≤ 0 返回空列表

#### 5.3.3 JPA 实体与 Repository（4 + 4，DDL 见 §7.1）

- `EnterpriseRepository` / `AlarmRepository`：`JpaRepository` + `JpaSpecificationExecutor`——动态条件全部走 Specification（Criteria API），零硬编码查询
- `AlarmRepository` 另有 **7 个 JPQL 聚合查询**（无原生 SQL），公共条件均为 `a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime`：
  - `countTotalAlarms` / `countDistinctEnterprises`（总量）
  - `countGroupByEnterprise` / `countGroupByAlarmType` / `countGroupByAlarmLevel`（分组，均 `order by count(a) desc`）
  - `countGroupByDay` / `countDistinctEnterpriseGroupByDay`（"按天"分组用 `substring(cast(occurredAt as string), 1, 10)` 截取日期前缀）
- 接口约定：startTime/endTime 由服务层兜底为极值（参数永不为 null）；分组返回 `[dimension, count]` 元组按 count 降序

#### 5.3.4 三个 ServiceImpl（强制租户过滤）

共同点：`@Service @Transactional(readOnly = true)`；**入口第一步 `TenantContext.require()`**（tenantId 一律来自鉴权上下文，绝不来自查询参数或 LLM 输出）。

**`EnterpriseServiceImpl.queryEnterprises`**（Specification 动态拼装）：

- 恒定条件：`tenantId = user.tenantId()`
- `nameKeyword` 非空 → `like(name, "%" + escapeLike(kw) + "%")`；`city`/`industry` 非空 → 等值；`minRegisteredCapitalWanYuan` 非空 → `>=`
- 先 `count(spec)` 取总数，再 `findAll(spec, PageRequest.of(0, limit, Sort.by(ASC, "id")))`
- 返回 `EnterpriseResult(summaries, total, total > size)`（truncated 如实标记）
- `normalizeLimit`：null 或 ≤0 → 缺省 **50**；上限 **200**（收敛到 [1,200]）
- `escapeLike`：转义 `\` `%` `_`（防 LIKE 通配符注入，被 AlarmServiceImpl 复用）

**`AlarmServiceImpl.queryAlarms`**：

- 恒定 `tenantId`；`startTime`/`endTime` → **时间闭区间**；`enterpriseName` → 模糊（复用 escapeLike）；`alarmType`/`alarmLevel` → **等值**（等级是闭集枚举）
- 排序 `occurredAt DESC`；返回 `AlarmResult(records, total, truncated)`

**`StatisticsServiceImpl.computeStatistics`**（聚合全部在 DB 完成）：

- 缺省归一：aggregationType null → `COUNT`；metric null → `ALARM_COUNT`；时间缺省兜底 `1970-01-01T00:00 ~ 9999-12-31T23:59:59`（保证 Repository 参数非 null）
- 分派矩阵：`groupBy=null` → 总量单行（dimension="总计"）；`ENTERPRISE`/`ALARM_TYPE`/`ALARM_LEVEL` → 对应分组查询；`DAY` → 按天分组
- 排序：MIN 升序，MAX/COUNT 降序；**MAX 且未指定 topN 时缺省 topN=1**；topN 截断 `subList(0, topN)`
- **快速失败**：`ENTERPRISE_COUNT` 搭配 `ALARM_TYPE`/`ALARM_LEVEL` 分组 → `AgentException(VALIDATION_ERROR)`（口径非法组合不进 LLM）

### 5.4 enterprise-ai-agent-tool —— 工具层与权限门控

**定位**：4 个 Spring AI `@Tool`（LLM 自主工具调用通道）+ 配置驱动权限评估 + 每请求过滤的注册中心。依赖 core + domain + `spring-ai-client-chat`。

#### 5.4.1 四个工具（`@Component` + 构造注入领域 Service，**绝不暴露 Repository/DataSource/SQL**）

| 工具类 | @Tool name | 入参 | 返回 | 所需权限点 |
|---|---|---|---|---|
| `EnterpriseTool` | `queryEnterprises` | `EnterpriseQuery` | `EnterpriseResult` | `enterprise:query` |
| `AlarmTool` | `queryAlarms` | `AlarmQuery` | `AlarmResult` | `alarm:query` |
| `StatisticsTool` | `computeStatistics` | `StatisticsQuery` | `StatisticsResult` | `statistics:query` |
| `KnowledgeTool` | `searchKnowledge` | `KnowledgeQuery` | `KnowledgeResult` | `knowledge:search` |

每个方法标 `@Tool(name=…, description=…)`（description 为 LLM 可读的调用契约 text block），参数标 `@ToolParam`；调用 Service 后经 `ToolUsage.record(TOOL_NAME, result)` 把结果写入 `AgentExecutionContext.toolResults`（审计可见）。

#### 5.4.2 `ConfigurationBasedToolPermissionEvaluator` —— 权限判定

```java
String required = properties.toolPermissions().get(toolName);
if (required == null || required.isBlank())
    return properties.defaultPolicy() == Policy.ALLOW;   // 未登记工具：default-policy（缺省 DENY）
if ("*".equals(required)) return true;                  // 配置值 "*" = 对所有人放行（内部演示工具）
return user.hasPermission(required) || user.hasPermission("*");  // 用户持有所需权限点或 "*" 通配
```

- 配置绑定一次即不可变（record + `@ConfigurationProperties`），O(1) Map 查找，无需缓存
- 企业内可替换为 RBAC 表/权限中心实现，core 接口不变

#### 5.4.3 `PermissionAwareToolRegistry` —— 每请求过滤的注册中心

```java
public List<ToolCallback> callbacksForCurrentUser() {
    UserContext user = TenantContext.require();          // 强制已鉴权
    return Arrays.stream(ToolCallbacks.from(toolObjects.toArray()))
            .filter(cb -> permissionEvaluator.canUse(user, cb.getToolDefinition().name()))
            .toList();
}
```

- 机制：**不做调用期拦截包装，而是在构造回调列表时按当前用户过滤**——`ToolCallbacks.from(...)` 把 `@Tool` POJO 转为 `ToolCallback`，再按工具名逐个过 `canUse`
- **过滤发生在每次调用（每请求）而非启动时**：同一 JVM 内不同用户得到不同工具集；无权限的工具不注册，LLM 完全看不见
- 用法：Spring AI ChatClient 场景 `chatClient.prompt().tools(registry.callbacksForCurrentUser())`
- **防御纵深**：即使工具被误暴露，Service 层仍从 TenantContext 强制注入 tenantId，LLM 指定的条件无法越过租户隔离
- 与主流程的关系：本项目主流程为确定性 Action（不依赖 LLM 自主选工具），工具通道供 Phase 6 的 ChatClient Tool Calling 场景使用

### 5.5 enterprise-ai-agent-rag —— 检索增强

**定位**：知识摄入（切块+元数据）与租户隔离的向量检索。依赖 core + domain + `spring-ai-starter-vector-store-elasticsearch`。

#### 5.5.1 `KnowledgeIngestionService` —— 摄入

`ingest(title, content, source, documentType, visibility)`：

1. `TenantContext.require()` —— **tenantId 只来自鉴权上下文，调用方不可伪造**（专项单测锁定）
2. `chunk(content)`：按 `\n` 段落聚合至约 **800 字符/块**（超长落盘再开新块，段落间以换行连接，trim 后落盘）；空内容则原文单块。生产可替换为 spring-ai-commons 的 `TokenTextSplitter`（接口不变）
3. 每个 chunk 构造元数据（**与检索侧过滤条件一一对应**）：`tenantId` / `documentType`（空 → `"general"`）/ `title` / `source` / `visibility`（**白名单归一**：非 `restricted` 一律 `public`）/ `chunk` 序号
4. `vectorStore.add(documents)` 批量写入——**代码不显式调用 EmbeddingModel**，向量化由 VectorStore 内部用自动配置的 EmbeddingModel 完成

#### 5.5.2 `KnowledgeSearchServiceImpl` —— 检索（实现 domain 的 `KnowledgeSearchService`）

```java
SearchRequest.builder().query(question).topK(topK).filterExpression(filter).build()
vectorStore.similaritySearch(request)
```

- **过滤表达式三层硬过滤**（`buildFilter`，包私有静态方法可直接单测）：

```
tenantId == '<sanitize(tenantId)>'
[&& documentType == '<sanitize(documentType)>']   // documentType 非空时
[&& visibility == 'public']                       // 用户无 knowledge:restricted 权限时强制追加
```

- **防注入**：`sanitize` = `replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "")`——只保留字母/数字/下划线/中文/点/连字符，引号/空格/等号/竖线等全部剥离，阻断 filterExpression 表达式注入（专项单测：`"tenant-a' || '1'=='1"` → `"tenant-a11"`）
- **纯向量语义检索**：无关键词/混合检索分支，**未设相似度阈值**（走 Spring AI 默认），仅 topK 截断（缺省 5，上限 10）
- 结果映射：`KnowledgeSnippet(title, content=doc.getText(), source, score)`（score 空时 0.0）

### 5.6 enterprise-ai-agent-agent —— Agent 编排（Embabel）

单一类 `EnterpriseAssistantAgent`（详见 §6），依赖 4 个领域服务 + `ConversationStore` + `PromptRegistry`，**无实例可变状态，Spring 单例线程安全**。

### 5.7 enterprise-ai-agent-api —— REST 接入层

#### 5.7.1 `AgentChatController` —— 唯一业务端点

`POST /api/agent/chat`，同步阻塞 JSON（**无 SSE/流式**）：

- 入参：`@Valid ChatRequest`；返回：`ChatResponse`
- 流程：sessionId 兜底生成 → `AgentExecutionContext.begin()` + executionId 挂请求属性 → `recorder.onExecutionStart()` → `AgentInvocation.create(agentPlatform, AgentResponse.class).invoke(new UserInput(message))` → 响应 null 抛 `PLANNING_ERROR`（双路径兜底下理论上不发生）→ 成功：`markSucceeded` + 写两条会话记忆（user/assistant，按 tenantId+sessionId 隔离）→ `finally`：`onExecutionEnd` + `AgentExecutionContext.clear()`
- 其他异常统一包装 `AgentException(BUSINESS_ERROR)` 后重抛

#### 5.7.2 DTO

| Record | 字段（含校验） |
|---|---|
| `ChatRequest` | `message`：`@NotBlank` + `@Size(max=4000)`；`sessionId`：`@Size(max=64)`，可空（空则新会话） |
| `ChatResponse` | `executionId`（追踪 ID）、`answer`（中文 Markdown）、`intent`（枚举名，可 null）、`status`（SUCCEEDED/FAILED） |
| `ErrorResponse` | `executionId`、`code`（7 类错误码）、`userMessage`（用户可读）、`retryable` |

#### 5.7.3 `AuthenticationFilter` —— 信任头认证

- **模式**：Trusted Headers（**非 API Key**）。设计前提：部署于 API 网关之后，网关完成 JWT 校验并注入头，本服务内网隔离、不重复验签。
- 头契约：`X-User-Id`（必填）、`X-Tenant-Id`（必填，缺失/空白即 401）、`X-User-Name`（可选，缺省取 userId）、`X-Roles` / `X-Permissions`（可选，逗号分隔，解析为小写化不可变 Set）。
- `@Component @Order(1)`，`OncePerRequestFilter`；**保护范围仅 `/api/**`**，actuator/静态资源放行。
- `try/finally` 中 `TenantContext.clear()`，保证线程池复用不串租户。
- 401 响应体（过滤器阶段无 executionId）：`{"code":"PERMISSION_DENIED","userMessage":"未认证：缺少 X-User-Id / X-Tenant-Id 请求头"}`。

#### 5.7.4 `GlobalExceptionHandler` —— 异常统一收口

`@RestControllerAdvice`，统一返回 `ErrorResponse`，**绝不透出堆栈/SQL/内部类名**：

| 异常 | HTTP | code | retryable |
|---|---|---|---|
| `AgentException` | PERMISSION_DENIED→403、VALIDATION_ERROR→400、其余→500 | `e.getCode().name()` | `e.isRetryable()` |
| Embabel `ActionException` | 500 | cause 链命中 LLM 签名→`LLM_ERROR`；否则 `TOOL_ERROR` | LLM 签名：`TransientAiException`→true 其余 false；无签名时 `instanceof ActionException.Transient` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | false（字段错误详情仅进日志） |
| `HttpMessageNotReadableException` | 400 | `VALIDATION_ERROR` | false（请求体缺失/JSON 畸形，Phase 13 修复） |
| `Exception` 兜底 | 500 | cause 链命中 LLM 签名→`LLM_ERROR`；否则 `BUSINESS_ERROR` | 仅 `TransientAiException` 签名→true |

> **LLM 失败签名**（Phase 13，§14.3-a 修复）：沿 cause 链探测 `org.springframework.ai.retry.TransientAiException` / `NonTransientAiException`（Spring AI HTTP 层）与 `com.openai.*`（openai-java SDK，包前缀匹配）——`AgentInvocation.invoke` 经 `CompletableFuture.get()` 上抛的失败均为 `ExecutionException` 包装，签名在链上而非顶层异常。

executionId 优先取请求属性，回退 `AgentExecutionContext.current()`。

### 5.8 enterprise-ai-agent-application —— 启动与配置聚合

- 主类 `EnterpriseAiAgentApplication` 四个关键注解（Boot 4 必备，否则其他模块 bean 全丢）：
  - `@SpringBootApplication(scanBasePackages = "com.enterpriseai")` —— 主类在 `.application` 子包，默认只扫本包
  - `@ConfigurationPropertiesScan(basePackages = "com.enterpriseai")` —— 注册 `agent.tools.permission.*` 与 `agent.prompts.*`
  - `@EnableJpaRepositories(basePackages = "com.enterpriseai.infra.persistence.repository")` —— JPA 仓库扫描与组件扫描相互独立
  - `@EntityScan(basePackages = "com.enterpriseai.infra.persistence.entity")` —— Boot 4 中该注解已迁包至 `org.springframework.boot.persistence.autoconfigure`
- 四个配置类：
  - `MetricsAgentExecutionRecorder`（`@Primary`）：装饰 `MySqlAgentExecutionRecorder`，叠加 Micrometer 指标（详见 §10.2）
  - `SpringAiConfig`：仅装配 `ChatClient`；`ChatModel`/`EmbeddingModel` 由 `spring-ai-starter-model-openai` 自动配置
  - `TenantContextAgentProcessCallback`：实现 Embabel `AgentProcessCallback`，无实例态设计（快照存 `ConcurrentHashMap<AgentProcess, UserContext>`，完成即移除防泄漏）——把请求线程的租户上下文传播到 Action 线程；由于 TenantContext 已是 InheritableThreadLocal，SIMPLE/CONCURRENT 下均非必需，仅当未来把 Action 调度到非派生线程时按官方钩子补齐
  - `AgentAuditEventListener`（Phase 13 补线）：实现 Embabel `AgenticEventListener` SPI（容器内全部实现 Bean 被平台 `eventListener(listeners)` 自动组播），把 `ActionExecutionResultEvent` 翻译为 `recorder.onActionComplete(...)`——此前该方法无生产调用方，`agent_action_execution` 明细与 `agent.action` Timer 实际从未触发；executionId 取自 `AgentExecutionContext.current()`（依赖 InheritableThreadLocal），审计失败只 WARN 不影响主流程

---

## 6. Agent 执行流程详解

### 6.1 规划模型：GOAP（Goal-Oriented Action Planning）

Embabel 默认规划器（`AStarGoapPlanner`）。核心机制：

- **黑板（Blackboard）**：强类型对象的容器。每个 `@Action` 声明自己的输入（方法参数类型）与输出（返回类型），规划器按**类型匹配**自动串联数据流——例如返回 `EnterpriseQuery` 的 Action 产出后，消费 `EnterpriseQuery` 的 Action 变为可用。
- **命名条件（`@Condition`）**：布尔方法（方法名即条件名），供 `@Action(pre=...)` 引用做分支门控。
- **Goal（`@AchievesGoal`）**：产出 `AgentResponse` 即达成目标；本项目两个收尾 Action 共享同一 Goal，由条件决定走哪条路径。
- **⚠️ GOAP 规划器铁律**（Phase 10 集成测试抓出的真实架构级 bug）：`@Action(pre={"命名条件"})` 中的命名条件**必须被某个 Action 在 `post={...}` 中声明**，否则 `isGoalReachable` 可达性检查判死 → 规划返回 null → `invoke()` 得 null。范式：产出分支判据的 Action（意图识别）在 `post` 中乐观声明全部可能条件，运行时按黑板实况重规划收敛到真实分支。

### 6.2 七阶段认知流水线

```
UserInput("那深圳呢")        ← 追问示例（多轮场景）
  │
  ▼ ⓪历史加载（纯 Java，Phase 13）：多轮上下文来源
loadConversationHistory    ← TenantContext.get() + AgentExecutionContext.current().sessionId()
  │                          → conversationStore.recentMessages(tenant, session, 10)
  │                          → ConversationHistory（上下文缺失/存储异常降级为空历史）
  ▼ ConversationHistory{[user: 帮我查北京的企业, assistant: 已找到…]}
  │
  ▼ ①意图识别（LLM，单一认知任务：分类；携带历史理解追问）
recognizeIntent ── post={intentIsEnterpriseQuery, intentIsAlarmQuery,
  │                  intentIsStatisticsQuery, intentIsKnowledgeQa, intentIsUnknown}
  │           （GOAP 乐观后置声明：建立分支可达性，运行时按实况收敛）
  ▼ IntentResult{ENTERPRISE_QUERY, 0.95, "..."}
  │
  ├── 5 个 @Condition（纯 Java）依据 intent 锁定唯一分支
  │
  ▼ ②参数提取（LLM，按意图选分支；未提供的字段必须为 null；历史供条件继承）
extractEnterpriseQuery / extractAlarmQuery /      ← pre={intentIsXxxQuery}
extractStatisticsQuery / extractKnowledgeQuery      （alarm/statistics 注入 current_time
  │                                                  供 LLM 换算相对时间）
  ▼ EnterpriseQuery{city=深圳, ...}   ← 强类型 record（结构化输出，city 继承自上文话题）
  │
  ▼ ③业务查询（纯 Java：Service → Repository → DB，LLM 不参与）
queryEnterprises / queryAlarms / computeStatistics / searchKnowledge
  │                                                  （RAG：ES 向量检索 + 租户过滤）
  ▼ EnterpriseResult{enterprises, total, truncated}
  │
  ▼ ④结果归一化（纯 Java）：四条分支统一为 AnalysisData
prepareEnterpriseData / prepareAlarmData /
prepareStatisticsData / prepareKnowledgeData          ← 截断至 MAX_ANALYSIS_ROWS=20 行，
  │                                                  total/truncated 如实传递
  ▼ AnalysisData{intent, dataPresent, totalRecords, truncated, sourceDescription, rows}
  │
  ▼ ⑤数据分析（LLM：只允许分析给定数据，禁止编造）
analyzeData ──→ AnalysisResult{summary, findings[], risks[], recommendations[]}
  │
  ▼ ⑥最终应答（LLM）与未知意图兜底 —— 二者共享同一 @AchievesGoal
composeResponse（pre: 分析+意图+原始问题）─→ FinalAnswer{answer} ─→ AgentResponse
unknownIntentResponse（pre={intentIsUnknown}）─→ AgentResponse{UNKNOWN}
```

要点：

- **LLM 只出现在 4 类认知节点**：意图识别、参数提取、数据分析、最终应答；⓪③④ 全是纯 Java，可单测、可审计、零 token。
- **`FinalAnswer` 是 Agent 内部 record**：LLM 仅产出 answer 文本，`intent` 由 Java 侧确定性回填，避免模型复读出错。
- **上下文保护**：`MAX_ANALYSIS_ROWS = 20` 行截断 + 知识片段内容截断 500 字，保护上下文窗口与 token 成本；`total`/`truncated` 如实传递，分析 Prompt 据此在数据缺失时明确说"未查询到数据"。
- **耗时参考**（Kimi kimi-k2-0905-preview 推理模型实测）：单请求 ~117s，4 次 LLM 调用（意图/提取/分析/应答）各一次；换非推理模型会显著更快。

---

## 7. 数据模型

### 7.1 MySQL 表结构（schema.sql，4 张表，InnoDB + utf8mb4）

> 初始化方式：手动执行 `resources/db/schema.sql` + `data.sql`（生产建议纳入 Flyway 管理）。

#### `enterprise` —— 企业信息表（多租户）

| 列 | 类型 | 约束/默认 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| tenant_id | VARCHAR(64) | NOT NULL | 租户 ID |
| name | VARCHAR(128) | NOT NULL | 企业名称 |
| city | VARCHAR(32) | | 城市 |
| industry | VARCHAR(32) | | 行业 |
| registered_capital_wan | DECIMAL(15,2) | | 注册资本（万元） |
| legal_person | VARCHAR(32) | | 法人 |
| status | VARCHAR(16) | | ACTIVE / SUSPENDED / CANCELLED |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_enterprise_tenant(tenant_id)`、`idx_enterprise_tenant_name(tenant_id,name)`、`idx_enterprise_tenant_city(tenant_id,city)` —— **所有索引都以 tenant_id 打头**。

#### `alarm` —— 企业告警表（多租户）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK | |
| tenant_id | VARCHAR(64) | NOT NULL | |
| enterprise_id | BIGINT | | 关联企业 |
| enterprise_name | VARCHAR(128) | | 企业名称（冗余，免联表） |
| alarm_type | VARCHAR(32) | | 环保超标/安全生产/证照过期/税务异常/数据合规 |
| alarm_level | VARCHAR(16) | | CRITICAL/HIGH/MEDIUM/LOW |
| occurred_at | DATETIME | NOT NULL | 发生时间 |
| message | VARCHAR(512) | | 告警内容 |
| status | VARCHAR(16) | | OPEN/PROCESSING/RESOLVED |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_alarm_tenant_time(tenant_id,occurred_at)`、`idx_alarm_tenant_enterprise(tenant_id,enterprise_name)`、`idx_alarm_tenant_level(tenant_id,alarm_level)`。

#### `agent_execution` —— Agent 执行审计主表（PK = execution_id）

| 列 | 类型 | 说明 |
|---|---|---|
| execution_id | VARCHAR(36) | PK，UUID，全链路追踪键 |
| session_id | VARCHAR(64) | 会话 |
| tenant_id / user_id | VARCHAR(64) | 归属 |
| user_input | TEXT | 用户输入 |
| intent | VARCHAR(32) | 识别的意图 |
| status | VARCHAR(16) | RUNNING / SUCCEEDED / FAILED |
| error_message | TEXT | 错误信息（内部细节，不外泄） |
| start_time / end_time | DATETIME | |
| duration_ms | BIGINT | 端到端耗时 |

索引：`idx_agent_exec_tenant_time(tenant_id,start_time)`、`idx_agent_exec_session(session_id)`。

#### `agent_action_execution` —— Action 审计明细

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| execution_id | VARCHAR(36) | 关联主表 |
| action_name | VARCHAR(64) | Action 方法名 |
| outcome_type | VARCHAR(128) | 产出类型 |
| latency_ms | BIGINT | 单 Action 耗时 |
| error | TEXT | 错误信息 |
| created_at | DATETIME | |

索引：`idx_agent_action_exec_id(execution_id)`。

### 7.2 种子数据（data.sql，21 行）

- **企业 10 行**：tenant-a 8 家（北京/上海/深圳/广州/杭州，覆盖科技/制造/环保/物流/医疗，注册资本 300 万 ~ 3 亿，含 1 家 SUSPENDED）；tenant-b 2 家——**刻意包含与 tenant-a 同名同法人"北京中科智联科技有限公司"**，用于验证租户隔离（双方各自只见自己那行）。
- **告警 11 行**：tenant-a 10 条（4 条 `NOW()` 当天 + 6 条 `DATE_SUB(NOW(), INTERVAL 1~6 DAY)` 近 7 天——保证"今天/最近 7 天"类查询有数据；覆盖四种等级、三种状态、五种类型）；tenant-b 1 条。

### 7.3 Elasticsearch 向量索引

- 索引名 `enterprise-knowledge`，`initialize-schema: true` 首次启动自动创建
- **维度 1024**（⚠️ Kimi 网关对 `text-embedding-3-small` 请求名实测返回 1024 维；官方 OpenAI 为 1536 维。**换 embedding 网关必须同步改 `spring.ai.vectorstore.elasticsearch.dimensions` 并删索引重建**，否则维度不匹配检索失败）
- 每个向量带元数据：`tenantId`（硬隔离）、`documentType`（manual/policy/faq…可选过滤）、`visibility`（public/restricted，restricted 需 `knowledge:restricted` 权限）
- 检索口径：**纯向量语义检索**（无关键词/混合检索），未设相似度阈值，仅 topK 截断（缺省 5，上限 10）；过滤表达式构造与防注入见 §5.5.2

### 7.4 Redis 会话记忆

- Key：`conv:{tenantId}:{sessionId}`（Redis List）—— **租户在 key 中，跨租户读取在存储层即不可达**
- 元素为纯字符串 `role|content`（如 `user|你好`）
- 7 天**滑动 TTL**（每次写入刷新）；单会话上限 100 条（超出 `trim` 保留最近 100 条）；单条内容截断 2000 字符
- role 取值 `user` / `assistant`；查询返回时间正序（实现细节见 §5.3.2）

---

## 8. Prompt 工程与版本管理

### 8.1 目录即认知分层

```
resources/prompts/
├── intent/      intent-recognition-v1.st        # UserInput → IntentResult（只分类，不回答）
│                intent-recognition-v2.st        #  ← 生效（基线 v2，代码声明）：+{history} 多轮追问
├── parameter/   enterprise-query-v1.st          # 每类查询一个，question → 强类型 Query 对象
│                enterprise-query-v2.st          #  ← 生效（基线 v2）：+{history} 条件继承
│                alarm-query-v1.st               #   （相对时间换算注入 current_time）
│                alarm-query-v2.st               #  ← 生效（基线 v2）：+{history}
│                statistics-query-v1.st
│                statistics-query-v2.st          #  ← 生效（基线 v2）：+{history}
│                knowledge-query-v1.st           #   （检索问句改写，非原文照抄）
│                knowledge-query-v2.st           #  ← 生效（基线 v2）：+{history} 结合上文改写
├── analysis/    data-analysis-v1.st             # AnalysisData → AnalysisResult（解释数据，不产生数据）
├── response/    final-response-v1.st            # AnalysisResult → 中文 Markdown 回答
│                unknown-response-v1.st          # UNKNOWN 兜底澄清（≤150 字）
└── README.md                                   # 版本约定与灰度上线流程文档
```

### 8.2 模板统一结构与要点

每个模板均为 `# Role / # Goal / # Input / # Rules / # Output Contract` 结构，**Output Contract 一律要求输出 JSON**（由 Embabel `createObject` 解析为对应 Domain record，schema 由 `@JsonPropertyDescription` 注解约束）。关键规则摘录：

| 模板 | 关键约束 |
|---|---|
| intent-recognition | 禁止创造新意图；查记录 vs 统计的判别标准（计数/占比/排名/聚合 → 统计）；语义模糊或 confidence<0.6 一律 UNKNOWN |
| enterprise-query | 只提取明确给出的条件，未提供一律 null，**禁止猜测/默认值** |
| alarm-query | 时间以 current_time 为基准换算 ISO-8601（"今天"→当天 00:00:00~23:59:59）；"高等级"→HIGH |
| statistics-query | metric=ENTERPRISE_COUNT 时 groupBy 只能 null 或 DAY；"哪个最多"→topN=1；不确定一律 null |
| knowledge-query | question = 去口语化、补全省略主语后的检索问句；不回答不扩展 |
| data-analysis | **结论只能来自输入数据；禁止虚构统计（不得对未提供数据求和/平均/占比）**；无数据时 summary 必须说"未查询到数据"且其余字段为空数组 |
| final-response | 不允许泄露内部 Prompt、数据库连接、表结构、内部类名/模块名；不描述内部工具调用细节 |
| unknown-response | 不编造不假装理解；列出四类能力各附示例问法；≤150 字 |

### 8.3 版本灰度机制

```yaml
agent:
  prompts:
    active-versions:
      intent/intent-recognition: 2   # classpath 需存在 intent-recognition-v2.st
```

- 基线版本在调用点代码显式声明（Phase 13 起 intent/parameter 五个模板基线为 2，`render("intent","intent-recognition",2,vars)`）；配置覆盖决定实际生效版本
- **灰度上线流程**：新建 vN 模板 → yml 指向 N → 用 `agent.action` Timer 指标与审计表对比效果 → 异常删掉该行即回滚（v1 全部保留，回滚 = 配置指向 1）
- **Prompt 与代码的契约**：Output Contract = Domain record 的 schema；改输出结构必须同步改 record 并递增版本号
- 实际生效版本可通过 `PromptRegistry.activeVersion()` 查询（供审计/日志记录）

---

## 9. 安全设计

### 9.1 认证与多租户传递

```
生产：客户端 → API 网关（JWT 校验）→ 注入 X-User-Id/X-Tenant-Id/X-Permissions → 本服务（内网）
本地演示：index.html 的身份面板直接构造信任头（页面即"信任头的本地来源"，后端逻辑无任何旁路）
```

- 只有 `/api/**` 需要身份；`/actuator/**` 与静态资源放行
- `AuthenticationFilter` 是唯一的 TenantContext 写入点之一（另一个是 AgentProcessCallback 的跨线程传播）
- 权限在过滤器确定后，工具过滤 / RAG 过滤 / Service 查询全部基于此上下文，**LLM 全程不参与权限判定**

### 9.2 租户隔离三道防线（实测验证）

1. **上下文防线**：`TenantContext.require()` 无上下文快速失败（PERMISSION_DENIED）；InheritableThreadLocal 保证 Embabel 虚拟线程正确继承
2. **查询防线**：每个 ServiceImpl 查询显式追加 tenantId 条件（Specification 动态拼装，审计可见，不用 Hibernate filter/拦截器等"魔法"）
3. **存储防线**：会话记忆 key 带租户；RAG filterExpression 强制 `tenantId ==` 等值条件

**实证**：同一问题"查询华东地区有哪些企业"，tenant-a 看到 8 家、tenant-b 只见自己 2 家，双方共有的同名企业各自只见自己那行。

### 9.3 工具权限模型（默认 DENY）

```yaml
agent:
  tools:
    permission:
      default-policy: DENY          # 未列出的工具一律拒绝，新增工具必须显式授权
      tool-permissions:
        queryEnterprises:  enterprise:query
        queryAlarms:       alarm:query
        computeStatistics: statistics:query
        searchKnowledge:   knowledge:search
```

- `ConfigurationBasedToolPermissionEvaluator`：基于配置判定 `canUse(user, toolName)`
- `PermissionAwareToolRegistry`：**每次调用时**按当前 UserContext 过滤可暴露的工具（非启动时快照），未授权工具不注册——LLM 根本看不见，杜绝越权诱导
- 5 个权限点：`enterprise:query` / `alarm:query` / `statistics:query` / `knowledge:search` / `knowledge:restricted`（最后一个控制 RAG 中 restricted 可见性文档的访问）

### 9.4 注入与信息泄露防护

- `escapeLike` 转义 LIKE 通配符（`%` `_` `\`），防通配符注入
- RAG filterExpression 构造前对注入字符清洗（有专项单测）
- 所有 SQL 条件来自强类型 Query record（Bean Validation 校验长度/范围），禁止外部 SQL 片段
- 对外错误响应只有 `{code, userMessage, retryable, executionId}`，堆栈/SQL/内部类名只进服务端日志
- index.html 的 Markdown 渲染：先 HTML 转义再仅支持 `**加粗**` 与 `` `行内代码` ``，无 innerHTML 注入面

---

## 10. 可观测性

### 10.1 统一追踪键：executionId

`AgentExecutionContext.begin()` 生成 UUID，贯穿：HTTP 响应字段 → 日志 → 审计表主键 → Micrometer tag（不，见下）→ 请求异常处理器回填。日志行另注入 `traceId/spanId`（MDC），与 executionId 双键关联。

### 10.2 Micrometer 指标（MetricsAgentExecutionRecorder，Prometheus 抓取）

| 指标 | 类型 | Tags | 说明 |
|---|---|---|---|
| `agent.execution` | Timer | `status`, `intent` | 端到端执行耗时（RUNNING/SUCCEEDED/FAILED × 意图，null 意图 → "none"） |
| `agent.action` | Timer | `action`, `outcome` | 单 Action 耗时（方法名 × 产出类型） |
| `agent.action.errors` | Counter | `action` | Action 错误计数（仅 error != null 时递增） |

**基数控制**：executionId / tenantId 不做 tag（无界基数）；租户级聚合走审计表 SQL。

### 10.3 审计落库（MySqlAgentExecutionRecorder）

装饰器模式：`MetricsAgentExecutionRecorder`（`@Primary`）→ 委托 MySQL 审计 + 叠加指标。生命周期：`onExecutionStart`（RUNNING 入库）→ `onActionComplete`（每个 Action 明细：名称/产出类型/耗时/错误）→ `onExecutionEnd`（终态 + duration）。**失败历史同样完整留痕**（Phase 12 两轮失败 + 最终成功的执行均在表中可查）。

> Phase 13 审计补线：`onActionComplete` 的触发源是 `AgentAuditEventListener`（Embabel `AgenticEventListener` SPI，见 §5.8）——此前该回调在生产代码中无调用方，Action 明细实际从未落库。

### 10.4 OTel Tracing 与日志

- Boot 4.1 原生 starter（`spring-boot-starter-opentelemetry` + `spring-boot-micrometer-tracing-opentelemetry`），OTLP 上报端点可配 `OTLP_ENDPOINT`
- 采样率 `management.tracing.sampling.probability: 1.0`（全采样，生产可调低）
- 日志 pattern：`%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]`
- 本地未起 OTel Collector 时会出现 OTLP 上报 ConnectException 日志——不影响业务功能，属预期噪音

---

## 11. 使用说明

### 11.1 环境要求

| 依赖 | 要求 |
|---|---|
| JDK | 21+ |
| Maven | 3.9+（或使用 IDE 内置） |
| Docker | 用于一键启动 MySQL 8.4 / Redis 7 / Elasticsearch 9.1.0 |
| LLM | 任意 OpenAI 兼容端点的 API Key（chat + embeddings 两类接口均需可用） |

### 11.2 快速开始（四步）

**① 启动中间件**

```bash
docker compose up -d
# MySQL(3306, root/root, 库 enterprise_agent) + Redis(6379) + ES 9.1(9200)
# 等待健康检查通过（docker compose ps 全部 healthy）
```

**② 初始化数据库表与种子数据**（容器内执行，或用客户端工具导入）

```bash
docker exec -i enterprise-agent-mysql mysql -uroot -proot \
  < enterprise-ai-agent-application/src/main/resources/db/schema.sql
docker exec -i enterprise-agent-mysql mysql -uroot -proot \
  < enterprise-ai-agent-application/src/main/resources/db/data.sql
```

**③ 配置 LLM 密钥并启动应用**

```bash
# PowerShell
$env:LLM_API_KEY="sk-xxx"
$env:LLM_BASE_URL="https://api.kimi.com/coding"    # 或任意 OpenAI 兼容端点
$env:LLM_CHAT_MODEL="kimi-k2-0905-preview"
$env:LLM_EMBEDDING_MODEL="text-embedding-3-small"

mvn spring-boot:run -pl enterprise-ai-agent-application
# 或打包运行：
mvn package -DskipTests
java -jar enterprise-ai-agent-application/target/enterprise-ai-agent-application-1.0.0-SNAPSHOT.jar
```

**④ 打开界面**

浏览器访问 `http://localhost:8080/` —— 单文件聊天界面（详见 §11.3）。

### 11.3 Web 界面（static/index.html）

本地演示用单文件聊天页（348 行，无框架纯原生 JS，随 Boot jar 托管在 `/`，与 API 同源无 CORS）：

- **对话区**：气泡消息、Ctrl/Cmd+Enter 发送、"正在思考…"动效（含秒数计时）、错误气泡（错误码 + userMessage + 可重试标记 + executionId）
- **身份面板**（本地演示专用；生产由网关注入信任头）：租户切换（tenant-a / tenant-b——即多租户隔离的可视化验证工具）、用户 ID、**5 个权限点复选框**（勾选结果拼成 `X-Permissions` 头，在后端真实生效：控制工具可见性与 RAG restricted 文档过滤）
- **会话管理**：sessionId 自动生成（crypto.randomUUID()）、一键新会话
- **可观测性外露**：每条回答的 meta 行显示意图徽章 + executionId + 耗时秒数
- **健康状态灯**：轮询 `GET /actuator/health`，绿点正常/红点不可达
- **示例问题**：4 个贴合种子数据的快捷按钮（🏢查企业 / 🔔查告警 / 📊数据统计 / 📚知识库问答）

### 11.4 REST API 调用

**对话端点**

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "X-User-Id: demo-user" \
  -H "X-Tenant-Id: tenant-a" \
  -H "X-Permissions: enterprise:query,alarm:query,statistics:query,knowledge:search" \
  -d '{"message": "查询华东地区有哪些企业", "sessionId": "e2e-test-001"}'
```

成功响应：

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "answer": "**华东地区企业（8 家）**\n\n- 上海数联信息技术有限公司：……",
  "intent": "ENTERPRISE_QUERY",
  "status": "SUCCEEDED"
}
```

错误响应（统一格式）：

```json
{
  "executionId": "…（可能为空，如过滤器阶段）",
  "code": "PERMISSION_DENIED",
  "userMessage": "未认证：缺少 X-User-Id / X-Tenant-Id 请求头",
  "retryable": false
}
```

**其他端点**：`GET /actuator/health`（健康）、`GET /actuator/prometheus`（指标）、`GET /actuator/metrics`（指标列表）、`GET /actuator/info`。

**头契约速查**

| Header | 必填 | 格式 |
|---|---|---|
| `X-User-Id` | ✅ | 任意字符串（≤64） |
| `X-Tenant-Id` | ✅ | 任意字符串（≤64） |
| `X-User-Name` | ❌ | 缺省取 User-Id |
| `X-Roles` | ❌ | 逗号分隔 |
| `X-Permissions` | ❌ | 逗号分隔，小写化解析 |

### 11.5 RAG 知识库灌入

`KnowledgeIngestionService`（rag 模块）负责文档摄入：

- **tenantId 只取自 `TenantContext`**（绝不取调用参数——防租户伪造，有专项单测）
- 切块（chunk）：按 `\n` 段落聚合至约 **800 字符/块** + 元数据写入：`tenantId` / `documentType` / `visibility` / `title` / `source` / `chunk` 序号，与检索端过滤条件一一对应
- `visibility` 规范化：非 `restricted` 一律存 `public`；restricted 文档检索时要求当前用户持有 `knowledge:restricted` 权限点
- 目前未暴露 REST 端点，通过编程调用（注入 `KnowledgeIngestionService`）；首次启动 ES 索引由 `initialize-schema: true` 自动创建

### 11.6 更换 LLM 提供商注意事项

| 通道 | 配置 | URL 拼法（**两通道写法分叉，勿合并**） |
|---|---|---|
| **Spring AI 通道**（EmbeddingModel / ChatClient / RAG / Tool） | `spring.ai.openai.base-url` | **不带** `/v1`（SDK 自动拼 `/v1/embeddings`） |
| **Embabel 通道**（Agent 的 LLM 调用） | `embabel.agent.platform.models.openai.base-url` | **必须带** `/v1`（Embabel 走官方 openai-java SDK，URL = baseUrl + `chat/completions` 固定相对路径） |

- 同一环境变量 `LLM_BASE_URL` 喂两个通道，但 yml 中写法分叉：`${LLM_BASE_URL}` vs `${LLM_BASE_URL}/v1`
- `embabel...models.openai.completions` / `embeddings-path` 是**废弃空操作**（Spring AI 2.0 时代不再生效，jar 内日志原文自证），路径必须烘进 base-url
- **embedding 维度**：换网关前实测维度并同步 `spring.ai.vectorstore.elasticsearch.dimensions`，**删 ES 索引重建**（Kimi 网关 1024 / 官方 OpenAI 1536）
- **推理模型**（如 kimi-k2-0905-preview）：reasoning 占 token，`max_tokens` 过小会 content 空——易误判为网关故障
- Embabel 无模型名配置属性：模型名由 openai-java SDK 默认值发出，Kimi 网关对任意模型名均接受并映射（其他网关需实测）

---

## 12. 配置参考

### 12.1 环境变量

| 变量 | 默认 | 用途 |
|---|---|---|
| `LLM_API_KEY` | （空） | LLM 网关密钥（未配置时对话返回 LLM_ERROR 但系统正常启动，审计留痕） |
| `LLM_BASE_URL` | `https://api.openai.com` | OpenAI 兼容端点（Spring AI 通道直接用；Embabel 通道 yml 追加 `/v1`） |
| `LLM_CHAT_MODEL` | `gpt-4o-mini` | Spring AI 通道 chat 模型 |
| `LLM_EMBEDDING_MODEL` | `text-embedding-3-small` | embedding 模型（注意 §11.6 维度事项） |
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | `localhost/3306/enterprise_agent/root/root` | MySQL |
| `REDIS_HOST/PORT/PASSWORD` | `localhost/6379/（空）` | Redis |
| `ES_URIS` | `http://localhost:9200` | Elasticsearch |
| `OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTel Collector（本地无 Collector 时会有上报失败日志，不影响业务） |

### 12.2 关键配置项（application.yml 全景）

```yaml
spring:
  application.name: enterprise-ai-agent
  threads.virtual.enabled: true            # Java 21 虚拟线程
  datasource:                              # MySQL + Hikari（max 20 / min-idle 5 / timeout 5s）
  jpa:
    hibernate.ddl-auto: none               # 表结构由 SQL 脚本管理（生产建议 Flyway）
    open-in-view: false
  elasticsearch:                           # uris/超时（VectorStore 经 Rest5Client 读此配置）
  data.redis:                              # host/port/password/timeout 3s
  ai:
    openai:                                # ← Spring AI 通道（chat model/temperature 0.2/embedding model）
    vectorstore.elasticsearch:             # initialize-schema: true / index-name: enterprise-knowledge / dimensions: 1024

embabel:
  agent:
    platform:
      process-type: SIMPLE                 # 切 CONCURRENT 时 AgentProcessCallback 兜底租户传播
      models:
        openai:
          api-key: ${LLM_API_KEY:}
          base-url: ${LLM_BASE_URL:https://api.openai.com}/v1   # ← 必须带 /v1（见 §11.6）
          max-attempts: 3                  # Phase 13 交互调优（默认 10 次×5s×5^n ≈ 3 分钟）
          backoff-millis: 2000             #   3 次×2s 起步×2 倍退避 ≈ 10s 内收敛
          backoff-multiplier: 2.0
          connect-timeout: 10s             # 1.5.3 新增（OpenAiCompatibleClientProperties）
          read-timeout: 180s               # 1.5.3 新增；容纳推理模型单次 ~117s 实测耗时

management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  tracing.sampling.probability: 1.0
  otlp.tracing.endpoint: ${OTLP_ENDPOINT:...}

agent:
  tools.permission:                        # default-policy: DENY + 4 个工具的权限映射
  prompts.active-versions: {}              # 灰度覆盖，key = "category/name"

logging:
  pattern.level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
  level:
    com.enterpriseai: DEBUG
    com.embabel: INFO
    org.springframework.ai: INFO
```

> `temperature: 0.2` —— 结构化提取任务需低温度，降低输出格式漂移。

---

## 13. 测试体系

### 13.1 总览（53 个，`mvn test` 全绿）

| 模块 | 数量 | 覆盖 |
|---|---|---|
| domain | 6 | ConversationHistory（transcript 格式 / 300 字截断 / 空历史占位 / null 语义） |
| core | 6 | TenantContext（require 失败方向 = PERMISSION_DENIED / 清理）；PromptRegistry（基线渲染 / 灰度覆盖 / 缺模板快速失败） |
| infrastructure | 8 | Redis 存储行解析（role\|content 分割 / 内容含 \| / 畸形行跳过）；统计口径（COUNT 总量单行 / MAX 默认 top1 降序 / MIN 升序 / ENTERPRISE_COUNT 不支持组合时快速失败） |
| rag | 8 | 隔离表达式（租户等值必有 / documentType 可选 / 无 restricted 权限强制 public / 注入清洗）；检索请求构造（topK/查询词/过滤表达式）；摄入（tenantId 只取自上下文 / 切块边界 / visibility 规范化） |
| tool | 5 | 权限评估（未登记 DENY / 显式 ALLOW / `*` 通配 / 按权限点判定） |
| api | 9 | 鉴权过滤器（头解析 / 401 / ThreadLocal 清理 / 非 API 路径放行）；错误分类（ExecutionException 包装 TransientAiException → LLM_ERROR 可重试 / openai SDK 前缀匹配 → LLM_ERROR 不可重试 / ActionException 无 LLM 签名 → TOOL_ERROR / 无关异常 → BUSINESS_ERROR / 请求体不可读 → 400） |
| application | 11 | Agent 集成 ×3（ENTERPRISE_QUERY 全链路且其余服务**零调用**；UNKNOWN 兜底**零副作用**；**多轮追问：历史注入意图 Prompt 且按 tenant+session 读取**）；HTTP 冒烟 ×3（200 响应结构+审计+记忆写入 / 401 / 400 参数校验）；审计事件监听 ×5（成功/失败映射 / 无上下文跳过 / 无关事件忽略 / typePart 解析） |

> Phase 12 文档曾写"26 个测试"，实际基线为 **31 个**（core 6 / infra 4 / tool 5 / rag 8 / api 3 / application 5）——Phase 13 复核修正。

### 13.2 离线测试策略（无真实 LLM / MySQL / ES / Redis）

- **Agent 集成测试设施**（全部来自已核实的官方 API）：`IntegrationTestUtils.dummyAgentPlatform(llmOperations)`（Embabel api jar 自带）构建独立平台 → `new AgentMetadataReader()`（Kotlin 全默认参构造，Java 可直接 new）→ `createAgentMetadata(agent实例)` 转 `Agent` → `platform.deploy()`。**GOAP 规划与 Action 链真实执行，仅 LLM 按输出类型打桩**（`llmOperations.createObject(messages, llmInteraction, eq(输出类), any, any)`，遵循官方 `EmbabelMockitoIntegrationTest` 示例签名）。
- **HTTP 冒烟**：MockMvc standalone（不启动容器），过滤器/异常收口/审计/记忆逐件显式装配。
- **价值实证**：集成测试抓出过真实架构级 bug——`@Condition` 条件未在任何 `post` 中声明导致 GOAP 判不可达（见 §6.1）；若无此测试，问题要到真实环境联调才暴露。

### 13.3 运行

```bash
mvn test                 # 全模块（离线，无需任何外部依赖）
mvn test -pl enterprise-ai-agent-application   # 只跑集成+冒烟
```

---

## 14. 已知限制与踩坑记录

> 本节是真实联调（Phase 12）逐项实证的坑位图，二次开发前必读。

### 14.1 Spring Boot 4.1 破坏性变更（全部实测踩坑）

1. **主类在子包时**必须 `scanBasePackages = "com.enterpriseai"` + `@ConfigurationPropertiesScan`，否则其他模块 bean 全丢（默认只扫主类所在包）。
2. **Jackson 3**：Boot 4 自动装配的 ObjectMapper 是 `tools.jackson.databind.ObjectMapper`；注入 `com.fasterxml` 的 ObjectMapper 找不到 bean（fasterxml databind 2.x 仅作注解兼容层存在）。
3. **JPA 扫描独立**：`@EnableJpaRepositories` 与组件扫描相互独立，须显式指定 infra 仓库包；`@EntityScan` 已迁包至 `org.springframework.boot.persistence.autoconfigure`。
4. **record 上 `@Component` + `@ConfigurationProperties` 并存 = 启动崩**——只留 `@ConfigurationProperties` + Scan。
5. **Boot 4 起 web starter 更名**：`spring-boot-starter-webmvc`（不再是 `spring-boot-starter-web`）。

### 14.2 Embabel 1.5.3 关键事实（源码/字节码核实）

1. **线程模型**：SIMPLE 模式的 Action 同样跑在 `embabel-virtual` 虚拟线程（`AgentPlatform.start` 经 `Asyncer.async` 提交，每任务新建线程）——**不要**认为"SIMPLE = 调用线程执行"；ThreadLocal 选型必须 Inheritable（本项目 TenantContext 与 AgentExecutionContext 均已处理）。
2. **SimpleAgentProcess 不调用 callbacks**；ConcurrentAgentProcess 才走 `AgentProcessCallback` 钩子（beforeActionLaunched 捕获 / onActionLaunched 恢复 / onActionCompleted 清理），回调实例被平台单例持有，**快照不能存实例字段**（跨请求竞态 = 租户串写）。Action 级事件观察走 `AgenticEventListener` SPI（容器内全部实现 Bean 被平台自动组播，本项目 `AgentAuditEventListener` 即此机制）。
3. **LLM 接入走官方 openai-java SDK（com.openai），不是 Spring AI 客户端**：URL = baseUrl + `chat/completions`（固定相对路径）→ embabel base-url 必须带 `/v1`；`completions` / `embeddings-path` 属性是废弃空操作。
4. **LLM 失败重试**（1.5.2/1.5.3 已修复重试策略与限流检测）：默认仍为 10 次×5s×5^n 退避；本项目已配置 `max-attempts: 3` + `backoff-millis: 2000` + `backoff-multiplier: 2.0`（见 §12.2），另用 1.5.3 新增 `connect-timeout`/`read-timeout` 约束单次调用时长。
5. **`@Action(toolGroups=...)` 是已废弃旧 API**（GitHub README 残留）；1.5.x 工具机制 = 领域对象 `@LlmTool` + `PromptRunner.withToolChainingFrom(type)`。本项目工具走 Spring AI `@Tool` + 自建权限注册中心，与 Embabel 工具链并行。
6. **Elasticsearch 必须 9.x 服务端**：Spring AI 2.0 rest5 客户端对 ES 8 的 `indices.exists` HEAD 请求返回 400 无体；ES 8 数据卷不能原地升级，须删卷重建。
7. **`AgentInvocation.invoke` 经 `CompletableFuture.get()` 上抛失败**（Phase 13 实证）：Action 失败到达 Controller 时是 `ExecutionException` 包装而非裸 `ActionException`——错误分类必须沿 cause 链探测（`GlobalExceptionHandler.diagnoseLlmFailure`）。
8. **Kotlin 值类互操作**（Phase 13 实证）：`IoBinding` 是 `@JvmInline value class`，Java 侧无法调用其 `getType()`（字节码中为静态 `getType-impl` 混淆名）——须走 `toString()`（返回 "name:Type" 原文）再解析。
9. **1.5.3 新增 `AchievableGoalValidator`**：@AchievesGoal 方法必须同时标注 @Action，否则部署被拒（本项目两个 Goal Action 均合规）。

### 14.3 遗留小项（不阻塞，按需处理）

- ~~LLM 失败分类宜为 `LLM_ERROR`~~ ✅ Phase 13 已修复（cause 链探测 Spring AI retry / openai SDK 异常）
- ~~`HttpMessageNotReadableException` 宜映射 400~~ ✅ Phase 13 已修复
- ~~3 分钟退避重试对交互场景偏长~~ ✅ Phase 13 已配置 max-attempts=3 + 超时
- ~~多轮追问的上下文注入~~ ✅ Phase 13 已交付（loadConversationHistory + v2 模板）
- 知识库摄入暂无 REST 端点（编程调用）

---

## 15. 目录结构总览

```
embabel_project/
├── pom.xml                        # 父 POM（Boot 4.1.1 / Java 21 / Spring AI 2.0.0 BOM / Embabel 1.5.0）
├── docker-compose.yml             # MySQL 8.4 + Redis 7 + ES 9.1.0（本地一键依赖）
├── app.log / app.err.log          # 运行日志（本地联调产物）
├── tasks/                         # 实施过程记录
│   ├── todo.md                    # Phase 0–12 全程计划与 Review（含全部实证结论）
│   └── e2e-test-1/2.json          # 端到端测试请求样例
│
├── enterprise-ai-agent-domain/            # 领域模型（零框架依赖）
│   └── src/main/java/.../domain/
│       ├── model/                 # 15 个 record + 5 组枚举闭集
│       └── service/               # 4 个领域服务接口（租户隔离契约）
│
├── enterprise-ai-agent-core/              # 核心契约层
│   └── src/main/java/.../core/
│       ├── context/               # TenantContext / UserContext / AgentExecutionContext
│       ├── exception/             # AgentErrorCode(7) / AgentException
│       ├── memory/                # ConversationStore 接口
│       ├── observation/           # AgentExecutionRecorder 接口
│       ├── permission/            # ToolPermissionEvaluator 接口
│       └── prompt/                # PromptRegistry + PromptVersionProperties
│
├── enterprise-ai-agent-infrastructure/    # 基础设施实现
│   └── src/main/java/.../infra/
│       ├── audit/                 # MySqlAgentExecutionRecorder
│       ├── memory/                # RedisConversationStore
│       ├── persistence/entity/    # 4 个 JPA 实体
│       ├── persistence/repository/# 4 个 Repository（Specification + JPQL 聚合）
│       └── service/               # 3 个 ServiceImpl（强制租户过滤）
│
├── enterprise-ai-agent-tool/              # 工具层
│   └── src/main/java/.../tool/
│       ├── alarm/enterprise/kb/stats/    # 4 个 Spring AI @Tool
│       ├── permission/           # 配置驱动权限评估器 + 属性
│       ├── registry/              # PermissionAwareToolRegistry（每次调用过滤）
│       └── ToolUsage.java
│
├── enterprise-ai-agent-rag/               # 检索增强
│   └── src/main/java/.../rag/
│       ├── index/                 # KnowledgeIngestionService（切块+元数据）
│       └── search/                # KnowledgeSearchServiceImpl（向量检索硬隔离）
│
├── enterprise-ai-agent-agent/             # Agent 编排（Embabel）
│   └── src/main/java/.../agent/assistant/
│       └── EnterpriseAssistantAgent.java  # @Agent + 17 @Action + 5 @Condition + 2 @AchievesGoal
│
├── enterprise-ai-agent-api/              # REST 接入
│   └── src/main/java/.../api/
│       ├── controller/            # AgentChatController（POST /api/agent/chat）
│       ├── dto/                   # ChatRequest / ChatResponse / ErrorResponse
│       ├── security/              # AuthenticationFilter（信任头）
│       └── advice/                # GlobalExceptionHandler
│
└── enterprise-ai-agent-application/       # 启动聚合（可执行）
    └── src/main/
        ├── java/.../application/
        │   ├── EnterpriseAiAgentApplication.java   # 主类（4 个扫描注解）
        │   └── config/             # Metrics 装饰器 / SpringAiConfig / 租户传播 Callback
        │                            #   / AgentAuditEventListener（Action 事件→审计补线，Phase 13）
        ├── resources/
        │   ├── application.yml     # 全部配置（见 §12；含 embabel 重试/超时调优）
        │   ├── db/schema.sql + data.sql
        │   ├── prompts/            # 13 个 .st 模板（5 个 v2 生效 + 8 个 v1 保留回滚）+ README
        │   └── static/index.html   # 聊天界面
        └── test/                   # Agent 集成 ×3 + HTTP 冒烟 ×3
```

---

*文档基于 2026-08-28 全量代码探索与真实联调实证撰写；实施过程完整记录见 `tasks/todo.md`。*
