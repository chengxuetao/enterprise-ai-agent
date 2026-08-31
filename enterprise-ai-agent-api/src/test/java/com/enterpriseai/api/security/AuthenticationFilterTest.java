package com.enterpriseai.api.security;

import com.enterpriseai.core.context.TenantContext;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鉴权过滤器（租户上下文唯一入口）：
 * 带头 → 链路内可见 UserContext（含权限解析）；缺头 → 401；
 * 请求结束 → ThreadLocal 必被清理（线程池复用不串租户）。
 */
class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter(new ObjectMapper());
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void buildsUserContextFromTrustedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
        request.addHeader(AuthenticationFilter.HEADER_USER_ID, "u1");
        request.addHeader(AuthenticationFilter.HEADER_TENANT_ID, "tenant-a");
        request.addHeader(AuthenticationFilter.HEADER_USER_NAME, "alice");
        request.addHeader(AuthenticationFilter.HEADER_PERMISSIONS, "enterprise:query, alarm:query");
        request.addHeader(AuthenticationFilter.HEADER_ROLES, "OPERATOR");

        AtomicReference<String> seenTenant = new AtomicReference<>();
        AtomicReference<Boolean> seenPermission = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenTenant.set(TenantContext.currentTenantId());
            seenPermission.set(TenantContext.require().hasPermission("alarm:query"));
        };

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("tenant-a", seenTenant.get());
        assertTrue(seenPermission.get(), "逗号分隔权限应被解析为集合");
        assertNull(TenantContext.get(), "请求结束后必须清理 ThreadLocal");
    }

    @Test
    void rejectsMissingIdentityHeadersWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");

        AtomicBoolean reachedController = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> reachedController.set(true);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("PERMISSION_DENIED"));
        assertFalse(reachedController.get(), "未认证请求不得进入后续链路");
    }

    @Test
    void nonApiPathsPassThroughWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        AtomicReference<String> seenUri = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenUri.set(((HttpServletRequest) req).getRequestURI());

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("/actuator/health", seenUri.get());
    }
}
