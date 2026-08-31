package com.enterpriseai.tool.alarm;

import com.enterpriseai.domain.model.AlarmQuery;
import com.enterpriseai.domain.model.AlarmResult;
import com.enterpriseai.domain.service.AlarmService;
import com.enterpriseai.tool.ToolUsage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 告警查询工具。
 *
 * 契约：
 * - Tool Name:  queryAlarms
 * - Input Schema:  AlarmQuery（时间/企业/类型/等级/条数，均可空）
 * - Output Schema: AlarmResult
 * - Permission:    alarm:query
 * - 安全：租户由 Service 层强制；alarmLevel 为闭集枚举。
 */
@Component
public class AlarmTool {

    public static final String QUERY_ALARMS = "queryAlarms";

    private final AlarmService alarmService;

    public AlarmTool(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @Tool(name = QUERY_ALARMS, description = """
            Query alarm records by optional conditions: time range (ISO-8601), enterprise name,
            alarm type, and alarm level (CRITICAL/HIGH/MEDIUM/LOW). Returns alarms ordered by
            occurrence time descending, with total count. Leave unspecified fields null.""")
    public AlarmResult queryAlarms(
            @ToolParam(description = "Structured alarm query conditions; unspecified fields must be null")
            AlarmQuery query) {
        AlarmResult result = alarmService.queryAlarms(query);
        ToolUsage.record(QUERY_ALARMS, result);
        return result;
    }
}
