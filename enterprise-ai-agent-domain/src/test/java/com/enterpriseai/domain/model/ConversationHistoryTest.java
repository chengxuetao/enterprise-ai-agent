package com.enterpriseai.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConversationHistory 的 Prompt 渲染与空值语义（多轮上下文注入的基础契约）。
 */
class ConversationHistoryTest {

    @Test
    void emptyHistoryRendersPlaceholder() {
        ConversationHistory history = ConversationHistory.empty();

        assertTrue(history.isEmpty());
        assertEquals("（无历史对话）", history.transcript());
    }

    @Test
    void nullMessagesListBehavesAsEmpty() {
        ConversationHistory history = new ConversationHistory(null);

        assertTrue(history.isEmpty());
        assertEquals(List.of(), history.messages());
    }

    @Test
    void transcriptRendersRoleLabelsInOrder() {
        ConversationHistory history = new ConversationHistory(List.of(
                new ConversationHistory.ConversationMessage("user", "帮我查北京的企业"),
                new ConversationHistory.ConversationMessage("assistant", "已找到 8 家。"),
                new ConversationHistory.ConversationMessage("user", "那深圳呢")));

        assertFalse(history.isEmpty());
        assertEquals("""
                用户：帮我查北京的企业
                助手：已找到 8 家。
                用户：那深圳呢
                """, history.transcript());
    }

    @Test
    void longMessageIsTruncatedTo300Chars() {
        String longContent = "长".repeat(500);
        ConversationHistory history = new ConversationHistory(List.of(
                new ConversationHistory.ConversationMessage("user", longContent)));

        String transcript = history.transcript();
        assertTrue(transcript.length() < longContent.length(),
                "超长消息应被截断以保护上下文窗口");
        assertTrue(transcript.trim().endsWith("…"), "截断应以省略号结尾");
        assertTrue(transcript.startsWith("用户：" + "长".repeat(300)));
    }

    @Test
    void nullContentIsRenderedAsEmpty() {
        ConversationHistory history = new ConversationHistory(List.of(
                new ConversationHistory.ConversationMessage("user", null)));

        assertEquals("用户：\n", history.transcript());
        assertNull(history.messages().get(0).content());
    }

    @Test
    void manyMessagesAreAllKept() {
        List<ConversationHistory.ConversationMessage> messages = IntStream.range(0, 10)
                .mapToObj(i -> new ConversationHistory.ConversationMessage("user", "msg" + i))
                .toList();

        assertEquals(10, new ConversationHistory(messages).messages().size());
    }
}
