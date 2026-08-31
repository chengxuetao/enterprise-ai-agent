package com.enterpriseai.infra.persistence.repository;

import com.enterpriseai.infra.persistence.entity.AlarmEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警仓储。动态条件查询走 Specification；统计聚合走下方 JPQL。
 *
 * 统计口径（与 StatisticsServiceImpl 约定）：
 * - startTime/endTime 由服务层兜底为极值，本接口参数永不为 null；
 * - 分组统计返回 [dimension, count] 元组，按 count 降序；MIN 口径由服务层反向排序；
 * - "按天" 分组使用 substring(cast(occurred_at as string),1,10)（MySQL datetime 标准格式前 10 位）。
 */
public interface AlarmRepository
        extends JpaRepository<AlarmEntity, Long>, JpaSpecificationExecutor<AlarmEntity> {

    @Query("""
            select count(a) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            """)
    long countTotalAlarms(@Param("tenantId") String tenantId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    @Query("""
            select count(distinct a.enterpriseId) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            """)
    long countDistinctEnterprises(@Param("tenantId") String tenantId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    @Query("""
            select a.enterpriseName, count(a) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            group by a.enterpriseName order by count(a) desc
            """)
    List<Object[]> countGroupByEnterprise(@Param("tenantId") String tenantId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    @Query("""
            select a.alarmType, count(a) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            group by a.alarmType order by count(a) desc
            """)
    List<Object[]> countGroupByAlarmType(@Param("tenantId") String tenantId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime);

    @Query("""
            select a.alarmLevel, count(a) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            group by a.alarmLevel order by count(a) desc
            """)
    List<Object[]> countGroupByAlarmLevel(@Param("tenantId") String tenantId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    @Query("""
            select substring(cast(a.occurredAt as string), 1, 10), count(a) from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            group by substring(cast(a.occurredAt as string), 1, 10)
            order by count(a) desc
            """)
    List<Object[]> countGroupByDay(@Param("tenantId") String tenantId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    @Query("""
            select substring(cast(a.occurredAt as string), 1, 10), count(distinct a.enterpriseId)
            from AlarmEntity a
            where a.tenantId = :tenantId and a.occurredAt >= :startTime and a.occurredAt <= :endTime
            group by substring(cast(a.occurredAt as string), 1, 10)
            order by count(distinct a.enterpriseId) desc
            """)
    List<Object[]> countDistinctEnterpriseGroupByDay(@Param("tenantId") String tenantId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);
}
