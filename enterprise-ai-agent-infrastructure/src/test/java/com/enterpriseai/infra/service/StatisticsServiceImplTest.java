package com.enterpriseai.infra.service;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import com.enterpriseai.domain.model.StatisticsQuery;
import com.enterpriseai.domain.model.StatisticsResult;
import com.enterpriseai.infra.persistence.repository.AlarmRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统计口径测试（与 statistics-query-v1.st 的 Prompt 口径一一对应）：
 * COUNT 总量单行 / 分组计数；MAX 缺省 topN=1；MIN 升序；
 * ENTERPRISE_COUNT 不支持 ALARM_TYPE/ALARM_LEVEL 分组（快速失败 VALIDATION_ERROR）。
 */
class StatisticsServiceImplTest {

    private final AlarmRepository alarmRepository = mock(AlarmRepository.class);
    private final StatisticsServiceImpl service = new StatisticsServiceImpl(alarmRepository);

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 31, 23, 59);

    @BeforeEach
    void setUp() {
        TenantContext.set(new UserContext("u1", "tenant-a", "alice", Set.of(), Set.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void countWithoutGroupByYieldsSingleTotalRow() {
        when(alarmRepository.countTotalAlarms("tenant-a", START, END)).thenReturn(42L);

        StatisticsResult result = service.computeStatistics(new StatisticsQuery(
                StatisticsQuery.AggregationType.COUNT,
                StatisticsQuery.StatisticsMetric.ALARM_COUNT,
                null, START, END, null));

        assertEquals(1, result.rows().size());
        assertEquals("总计", result.rows().get(0).dimension());
        assertEquals(42L, result.rows().get(0).value());
        verify(alarmRepository).countTotalAlarms("tenant-a", START, END);
    }

    @Test
    void maxDefaultsToTop1Descending() {
        when(alarmRepository.countGroupByEnterprise(eq("tenant-a"), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"甲公司", 30L},
                        new Object[]{"乙公司", 10L},
                        new Object[]{"丙公司", 20L}));

        StatisticsResult result = service.computeStatistics(new StatisticsQuery(
                StatisticsQuery.AggregationType.MAX,
                StatisticsQuery.StatisticsMetric.ALARM_COUNT,
                StatisticsQuery.GroupByDimension.ENTERPRISE, START, END, null));

        assertEquals(1, result.rows().size());
        assertEquals("甲公司", result.rows().get(0).dimension());
        assertEquals(30L, result.rows().get(0).value());
    }

    @Test
    void minSortsAscending() {
        when(alarmRepository.countGroupByAlarmLevel(eq("tenant-a"), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"CRITICAL", 9L},
                        new Object[]{"LOW", 1L},
                        new Object[]{"MEDIUM", 5L}));

        StatisticsResult result = service.computeStatistics(new StatisticsQuery(
                StatisticsQuery.AggregationType.MIN,
                StatisticsQuery.StatisticsMetric.ALARM_COUNT,
                StatisticsQuery.GroupByDimension.ALARM_LEVEL, START, END, null));

        assertEquals("LOW", result.rows().get(0).dimension());
        assertEquals("MEDIUM", result.rows().get(1).dimension());
        assertEquals("CRITICAL", result.rows().get(2).dimension());
    }

    @Test
    void enterpriseCountRejectsAlarmTypeGroupingFast() {
        AgentException e = assertThrows(AgentException.class,
                () -> service.computeStatistics(new StatisticsQuery(
                        StatisticsQuery.AggregationType.COUNT,
                        StatisticsQuery.StatisticsMetric.ENTERPRISE_COUNT,
                        StatisticsQuery.GroupByDimension.ALARM_TYPE, START, END, null)));
        assertEquals(AgentErrorCode.VALIDATION_ERROR, e.getCode());
    }
}
