package com.enterpriseai.rag.search;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.domain.model.KnowledgeQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 检索隔离（多租户铁律在向量层的落点）：
 * 过滤表达式必须包含当前租户等值条件；无 restricted 权限强制 public；
 * 表达式值经 sanitize 清洗，防注入。
 */
class KnowledgeSearchServiceImplTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeSearchServiceImpl service = new KnowledgeSearchServiceImpl(vectorStore);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void login(String permissions) {
        TenantContext.set(new UserContext("u1", "tenant-a", "alice",
                Set.of(), permissions == null ? Set.of() : Set.of(permissions)));
    }

    @Test
    void buildFilterAlwaysContainsTenantIsolation() {
        login(null);
        String filter = KnowledgeSearchServiceImpl.buildFilter(
                TenantContext.require(), null);
        assertEquals("tenantId == 'tenant-a' && visibility == 'public'", filter);
    }

    @Test
    void documentTypeAddedWhenProvided() {
        login(null);
        String filter = KnowledgeSearchServiceImpl.buildFilter(
                TenantContext.require(), "manual");
        assertEquals("tenantId == 'tenant-a' && documentType == 'manual' && visibility == 'public'", filter);
    }

    @Test
    void restrictedPermissionSeesPrivateDocuments() {
        login("knowledge:restricted");
        String filter = KnowledgeSearchServiceImpl.buildFilter(
                TenantContext.require(), null);
        assertEquals("tenantId == 'tenant-a'", filter);
    }

    @Test
    void sanitizeStripsExpressionInjection() {
        // 引号、空格、等号、竖线全部剥离；连字符在白名单内（过滤表达式安全字符）
        String evil = "tenant-a' || '1'=='1";
        assertEquals("tenant-a11", KnowledgeSearchServiceImpl.sanitize(evil));
    }

    @Test
    void searchBuildsRequestWithFilterAndTopK() {
        login(null);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("内容", Map.of("title", "t", "source", "s"))));

        var result = service.search(new KnowledgeQuery("如何报销", null, null));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals("如何报销", request.getQuery());
        assertEquals(5, request.getTopK());
        assertNotNull(request.getFilterExpression(), "必须携带租户过滤表达式");
        assertEquals(1, result.snippets().size());
        assertEquals("t", result.snippets().get(0).title());
        assertTrue(result.snippets().get(0).score() >= 0.0);
    }
}
