package com.enterpriseai.domain.service;

import com.enterpriseai.domain.model.StatisticsQuery;
import com.enterpriseai.domain.model.StatisticsResult;

/**
 * 统计领域服务：聚合计算全部在数据库/Java 侧完成，LLM 不参与计算。
 */
public interface StatisticsService {

    StatisticsResult computeStatistics(StatisticsQuery query);
}
