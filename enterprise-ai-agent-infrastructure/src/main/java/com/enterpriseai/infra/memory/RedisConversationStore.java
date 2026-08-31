package com.enterpriseai.infra.memory;

import com.enterpriseai.core.memory.ConversationStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis List 实现的短期会话记忆。
 *
 * key = conv:{tenantId}:{sessionId}，租户隔离在 key 层面成立；
 * 每次写入刷新 TTL（7 天不活跃即清理），单条内容截断至 2000 字符，
 * 列表保留最近 100 条，防止长会话无界增长。
 */
@Component
public class RedisConversationStore implements ConversationStore {

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
    public List<String> recentMessages(String tenantId, String sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> raw = redis.opsForList().range(key(tenantId, sessionId), -limit, -1);
        return raw == null ? List.of() : raw;
    }

    private static String key(String tenantId, String sessionId) {
        return "conv:" + tenantId + ":" + sessionId;
    }
}
