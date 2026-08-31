package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.time.LocalDateTime;
import java.util.List;

/** 告警查询结果：由 Java Service 产出，LLM 只读分析。 */
@JsonClassDescription("Query result containing a list of alarm records")
public record AlarmResult(
        List<AlarmRecord> alarms,
        int total,
        boolean truncated) {

    @JsonClassDescription("A single alarm record")
    public record AlarmRecord(
            Long id,
            String enterpriseName,
            String alarmType,
            AlarmLevel alarmLevel,
            LocalDateTime occurredAt,
            String message,
            String status) {
    }
}
