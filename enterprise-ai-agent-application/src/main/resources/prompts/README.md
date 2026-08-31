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
