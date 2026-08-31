package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;

/** 统计结果：由 Java Service 聚合计算产出，LLM 只负责解释。 */
@JsonClassDescription("Aggregated statistics result grouped by one dimension")
public record StatisticsResult(
        StatisticsQuery.AggregationType aggregationType,
        StatisticsQuery.StatisticsMetric metric,
        StatisticsQuery.GroupByDimension groupBy,
        List<StatisticsRow> rows,
        int totalRows) {

    @JsonClassDescription("One aggregated row: dimension label and its value")
    public record StatisticsRow(
            String dimension,
            long value) {
    }
}
