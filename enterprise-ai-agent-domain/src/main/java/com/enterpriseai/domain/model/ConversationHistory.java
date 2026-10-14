package com.enterpriseai.domain.model;

import java.util.List;

/**
 * 本次请求之前的历史对话（多轮上下文）。
 *
 * 定位：黑板数据流类型（同 {@link AnalysisData}），由纯 Java Action 从
 * 会话记忆加载，供意图识别与参数提取 Prompt 注入——解决追问场景
 * （如"那深圳呢"）中当前请求语义不完整的问题。
 *
 * 不是 LLM 输出类型：内容由后端会话记忆决定，LLM 只读取不产生。
 */
public record ConversationHistory(List<ConversationMessage> messages) {

    /** 每条消息注入 Prompt 时的最大长度，保护意图/提取 Prompt 的上下文窗口。 */
    private static final int MAX_MESSAGE_CHARS = 300;

    /** 单条历史消息。role 为 "user" / "assistant"。 */
    public record ConversationMessage(String role, String content) {
    }

    public ConversationHistory {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** 空历史（首轮对话或上下文不可用时的降级值）。 */
    public static ConversationHistory empty() {
        return new ConversationHistory(List.of());
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * 渲染进 Prompt 的转录文本，时间正序，每条一行："用户：…" / "助手：…"。
     * 空历史返回占位说明，便于模板规则区分首轮与追问。
     */
    public String transcript() {
        if (isEmpty()) {
            return "（无历史对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage message : messages) {
            String label = "assistant".equals(message.role()) ? "助手" : "用户";
            sb.append(label).append("：").append(truncate(message.content())).append('\n');
        }
        return sb.toString();
    }

    private static String truncate(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= MAX_MESSAGE_CHARS ? safe : safe.substring(0, MAX_MESSAGE_CHARS) + "…";
    }
}
