package com.enterpriseai.tool.permission;

import com.enterpriseai.core.context.UserContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具权限评估（默认 DENY 铁律）：
 * 未登记工具不可见；登记权限点后按用户权限判定；* 通配两类。
 */
class ConfigurationBasedToolPermissionEvaluatorTest {

    private static final UserContext ALLOWED =
            new UserContext("u1", "tenant-a", "alice", Set.of(), Set.of("enterprise:query"));
    private static final UserContext FORBIDDEN =
            new UserContext("u2", "tenant-a", "bob", Set.of(), Set.of());

    private final ConfigurationBasedToolPermissionEvaluator evaluator = new ConfigurationBasedToolPermissionEvaluator(
            new ToolPermissionProperties(
                    Map.of("queryEnterprises", "enterprise:query", "internalEcho", "*"),
                    ToolPermissionProperties.Policy.DENY));

    @Test
    void unregisteredToolIsDeniedUnderDefaultDeny() {
        assertFalse(evaluator.canUse(ALLOWED, "brandNewTool"));
    }

    @Test
    void unregisteredToolIsAllowedOnlyWithExplicitAllowPolicy() {
        var allowAll = new ConfigurationBasedToolPermissionEvaluator(
                new ToolPermissionProperties(Map.of(), ToolPermissionProperties.Policy.ALLOW));
        assertTrue(allowAll.canUse(FORBIDDEN, "brandNewTool"));
    }

    @Test
    void userWithPermissionCanUseRegisteredTool() {
        assertTrue(evaluator.canUse(ALLOWED, "queryEnterprises"));
    }

    @Test
    void userWithoutPermissionCannotUseRegisteredTool() {
        assertFalse(evaluator.canUse(FORBIDDEN, "queryEnterprises"));
    }

    @Test
    void wildcardPermissionOrWildcardRequirementGrantsAccess() {
        var superman = new UserContext("u3", "tenant-a", "root", Set.of(), Set.of("*"));
        assertTrue(evaluator.canUse(superman, "queryEnterprises"));
        assertTrue(evaluator.canUse(FORBIDDEN, "internalEcho"));
    }
}
