package com.enterpriseai.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 企业查询条件（LLM 参数提取的输出类型之一）。
 * 所有字段可空：用户未提供的条件必须为 null，禁止猜测。
 * SQL 由 Java Service 层按本对象动态拼装，LLM 不接触 SQL。
 */
@JsonClassDescription("Structured query conditions for searching enterprises")
public record EnterpriseQuery(
        @JsonPropertyDescription("Keyword contained in the enterprise name, null if not specified")
        @Size(max = 64) String nameKeyword,
        @JsonPropertyDescription("City name such as 北京, null if not specified")
        @Size(max = 32) String city,
        @JsonPropertyDescription("Industry name, null if not specified")
        @Size(max = 32) String industry,
        @JsonPropertyDescription("Minimum registered capital in 万 (10k CNY), null if not specified")
        @DecimalMin("0") BigDecimal minRegisteredCapitalWanYuan,
        @JsonPropertyDescription("Max number of rows to return, null if not specified")
        @Max(200) Integer limit) {
}
