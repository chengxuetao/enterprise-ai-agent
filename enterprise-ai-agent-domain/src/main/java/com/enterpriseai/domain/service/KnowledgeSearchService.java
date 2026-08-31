package com.enterpriseai.domain.service;

import com.enterpriseai.domain.model.KnowledgeQuery;
import com.enterpriseai.domain.model.KnowledgeResult;

/**
 * 知识库检索领域服务（RAG 实现位于 rag 模块）。
 * 实现方必须：向量检索时按 tenantId 做元数据过滤，禁止跨租户检索。
 */
public interface KnowledgeSearchService {

    KnowledgeResult search(KnowledgeQuery query);
}
