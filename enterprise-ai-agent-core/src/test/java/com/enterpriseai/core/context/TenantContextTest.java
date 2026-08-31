package com.enterpriseai.core.context;

import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantContext 生命周期：set → get/require 可见；clear 后不可见；
 * 未鉴权线程上 require() 必须以 PERMISSION_DENIED 快速失败（多租户铁律的失败方向）。
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireReturnsBoundUser() {
        UserContext user = new UserContext("u1", "tenant-a", "alice", Set.of(), Set.of("enterprise:query"));
        TenantContext.set(user);
        assertEquals(user, TenantContext.get());
        assertEquals(user, TenantContext.require());
        assertEquals("tenant-a", TenantContext.currentTenantId());
        assertTrue(user.hasPermission("enterprise:query"));
    }

    @Test
    void requireFailsWithPermissionDeniedWhenUnset() {
        AgentException e = assertThrows(AgentException.class, TenantContext::require);
        assertEquals(AgentErrorCode.PERMISSION_DENIED, e.getCode());
    }

    @Test
    void clearRemovesContext() {
        TenantContext.set(new UserContext("u1", "tenant-a", "alice", Set.of(), Set.of()));
        TenantContext.clear();
        assertNull(TenantContext.get());
        assertThrows(AgentException.class, TenantContext::require);
    }
}
