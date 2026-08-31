package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Agent 的最终输出类型：Goal 的达成标志（@AchievesGoal 挂在产出此类型的 Action 上）。
 */
@JsonClassDescription("Final answer of the enterprise assistant agent")
public record AgentResponse(
        @JsonPropertyDescription("User-facing answer in Markdown, Chinese")
        String answer,
        IntentType intent) {

    public AgentResponse {
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
    }
}
