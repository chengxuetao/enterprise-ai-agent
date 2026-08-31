package com.enterpriseai.domain.model;

/**
 * 系统当前支持的意图闭集。
 * LLM 只能从中选择（Prompt 明确约束），Java 侧以枚举强校验。
 */
public enum IntentType {
    /** 企业信息查询 */
    ENTERPRISE_QUERY,
    /** 告警查询 */
    ALARM_QUERY,
    /** 数据统计（聚合、计数、占比、排名） */
    STATISTICS_QUERY,
    /** 企业知识库问答（RAG） */
    KNOWLEDGE_QA,
    /** 无法识别 */
    UNKNOWN
}
