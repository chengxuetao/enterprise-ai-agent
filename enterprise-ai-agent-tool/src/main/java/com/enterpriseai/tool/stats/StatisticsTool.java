package com.enterpriseai.tool.stats;

import com.enterpriseai.domain.model.StatisticsQuery;
import com.enterpriseai.domain.model.StatisticsResult;
import com.enterpriseai.domain.service.StatisticsService;
import com.enterpriseai.tool.ToolUsage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 统计工具。
 *
 * 契约：
 * - Tool Name:  computeStatistics
 * - Input Schema:  StatisticsQuery（聚合/指标/分组均为闭集枚举；ENTERPRISE_COUNT 仅支持 groupBy=null 或 DAY）
 * - Output Schema: StatisticsResult（分组行 + 排序，topN 已应用）
 * - Permission:    statistics:query
 * - 说明：聚合全部在数据库完成，本工具不含任何 LLM 参与的计算。
 */
@Component
public class StatisticsTool {

    public static final String COMPUTE_STATISTICS = "computeStatistics";

    private final StatisticsService statisticsService;

    public StatisticsTool(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @Tool(name = COMPUTE_STATISTICS, description = """
            Compute aggregated statistics over alarms. aggregationType: COUNT (grouped count or total),
            MAX/MIN (grouped count then ordered by value, use topN). metric: ALARM_COUNT or
            ENTERPRISE_COUNT (distinct enterprises; only groupBy=null or DAY).
            groupBy: ENTERPRISE, ALARM_TYPE, ALARM_LEVEL, DAY, or null for an overall total.""")
    public StatisticsResult computeStatistics(
            @ToolParam(description = "Structured statistics query with closed enums")
            StatisticsQuery query) {
        StatisticsResult result = statisticsService.computeStatistics(query);
        ToolUsage.record(COMPUTE_STATISTICS, result);
        return result;
    }
}
