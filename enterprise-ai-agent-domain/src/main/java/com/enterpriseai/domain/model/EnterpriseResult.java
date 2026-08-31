package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.math.BigDecimal;
import java.util.List;

/**
 * 企业查询结果：由 Java Service 产出。
 * LLM 在 DataAnalysisAction 中只读分析，不允许修改或编造其中数据。
 */
@JsonClassDescription("Query result containing a list of enterprises")
public record EnterpriseResult(
        List<EnterpriseSummary> enterprises,
        int total,
        boolean truncated) {

    @JsonClassDescription("Summary of a single enterprise")
    public record EnterpriseSummary(
            Long id,
            String name,
            String city,
            String industry,
            BigDecimal registeredCapitalWanYuan,
            String legalPerson,
            String status) {
    }
}
