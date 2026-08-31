package com.enterpriseai.domain.model;

/**
 * 对外入口请求（API 层使用），与 Embabel 的 UserInput 解耦。
 * Controller 负责把 message 转换为 Embabel UserInput 驱动 Agent。
 */
public record UserRequest(String message, String sessionId) {

    public UserRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }
    }
}
