package com.enterpriseai.tool.enterprise;

import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;
import com.enterpriseai.domain.service.EnterpriseService;
import com.enterpriseai.tool.ToolUsage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 企业查询工具（LLM Tool Calling 入口）。
 *
 * 契约（对应架构文档第 13 节）：
 * - Tool Name:  queryEnterprises
 * - Description: 按可选条件查询企业信息
 * - Input Schema:  EnterpriseQuery（强类型 record，未提及的字段必须为 null）
 * - Output Schema: EnterpriseResult（列表 + 总数 + 是否截断）
 * - Permission:    enterprise:query（缺省拒绝，见 agent.tools.permission）
 * - 安全：租户条件由 EnterpriseServiceImpl 从 TenantContext 注入，LLM 无法指定；
 *         本工具不暴露 DataSource/Repository/SQL——只暴露业务语义。
 */
@Component
public class EnterpriseTool {

    public static final String QUERY_ENTERPRISES = "queryEnterprises";

    private final EnterpriseService enterpriseService;

    public EnterpriseTool(EnterpriseService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @Tool(name = QUERY_ENTERPRISES, description = """
            Query enterprise information by optional conditions: name keyword, city, industry,
            and minimum registered capital (in 万/10k CNY). Returns a list of enterprises
            with total count. Only fields explicitly requested by the user should be set;
            leave unspecified fields null.""")
    public EnterpriseResult queryEnterprises(
            @ToolParam(description = "Structured query conditions; unspecified fields must be null")
            EnterpriseQuery query) {
        EnterpriseResult result = enterpriseService.queryEnterprises(query);
        ToolUsage.record(QUERY_ENTERPRISES, result);
        return result;
    }
}
