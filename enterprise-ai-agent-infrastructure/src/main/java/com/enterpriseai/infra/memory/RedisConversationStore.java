package com.enterpriseai.infra.memory;

import com.enterpriseai.core.memory.ConversationStore;
import com.enterpriseai.domain.model.ConversationHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Redis List 实现的短期会话记忆。
 * key = conv:{tenantId}:{sessionId}，租户隔离在 key 层面成立；
 * 每次写入刷新 TTL（7 天不活跃即清理），单条内容截断至 2000 字符，
 * 列表保留最近 100 条，防止长会话无界增长。
 *
 * 存储编码 "role|content"（首次 '|' 分割，内容中的 '|' 不受影响）是本实现私有细节，
 * 读取侧统一解析为类型化消息后返回。
 */
@Component
public class RedisConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

    private static final Duration TTL = Duration.ofDays(7);
    private static final int MAX_CONTENT_CHARS = 2000;
    private static final long MAX_MESSAGES = 100;

    private final StringRedisTemplate redis;

    public RedisConversationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void appendMessage(String tenantId, String sessionId, String role, String content) {
        String key = key(tenantId, sessionId);
        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_CONTENT_CHARS) {
            safeContent = safeContent.substring(0, MAX_CONTENT_CHARS);
        }
        redis.opsForList().rightPush(key, role + "|" + safeContent);
        redis.expire(key, TTL);
        Long size = redis.opsForList().size(key);
        if (size != null && size > MAX_MESSAGES) {
            redis.opsForList().trim(key, -MAX_MESSAGES, -1);
        }
    }

    @Override
    public List<ConversationHistory.ConversationMessage> recentMessages(String tenantId, String sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> raw = redis.opsForList().range(key(tenantId, sessionId), -limit, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .map(RedisConversationStore::parse)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 解析存储行 "role|content"；无分隔符的畸形行跳过并 WARN（不影响其余行）。 */
    static ConversationHistory.ConversationMessage parse(String line) {
        if (line == null) {
            return null;
        }
        int separator = line.indexOf('|');
        if (separator <= 0) {
            log.warn("会话记忆畸形行已跳过：{}", line);
            return null;
        }
        return new ConversationHistory.ConversationMessage(
                line.substring(0, separator), line.substring(separator + 1));
    }

    private static String key(String tenantId, String sessionId) {
        return "conv:" + tenantId + ":" + sessionId;
    }
}
