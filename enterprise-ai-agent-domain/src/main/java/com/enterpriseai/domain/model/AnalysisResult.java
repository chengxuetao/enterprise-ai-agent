package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 数据分析结果（LLM Structured Output 的目标类型之一）。
 * 契约：所有结论只能来自 AnalysisData，禁止编造（见 data-analysis-v1.st）。
 */
@JsonClassDescription("Structured analysis result derived only from the provided data")
public record AnalysisResult(
        @JsonPropertyDescription("One-paragraph summary in Chinese")
        String summary,
        @JsonPropertyDescription("Findings strictly derived from the data")
        List<String> findings,
        @JsonPropertyDescription("Risks observed in the data, empty if none")
        List<String> risks,
        @JsonPropertyDescription("Actionable recommendations, empty if data is insufficient")
        List<String> recommendations) {

    public AnalysisResult {
        findings = findings == null ? List.of() : findings;
        risks = risks == null ? List.of() : risks;
        recommendations = recommendations == null ? List.of() : recommendations;
    }
}
