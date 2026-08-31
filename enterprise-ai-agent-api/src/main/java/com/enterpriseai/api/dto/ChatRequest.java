package com.enterpriseai.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/agent/chat 请求体。
 *
 * sessionId 为空时由服务端生成新会话 ID（开启新会话）；
 * 携带上一轮返回的 sessionId 即为多轮会话（记忆按 tenantId+sessionId 隔离）。
 */
public record ChatRequest(
        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 过长（上限 4000 字符）")
        @JsonPropertyDescription("用户输入，中文自然语言")
        String message,

        @Size(max = 64, message = "sessionId 过长")
        @JsonPropertyDescription("会话 ID；为空则开启新会话")
        String sessionId
) {
}
