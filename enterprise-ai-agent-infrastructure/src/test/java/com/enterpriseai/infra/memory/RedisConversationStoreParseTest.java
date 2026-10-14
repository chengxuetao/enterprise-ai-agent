package com.enterpriseai.infra.memory;

import com.enterpriseai.domain.model.ConversationHistory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Redis 存储行 "role|content" 的解析契约：
 * 首次 '|' 分割（内容中的 '|' 保留），畸形行（无分隔符/null）跳过。
 */
class RedisConversationStoreParseTest {

    @Test
    void parsesRoleAndContent() {
        ConversationHistory.ConversationMessage message = RedisConversationStore.parse("user|帮我查企业");

        assertEquals("user", message.role());
        assertEquals("帮我查企业", message.content());
    }

    @Test
    void contentMayContainPipeCharacters() {
        ConversationHistory.ConversationMessage message =
                RedisConversationStore.parse("assistant|条件 A|B|C 全部满足");

        assertEquals("assistant", message.role());
        assertEquals("条件 A|B|C 全部满足", message.content());
    }

    @Test
    void emptyContentIsPreserved() {
        ConversationHistory.ConversationMessage message = RedisConversationStore.parse("user|");

        assertEquals("user", message.role());
        assertEquals("", message.content());
    }

    @Test
    void malformedLineWithoutSeparatorIsRejected() {
        assertNull(RedisConversationStore.parse("没有分隔符的行"));
        assertNull(RedisConversationStore.parse("|content"));
        assertNull(RedisConversationStore.parse(null));
    }
}
