package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * 统计查询（LLM 参数提取的输出类型之一）。
 * 聚合方式、指标、分组维度均为闭集枚举，防止 LLM 发明不支持的统计口径。
 */
@JsonClassDescription("Structured statistics query with closed enums for aggregation, metric and grouping")
public record StatisticsQuery(
        @JsonPropertyDescription("Aggregation type: COUNT, MAX or MIN")
        AggregationType aggregationType,
        @JsonPropertyDescription("Metric to measure: ALARM_COUNT or ENTERPRISE_COUNT")
        StatisticsMetric metric,
        @JsonPropertyDescription("Dimension to group by: ENTERPRISE, ALARM_TYPE, ALARM_LEVEL or DAY")
        GroupByDimension groupBy,
        @JsonPropertyDescription("Start time in ISO-8601 format, null if not specified")
        LocalDateTime startTime,
        @JsonPropertyDescription("End time in ISO-8601 format, null if not specified")
        LocalDateTime endTime,
        @JsonPropertyDescription("Return only top N groups when sorting by value, null for all")
        @Min(1) @Max(50) Integer topN) {

    public enum AggregationType { COUNT, MAX, MIN }

    public enum StatisticsMetric { ALARM_COUNT, ENTERPRISE_COUNT }

    public enum GroupByDimension { ENTERPRISE, ALARM_TYPE, ALARM_LEVEL, DAY }
}
