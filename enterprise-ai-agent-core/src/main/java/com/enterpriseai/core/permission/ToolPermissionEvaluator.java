package com.enterpriseai.core.permission;

import com.enterpriseai.core.context.UserContext;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具权限门控：决定某个用户的哪些工具可以暴露给 LLM。
 *
 * 契约：
 * - 判断依据只能是后端权限数据（角色/权限点），绝不依赖 Prompt 约束；
 * - 未授权的工具必须"不注册"，让 LLM 根本看不见，而不是注册后提示拒绝。
 */
public interface ToolPermissionEvaluator {

    boolean canUse(UserContext user, String toolName);

    default Set<String> filterAllowed(UserContext user, Set<String> toolNames) {
        return toolNames.stream()
                .filter(t -> canUse(user, t))
                .collect(Collectors.toSet());
    }
}
