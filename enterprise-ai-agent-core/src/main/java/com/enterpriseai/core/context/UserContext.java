package com.enterpriseai.core.context;

import java.util.Set;

/**
 * 已认证用户的不可变上下文快照。
 * 由鉴权过滤器构建（Phase 9），随请求线程传播。
 */
public record UserContext(
        String userId,
        String tenantId,
        String username,
        Set<String> roles,
        Set<String> permissions) {

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
