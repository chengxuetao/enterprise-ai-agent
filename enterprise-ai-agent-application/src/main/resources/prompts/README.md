# Prompt 模板库（版本管理约定）

## 目录结构

```
prompts/
├── intent/        意图识别（1 个认知任务：UserInput → IntentResult）
├── parameter/     参数提取（每类查询 1 个：Query 记录 → 强类型 Query 对象）
├── analysis/      数据分析（AnalysisData → AnalysisResult）
└── response/      最终响应组织（AnalysisResult → 面向用户的中文回答）
```

命名规则：`{name}-v{version}.st`，如 `intent-recognition-v1.st`。
**一个文件 = 一个认知任务**（架构铁律 4），禁止合并出"万能 Prompt"。

## 版本管理

| 机制 | 说明 |
|---|---|
| 基线版本 | 调用点代码中显式声明（如 `render("intent", "intent-recognition", 1, vars)`） |
| 灰度覆盖 | `application.yml` 的 `agent.prompts.active-versions.{category/name}: N`，加新 .st 文件 + 一行配置即生效 |
| 回滚 | 删除覆盖配置即回到基线版本（模板不可变，永不原地修改） |
| 变量 | `{var}` 占位符由 `PromptRegistry` 显式替换，不引入模板引擎 |

## 新版 Prompt 上线流程（灰度）

1. 新建 `xxx-v2.st`（v1 保持不动，供回滚）；
2. yml 中把该 Prompt 的 `active-versions` 指向 2；
3. 观测指标（`agent.action` Timer 按 actionName 维度）与审计表中的
   动作耗时/成功率对比 v1；
4. 异常时删除配置行立即回滚——无需重启之外的任何变更流程。

## Prompt 与代码的契约

每个 .st 的 Output Contract 一节描述的输出结构 = 对应 Domain 记录
（带 `@JsonPropertyDescription` 的 schema 约束）。**改 Prompt 输出结构必须同步改
Domain 记录并递增版本号**，二者共同构成 LLM 结构化输出的契约面。

## 当前版本与多轮上下文（Phase 13）

| 模板 | 生效版本 | 说明 |
|---|---|---|
| intent/intent-recognition | **v2**（基线，代码声明） | 新增 `{history}`：最近 10 条历史对话，追问继承上文意图 |
| parameter/enterprise-query | **v2**（基线，代码声明） | 新增 `{history}`：追问继承上文查询条件 |
| parameter/alarm-query | **v2**（基线，代码声明） | 同上，保留 `{current_time}` |
| parameter/statistics-query | **v2**（基线，代码声明） | 同上，保留 `{current_time}` |
| parameter/knowledge-query | **v2**（基线，代码声明） | 新增 `{history}`：结合上文改写独立完整检索问句 |
| analysis/data-analysis | v1 | 不变（分析对象是结构化 AnalysisData，无需历史） |
| response/final-response | v1 | 不变 |
| response/unknown-response | v1 | 不变 |

- v1 模板全部保留在 classpath，回滚 = yml 加一行 `agent.prompts.active-versions.{category/name}: 1`。
- `{history}` 由 Agent 的 `loadConversationHistory` Action 产出（`ConversationHistory.transcript()`），
  首轮对话或会话记忆不可用时为"（无历史对话）"——**占位符恒有值**，模板不会残留未替换标记。
- 历史规则铁律：只能引用历史中明确出现过的条件，禁止编造（各 v2 模板 Rules 均有对应条目）。

