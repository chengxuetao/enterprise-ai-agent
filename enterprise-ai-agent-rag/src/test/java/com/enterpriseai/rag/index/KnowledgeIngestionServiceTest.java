package com.enterpriseai.rag.index;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 知识摄入：tenantId 只来自 TenantContext（调用方不可伪造）；
 * 切块聚合至约 800 字符；visibility 规范化为 public/restricted。
 */
class KnowledgeIngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeIngestionService service = new KnowledgeIngestionService(vectorStore);

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void chunkKeepsShortContentAsSinglePiece() {
        assertEquals(List.of("第一段\n第二段"),
                KnowledgeIngestionService.chunk("第一段\n第二段"));
    }

    @Test
    void chunkSplitsLongContentIntoBoundedPieces() {
        String paragraph = "字".repeat(600);
        List<String> chunks = KnowledgeIngestionService.chunk(paragraph + "\n" + paragraph + "\n" + paragraph);
        // 600+600 > 800 触发切分；每个块不超过 800 + 一行长度
        assertTrue(chunks.size() >= 2, "长文本应被切块，实际块数=" + chunks.size());
        assertTrue(chunks.stream().allMatch(c -> c.length() <= 800));
    }

    @Test
    void ingestStampsTenantFromContextNotFromCaller() {
        TenantContext.set(new UserContext("u1", "tenant-b", "bob", Set.of(), Set.of()));

        service.ingest("手册", "正文内容", "handbook.pdf", "manual", "restricted");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> documents = captor.getValue();
        assertEquals(1, documents.size());
        assertEquals("tenant-b", documents.get(0).getMetadata().get("tenantId"));
        assertEquals("manual", documents.get(0).getMetadata().get("documentType"));
        assertEquals("restricted", documents.get(0).getMetadata().get("visibility"));
        assertEquals(1, documents.get(0).getMetadata().get("chunk"));
    }
}
