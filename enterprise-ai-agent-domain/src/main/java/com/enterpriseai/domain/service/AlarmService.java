package com.enterpriseai.domain.service;

import com.enterpriseai.domain.model.AlarmQuery;
import com.enterpriseai.domain.model.AlarmResult;

/**
 * 告警查询领域服务。实现方必须强制租户隔离（同 EnterpriseService）。
 */
public interface AlarmService {

    AlarmResult queryAlarms(AlarmQuery query);
}
