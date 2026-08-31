package com.enterpriseai.tool.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 工具权限映射配置。
 *
 * tool-permissions：工具名 → 所需权限点（支持 "*" 表示放行所有用户）。
 * default-policy：  未在映射中的工具的缺省策略（DENY 更安全，新增工具必须显式授权）。
 *
 * 示例（application.yml）：
 * agent.tools.permission.tool-permissions.queryEnterprises: enterprise:query
 */
@ConfigurationProperties(prefix = "agent.tools.permission")
public record ToolPermissionProperties(
        Map<String, String> toolPermissions,
        Policy defaultPolicy) {

    public enum Policy { ALLOW, DENY }

    public ToolPermissionProperties {
        if (toolPermissions == null) {
            toolPermissions = Map.of();
        }
        if (defaultPolicy == null) {
            defaultPolicy = Policy.DENY;
        }
    }
}
