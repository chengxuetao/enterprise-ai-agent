package com.enterpriseai.core.memory;

import java.util.List;

/**
 * 会话记忆存储（短期记忆，Redis 实现）。
 *
 * 设计（与架构文档第 16 节对应）：
 * - 短期记忆：按 tenantId + sessionId 隔离的最近对话轮次，用于后续多轮追问
 *   场景（意图识别携带上文），TTL 过期自动清理；
 * - 长期记忆：不属于本接口——企业/人员画像类长期记忆走 RAG 向量库
 *   （复用 KnowledgeIngestionService 的元数据契约，tenantId 隔离）。
 *
 * key 天然带 tenantId：跨租户读取在存储层即不可达。
 */
public interface ConversationStore {

    /** 追加一轮消息（role: "user" / "assistant"）。 */
    void appendMessage(String tenantId, String sessionId, String role, String content);

    /** 最近 limit 条消息（时间正序），无记录时返回空列表。 */
    List<String> recentMessages(String tenantId, String sessionId, int limit);
}
