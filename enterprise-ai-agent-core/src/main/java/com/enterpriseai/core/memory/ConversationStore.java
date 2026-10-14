package com.enterpriseai.core.memory;

import com.enterpriseai.domain.model.ConversationHistory;

import java.util.List;

/**
 * 会话记忆存储（短期记忆，Redis 实现）。
 *
 * 设计（与架构文档第 16 节对应）：
 * - 短期记忆：按 tenantId + sessionId 隔离的最近对话轮次，用于多轮追问场景
 *   （Agent 的 loadConversationHistory Action 读取后注入意图识别/参数提取 Prompt），
 *   TTL 过期自动清理；
 * - 长期记忆：不属于本接口——企业/人员画像类长期记忆走 RAG 向量库
 *   （复用 KnowledgeIngestionService 的元数据契约，tenantId 隔离）。
 *
 * key 天然带 tenantId：跨租户读取在存储层即不可达。
 * 存储编码（如 "role|content"）是各实现私有细节，本契约只暴露类型化消息。
 */
public interface ConversationStore {

    /** 追加一轮消息（role: "user" / "assistant"）。 */
    void appendMessage(String tenantId, String sessionId, String role, String content);

    /** 最近 limit 条消息（时间正序），无记录时返回空列表。 */
    List<ConversationHistory.ConversationMessage> recentMessages(String tenantId, String sessionId, int limit);
}
