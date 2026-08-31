package com.enterpriseai.tool.kb;

import com.enterpriseai.domain.model.KnowledgeQuery;
import com.enterpriseai.domain.model.KnowledgeResult;
import com.enterpriseai.domain.service.KnowledgeSearchService;
import com.enterpriseai.tool.ToolUsage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具（RAG）。
 *
 * 契约：
 * - Tool Name:  searchKnowledge
 * - Input Schema:  KnowledgeQuery（改写后的检索问句 + 可选 documentType/topK）
 * - Output Schema: KnowledgeResult（snippet 列表，含 title/content/source/score）
 * - Permission:    knowledge:search
 * - 安全：检索在 rag 模块实现，按 tenantId 做向量库元数据过滤，跨租户内容不可见。
 */
@Component
public class KnowledgeTool {

    public static final String SEARCH_KNOWLEDGE = "searchKnowledge";

    private final KnowledgeSearchService knowledgeSearchService;

    public KnowledgeTool(KnowledgeSearchService knowledgeSearchService) {
        this.knowledgeSearchService = knowledgeSearchService;
    }

    @Tool(name = SEARCH_KNOWLEDGE, description = """
            Search the internal enterprise knowledge base for documents such as manuals,
            policies and FAQs. Input a rewritten, retrieval-friendly question.
            Returns the most relevant snippets with titles, content and sources.""")
    public KnowledgeResult searchKnowledge(
            @ToolParam(description = "Retrieval question plus optional documentType and topK")
            KnowledgeQuery query) {
        KnowledgeResult result = knowledgeSearchService.search(query);
        ToolUsage.record(SEARCH_KNOWLEDGE, result);
        return result;
    }
}
