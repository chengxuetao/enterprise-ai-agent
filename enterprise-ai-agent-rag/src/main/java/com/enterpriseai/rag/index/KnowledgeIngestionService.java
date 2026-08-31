package com.enterpriseai.rag.index;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识摄入服务：把文档切块后写入向量库，并为每一块打上检索隔离所需的元数据。
 *
 * 元数据契约（与 KnowledgeSearchServiceImpl 的过滤条件一一对应）：
 * - tenantId：摄入者的租户（来自 TenantContext，调用方不可指定）
 * - documentType：manual / policy / faq / ...
 * - title / source：展示与溯源
 * - visibility：public / restricted（restricted 仅 knowledge:restricted 权限可见）
 *
 * 切块策略：按段落聚合至约 800 字符的简易切块；
 * 生产环境可替换为 spring-ai-commons 的 TokenTextSplitter（接口不变）。
 */
@Service
public class KnowledgeIngestionService {

    private static final int MAX_CHUNK_SIZE = 800;

    private final VectorStore vectorStore;

    public KnowledgeIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingest(String title, String content, String source,
                       String documentType, String visibility) {
        UserContext user = TenantContext.require();

        List<Document> documents = new ArrayList<>();
        List<String> chunks = chunk(content);
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tenantId", user.tenantId());
            metadata.put("documentType", documentType == null || documentType.isBlank()
                    ? "general" : documentType);
            metadata.put("title", title);
            metadata.put("source", source);
            metadata.put("visibility", "restricted".equals(visibility) ? "restricted" : "public");
            metadata.put("chunk", i + 1);
            documents.add(new Document(chunks.get(i), metadata));
        }
        vectorStore.add(documents);
    }

    static List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\n")) {
            if (!current.isEmpty() && current.length() + paragraph.length() > MAX_CHUNK_SIZE) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(paragraph).append('\n');
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(content) : chunks;
    }
}
