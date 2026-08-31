package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;

/**
 * 归一化后的分析输入：四条业务分支的查询结果由纯 Java Action 统一转换为此类型，
 * 供 DataAnalysisAction 消费。行数已在转换时截断（保护上下文窗口与 token 成本）。
 */
@JsonClassDescription("Normalized business data prepared for LLM analysis")
public record AnalysisData(
        IntentType intent,
        boolean dataPresent,
        int totalRecords,
        boolean truncated,
        String sourceDescription,
        List<DataRow> rows) {

    public record DataRow(String label, String details) {
    }
}
