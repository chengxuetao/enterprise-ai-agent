package com.enterpriseai.application.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 基础装配（Phase 3）。
 *
 * - ChatModel / EmbeddingModel：由 spring-ai-starter-model-openai 自动配置，
 *   通过 spring.ai.openai.* 指向 OpenAI 兼容端点（DeepSeek/Qwen/vLLM 等可覆写 base-url 与 model）。
 * - ChatClient.Builder：由 spring-ai-autoconfigure-model-chat-client 提供（Embabel 亦基于该体系构建）。
 * - Structured Output：Embabel Ai#createObject 走 ChatModel 的 BeanOutputConverter；
 *   直接使用 Spring AI 的场景（Phase 6 Tool Calling、Phase 7 RAG）经此 ChatClient。
 */
@Configuration
public class SpringAiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
