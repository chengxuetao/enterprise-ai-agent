package com.enterpriseai.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 统一错误响应——只暴露错误码与用户可读信息，
 * 绝不透出堆栈、SQL、内部类名等实现细节（架构安全要求）。
 */
public record ErrorResponse(
        @JsonPropertyDescription("本次执行的追踪 ID（可能为空）")
        String executionId,
        @JsonPropertyDescription("错误码：LLM_ERROR / TOOL_ERROR / VALIDATION_ERROR / PERMISSION_DENIED / RAG_ERROR / PLANNING_ERROR / BUSINESS_ERROR")
        String code,
        @JsonPropertyDescription("用户可读的中文错误信息")
        String userMessage,
        @JsonPropertyDescription("该类错误是否建议客户端重试")
        boolean retryable
) {
}
