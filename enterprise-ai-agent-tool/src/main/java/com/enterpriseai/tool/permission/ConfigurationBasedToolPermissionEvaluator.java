package com.enterpriseai.tool.permission;

import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.permission.ToolPermissionEvaluator;
import org.springframework.stereotype.Component;

/**
 * 基于配置的权限评估器（默认实现）。
 *
 * 判断顺序：
 * 1. 工具未登记权限点 → 按 default-policy（缺省 DENY，新工具必须显式授权才可暴露）；
 * 2. 配置为 "*" → 放行（内部演示工具可用）；
 * 3. 用户拥有所需权限点或 "*" → 放行。
 *
 * 企业内可替换为基于 RBAC 表/权限中心的实现，接口不变（core.ToolPermissionEvaluator）。
 */
@Component
public class ConfigurationBasedToolPermissionEvaluator implements ToolPermissionEvaluator {

    private final ToolPermissionProperties properties;

    public ConfigurationBasedToolPermissionEvaluator(ToolPermissionProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean canUse(UserContext user, String toolName) {
        String required = properties.toolPermissions().get(toolName);
        if (required == null || required.isBlank()) {
            return properties.defaultPolicy() == ToolPermissionProperties.Policy.ALLOW;
        }
        if ("*".equals(required)) {
            return true;
        }
        return user.hasPermission(required) || user.hasPermission("*");
    }
}
