package com.enterpriseai.api.security;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请求级身份与租户装配过滤器（多租户铁律的入口执行点）。
 *
 * 信任头模式（Trusted Headers）：生产部署于 API 网关之后，网关完成 JWT 校验并
 * 注入以下头；本服务内网隔离，不重复验签：
 * - X-User-Id（必填）、X-Tenant-Id（必填）
 * - X-User-Name（可选，缺省取 userId）
 * - X-Roles / X-Permissions（可选，逗号分隔）
 *
 * 该过滤器是【唯一的】TenantContext 写入点之一（另一个为 AgentProcessCallback
 * 的跨线程传播），请求结束即清理，杜绝线程池串租户。
 * 权限在这里确定后，后续工具过滤、RAG 过滤、Service 查询全部基于此上下文，
 * LLM 全程不参与权限判定（铁律 2）。
 */
@Component
@Order(1)
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_ROLES = "X-Roles";
    public static final String HEADER_PERMISSIONS = "X-Permissions";

    private final ObjectMapper objectMapper;

    public AuthenticationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 仅保护 Agent API；actuator/静态资源放行
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = header(request, HEADER_USER_ID);
        String tenantId = header(request, HEADER_TENANT_ID);
        if (userId == null || tenantId == null) {
            writeUnauthorized(response);
            return;
        }

        UserContext user = new UserContext(
                userId,
                tenantId,
                headerOrDefault(request, HEADER_USER_NAME, userId),
                splitToSet(request, HEADER_ROLES),
                splitToSet(request, HEADER_PERMISSIONS));

        TenantContext.set(user);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = header(request, name);
        return value == null ? fallback : value;
    }

    private static Set<String> splitToSet(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorResponseShape(
                "PERMISSION_DENIED", "未认证：缺少 X-User-Id / X-Tenant-Id 请求头"));
    }

    /** 过滤器阶段尚无 executionId，用最小错误体。 */
    private record ErrorResponseShape(String code, String userMessage) {
    }
}
