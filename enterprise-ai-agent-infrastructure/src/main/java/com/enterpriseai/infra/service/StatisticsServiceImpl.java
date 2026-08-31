package com.enterpriseai.infra.service;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import com.enterpriseai.domain.model.StatisticsQuery;
import com.enterpriseai.domain.model.StatisticsResult;
import com.enterpriseai.domain.model.StatisticsResult.StatisticsRow;
import com.enterpriseai.domain.service.StatisticsService;
import com.enterpriseai.infra.persistence.repository.AlarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统计实现：全部聚合在数据库完成，LLM 不参与计算。
 *
 * 口径约定（与 statistics-query-v1.st 及 AlarmRepository 注释一致）：
 * - COUNT：按 groupBy 分组计数（groupBy 为 null 时输出单行"总计"）；
 * - MAX / MIN：分组计数后按值 降序/升序 取 topN（MAX 且 topN 缺省时取 1）；
 * - ENTERPRISE_COUNT（去重企业数）仅支持 groupBy 为 null 或 DAY，其他组合直接拒绝（快速失败）。
 */
@Service
@Transactional(readOnly = true)
public class StatisticsServiceImpl implements StatisticsService {

    private static final LocalDateTime MIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime MAX_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final AlarmRepository alarmRepository;

    public StatisticsServiceImpl(AlarmRepository alarmRepository) {
        this.alarmRepository = alarmRepository;
    }

    @Override
    public StatisticsResult computeStatistics(StatisticsQuery query) {
        UserContext user = TenantContext.require();

        StatisticsQuery.AggregationType aggregationType =
                query.aggregationType() == null ? StatisticsQuery.AggregationType.COUNT : query.aggregationType();
        StatisticsQuery.StatisticsMetric metric =
                query.metric() == null ? StatisticsQuery.StatisticsMetric.ALARM_COUNT : query.metric();

        LocalDateTime start = query.startTime() == null ? MIN_TIME : query.startTime();
        LocalDateTime end = query.endTime() == null ? MAX_TIME : query.endTime();
        String tenantId = user.tenantId();

        List<Object[]> tuples = fetchTuples(query, metric, tenantId, start, end);

        List<StatisticsRow> rows = new ArrayList<>(tuples.stream()
                .map(t -> new StatisticsRow(
                        t[0] == null ? "未知" : String.valueOf(t[0]),
                        ((Number) t[1]).longValue()))
                .toList());

        if (aggregationType == StatisticsQuery.AggregationType.MIN) {
            rows.sort(Comparator.comparingLong(StatisticsRow::value));
        } else {
            rows.sort(Comparator.comparingLong(StatisticsRow::value).reversed());
        }

        Integer topN = query.topN();
        if (topN == null && aggregationType == StatisticsQuery.AggregationType.MAX) {
            topN = 1;
        }
        if (topN != null && topN < rows.size()) {
            rows = new ArrayList<>(rows.subList(0, topN));
        }

        return new StatisticsResult(aggregationType, metric, query.groupBy(), rows, rows.size());
    }

    private List<Object[]> fetchTuples(StatisticsQuery query,
                                       StatisticsQuery.StatisticsMetric metric,
                                       String tenantId,
                                       LocalDateTime start,
                                       LocalDateTime end) {
        if (query.groupBy() == null) {
            long value = metric == StatisticsQuery.StatisticsMetric.ENTERPRISE_COUNT
                    ? alarmRepository.countDistinctEnterprises(tenantId, start, end)
                    : alarmRepository.countTotalAlarms(tenantId, start, end);
            return List.<Object[]>of(new Object[]{"总计", value});
        }
        boolean distinctEnterprise = metric == StatisticsQuery.StatisticsMetric.ENTERPRISE_COUNT;
        return switch (query.groupBy()) {
            case ENTERPRISE -> {
                if (distinctEnterprise) {
                    // "按企业去重计数" 语义上等价于按企业统计告警数
                    yield alarmRepository.countGroupByEnterprise(tenantId, start, end);
                }
                yield alarmRepository.countGroupByEnterprise(tenantId, start, end);
            }
            case ALARM_TYPE -> {
                requireNotDistinct(metric);
                yield alarmRepository.countGroupByAlarmType(tenantId, start, end);
            }
            case ALARM_LEVEL -> {
                requireNotDistinct(metric);
                yield alarmRepository.countGroupByAlarmLevel(tenantId, start, end);
            }
            case DAY -> distinctEnterprise
                    ? alarmRepository.countDistinctEnterpriseGroupByDay(tenantId, start, end)
                    : alarmRepository.countGroupByDay(tenantId, start, end);
        };
    }

    private static void requireNotDistinct(StatisticsQuery.StatisticsMetric metric) {
        if (metric == StatisticsQuery.StatisticsMetric.ENTERPRISE_COUNT) {
            throw new AgentException(AgentErrorCode.VALIDATION_ERROR,
                    "Unsupported statistics combination: ENTERPRISE_COUNT only supports groupBy=null or DAY");
        }
    }
}
