package com.enterpriseai.api.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * POST /api/agent/chat 成功响应。
 *
 * status: SUCCEEDED / FAILED；
 * executionId 为本次执行的统一追踪键（审计表、日志、指标、Trace 同 ID 关联）；
 * 失败时返回 ErrorResponse（同 executionId），便于客户端凭 ID 查询执行明细或重试。
 */
public record ChatResponse(
        @JsonPropertyDescription("本次执行的追踪 ID")
        String executionId,
        @JsonPropertyDescription("面向用户的中文回答（Markdown）")
        String answer,
        @JsonPropertyDescription("识别出的意图")
        String intent,
        @JsonPropertyDescription("SUCCEEDED / FAILED")
        String status
) {
}
