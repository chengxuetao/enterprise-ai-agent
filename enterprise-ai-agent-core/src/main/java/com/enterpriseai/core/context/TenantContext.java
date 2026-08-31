package com.enterpriseai.core.context;

import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;

/**
 * 租户/用户上下文，基于 InheritableThreadLocal。
 *
 * 为什么必须是 Inheritable（Phase 12 实测发现）：Embabel 1.5 的 AgentPlatform.start
 * 把整个进程提交到 asyncer 执行——Java 21 下为 Executors.newThreadPerTaskExecutor
 * （每任务新建 embabel-virtual 虚拟线程），SIMPLE/CONCURRENT 两种模式的 Action 都不在
 * 请求线程上跑。普通 ThreadLocal 在此全部丢失（症状：require() 抛
 * "No authenticated user context bound to current thread"）。
 * 每任务新建线程 ⇒ 子线程创建时自动继承快照；线程不复用 ⇒ 无跨请求残留，
 * InheritableThreadLocal 的经典线程池脏读风险在本执行模型下不存在。
 * （UserContext 为不可变 record，引用共享无变异风险。）
 *
 * 生命周期约定：
 * - 鉴权过滤器在请求入口 set，请求结束（finally）必须 clear；
 * - 上下文对象一旦 set 不允许修改字段值（immutable 快照）；
 * - 数据层与 RAG 层一律通过本类获取 tenantId，禁止从用户输入或 LLM 输出取租户。
 */
public final class TenantContext {

    private static final InheritableThreadLocal<UserContext> HOLDER = new InheritableThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UserContext userContext) {
        HOLDER.set(userContext);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    /** 要求当前线程必须存在已认证上下文，否则抛出 PERMISSION_DENIED。 */
    public static UserContext require() {
        UserContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new AgentException(AgentErrorCode.PERMISSION_DENIED,
                    "No authenticated user context bound to current thread");
        }
        return ctx;
    }

    public static String currentTenantId() {
        UserContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.tenantId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
