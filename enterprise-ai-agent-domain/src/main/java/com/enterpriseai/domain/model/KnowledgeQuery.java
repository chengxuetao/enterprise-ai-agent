package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 知识库检索条件（LLM 参数提取的输出类型之一）。
 * question 是改写后的检索问句（用于向量检索），不是原文照抄。
 */
@JsonClassDescription("Structured knowledge base search request")
public record KnowledgeQuery(
        @JsonPropertyDescription("Rewritten search question suitable for vector retrieval")
        @Size(min = 2, max = 512) String question,
        @JsonPropertyDescription("Document type filter such as manual/policy/faq, null if not specified")
        @Size(max = 32) String documentType,
        @JsonPropertyDescription("Number of snippets to retrieve, null for default 5")
        @Min(1) @Max(10) Integer topK) {
}
