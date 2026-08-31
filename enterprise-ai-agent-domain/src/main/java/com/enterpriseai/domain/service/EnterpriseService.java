package com.enterpriseai.domain.service;

import com.enterpriseai.domain.model.EnterpriseQuery;
import com.enterpriseai.domain.model.EnterpriseResult;

/**
 * 企业查询领域服务。
 * 实现方（infrastructure 模块）必须：
 * 1. 基于 TenantContext.currentTenantId() 强制追加租户条件，禁止跨租户数据泄漏；
 * 2. 所有 SQL 条件来自强类型 EnterpriseQuery，禁止接收外部 SQL 片段。
 */
public interface EnterpriseService {

    EnterpriseResult queryEnterprises(EnterpriseQuery query);
}
