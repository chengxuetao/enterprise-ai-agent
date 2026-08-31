package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 告警查询条件（LLM 参数提取的输出类型之一）。
 * 相对时间（今天、最近 7 天）由 LLM 依据 Prompt 提供的 current_time 换算为 ISO-8601 绝对时间。
 */
@JsonClassDescription("Structured query conditions for searching alarms")
public record AlarmQuery(
        @JsonPropertyDescription("Start time in ISO-8601 format yyyy-MM-dd'T'HH:mm:ss, null if not specified")
        LocalDateTime startTime,
        @JsonPropertyDescription("End time in ISO-8601 format yyyy-MM-dd'T'HH:mm:ss, null if not specified")
        LocalDateTime endTime,
        @JsonPropertyDescription("Enterprise name, null if not specified")
        @Size(max = 64) String enterpriseName,
        @JsonPropertyDescription("Alarm type name, null if not specified")
        @Size(max = 32) String alarmType,
        @JsonPropertyDescription("One of CRITICAL, HIGH, MEDIUM, LOW, null if not specified")
        AlarmLevel alarmLevel,
        @JsonPropertyDescription("Max number of rows to return, null if not specified")
        @Max(200) Integer limit) {
}
