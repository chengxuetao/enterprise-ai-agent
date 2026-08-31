package com.enterpriseai.rag.search;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.domain.model.KnowledgeQuery;
import com.enterpriseai.domain.model.KnowledgeResult;
import com.enterpriseai.domain.model.KnowledgeResult.KnowledgeSnippet;
import com.enterpriseai.domain.service.KnowledgeSearchService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识检索实现（Elasticsearch Vector Store）。
 *
 * 隔离与权限（硬性过滤，不依赖 Prompt）：
 * 1. tenantId == 当前租户 —— 跨租户内容在向量检索层即不可见；
 * 2. documentType 等值过滤（用户指定时）；
 * 3. 无 knowledge:restricted 权限的用户只见 visibility == 'public' 的文档。
 *
 * filterExpression 的值经 sanitize 白名单清洗，防表达式注入。
 */
@Service
public class KnowledgeSearchServiceImpl implements KnowledgeSearchService {

    /** 无 knowledge:restricted 权限的用户只能检索公开文档。 */
    public static final String PERMISSION_RESTRICTED = "knowledge:restricted";

    private static final int DEFAULT_TOP_K = 5;

    private final VectorStore vectorStore;

    public KnowledgeSearchServiceImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public KnowledgeResult search(KnowledgeQuery query) {
        UserContext user = TenantContext.require();
        int topK = query.topK() == null ? DEFAULT_TOP_K : query.topK();

        String filter = buildFilter(user, query.documentType());

        SearchRequest request = SearchRequest.builder()
                .query(query.question())
                .topK(topK)
                .filterExpression(filter)
                .build();

        List<Document> documents = vectorStore.similaritySearch(request);
        List<KnowledgeSnippet> snippets = documents.stream()
                .map(KnowledgeSearchServiceImpl::toSnippet)
                .toList();
        return new KnowledgeResult(query.question(), snippets);
    }

    /**
     * 构造检索过滤表达式（包私有以便直接单测隔离规则）：
     * 租户等值 → 可选文档类型 → 无 restricted 权限则仅公开文档。
     */
    static String buildFilter(UserContext user, String documentType) {
        String filter = "tenantId == '" + sanitize(user.tenantId()) + "'";
        if (documentType != null && !documentType.isBlank()) {
            filter += " && documentType == '" + sanitize(documentType) + "'";
        }
        if (!user.hasPermission(PERMISSION_RESTRICTED)) {
            filter += " && visibility == 'public'";
        }
        return filter;
    }

    private static KnowledgeSnippet toSnippet(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return new KnowledgeSnippet(
                str(metadata.get("title")),
                doc.getText(),
                str(metadata.get("source")),
                doc.getScore() == null ? 0.0 : doc.getScore());
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 过滤表达式的值只保留安全字符（字母/数字/中文/._-），阻断表达式注入。 */
    static String sanitize(String value) {
        return value == null ? "" : value.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "");
    }
}
