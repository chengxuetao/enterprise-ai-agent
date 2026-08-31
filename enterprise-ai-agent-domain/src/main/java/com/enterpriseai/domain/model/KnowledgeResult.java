package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;

/**
 * 知识库检索结果：由 RAG 服务产出（已按 tenantId 隔离）。
 * LLM 只基于 snippets 内容回答，不得引入外部知识。
 */
@JsonClassDescription("Knowledge base retrieval result")
public record KnowledgeResult(
        String question,
        List<KnowledgeSnippet> snippets) {

    @JsonClassDescription("One retrieved knowledge snippet")
    public record KnowledgeSnippet(
            String title,
            String content,
            String source,
            double score) {
    }
}
