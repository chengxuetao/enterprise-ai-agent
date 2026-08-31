package com.enterpriseai.application.config;

import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessCallback;
import com.embabel.agent.core.Action;
import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把请求线程上的 TenantContext 传播到 Embabel Action 执行线程（CONCURRENT 模式的兜底）。
 *
 * 线程模型事实链（Phase 12 字节码级核实）：
 * - AgentPlatform.start 把整个进程经 Asyncer.async 提交执行；Java 21 下 asyncer 为
 *   newThreadPerTaskExecutor（每任务新建 embabel-virtual 虚拟线程）；
 * - TenantContext 已改为 InheritableThreadLocal ⇒ SIMPLE/CONCURRENT 两种模式下，
 *   Action 线程（由进程线程派生）都会自动继承上下文——本回调在两种模式下均非必需；
 * - 仅当未来把 Action 调度到非派生线程（自定义线程池/远端执行器）时，本回调按
 *   官方钩子补齐传播：beforeActionLaunched 在进程线程捕获，onActionLaunched 在
 *   Action 线程恢复，onActionCompleted 清理。
 *
 * 无实例态设计：回调实例由平台单例持有（跨所有进程共享），上下文快照按 AgentProcess
 * 身份存入 ConcurrentHashMap——并发进程互不串写（多租户铁律），完成即移除防泄漏。
 */
@Component
public class TenantContextAgentProcessCallback implements AgentProcessCallback {

    private final Map<AgentProcess, UserContext> capturedByProcess = new ConcurrentHashMap<>();

    @Override
    public void beforeActionLaunched(AgentProcess process) {
        UserContext current = TenantContext.get();
        if (current != null) {
            capturedByProcess.put(process, current);
        }
    }

    @Override
    public void onActionLaunched(AgentProcess process, Action action) {
        // 每个 Action 启动前恢复：即使上一 Action 完成时清理过也能续上
        UserContext captured = capturedByProcess.get(process);
        if (captured != null) {
            TenantContext.set(captured);
        }
    }

    @Override
    public void onActionCompleted(AgentProcess process, Action action) {
        capturedByProcess.remove(process);
        TenantContext.clear();
    }
}
