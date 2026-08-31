package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 意图识别结果：LLM Structured Output 的目标类型之一。
 * 置信度低于阈值时 LLM 必须返回 UNKNOWN（见 intent-recognition-v1.st）。
 */
@JsonClassDescription("Result of intent recognition for an enterprise assistant request")
public record IntentResult(
        @JsonPropertyDescription("One of ENTERPRISE_QUERY, ALARM_QUERY, STATISTICS_QUERY, KNOWLEDGE_QA, UNKNOWN")
        IntentType intent,
        @JsonPropertyDescription("Confidence between 0.0 and 1.0")
        double confidence,
        @JsonPropertyDescription("Short reason for the chosen intent, in Chinese")
        String reason) {
}
