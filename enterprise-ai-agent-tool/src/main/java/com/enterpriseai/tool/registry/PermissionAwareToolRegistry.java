package com.enterpriseai.tool.registry;

import com.enterpriseai.core.context.TenantContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.permission.ToolPermissionEvaluator;
import com.enterpriseai.tool.alarm.AlarmTool;
import com.enterpriseai.tool.enterprise.EnterpriseTool;
import com.enterpriseai.tool.kb.KnowledgeTool;
import com.enterpriseai.tool.stats.StatisticsTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 权限感知的工具注册中心。
 *
 * 关键设计：过滤发生在【每次调用】而非启动时——同一 JVM 内不同租户/用户
 * 得到不同的工具集。无权限的工具"不注册"，LLM 完全看不见，而不是注册后拒绝。
 *
 * 用法（Spring AI ChatClient）：
 *   chatClient.prompt().tools(registry.callbacksForCurrentUser())...
 *
 * 用法（Embabel Action 内，1.5.0 机制）：
 *   领域对象方法标注 @LlmTool + ai.withDefaultLlm().withToolChainingFrom(X.class)
 *   （见架构文档；主流程为确定性 Action，不依赖 LLM 自主选工具）。
 */
@Component
public class PermissionAwareToolRegistry {

    private final List<Object> toolObjects;
    private final ToolPermissionEvaluator permissionEvaluator;

    public PermissionAwareToolRegistry(
            EnterpriseTool enterpriseTool,
            AlarmTool alarmTool,
            StatisticsTool statisticsTool,
            KnowledgeTool knowledgeTool,
            ToolPermissionEvaluator permissionEvaluator) {
        this.toolObjects = List.of(enterpriseTool, alarmTool, statisticsTool, knowledgeTool);
        this.permissionEvaluator = permissionEvaluator;
    }

    /** 当前线程用户的可用工具（要求已鉴权上下文）。 */
    public List<ToolCallback> callbacksForCurrentUser() {
        UserContext user = TenantContext.require();
        return Arrays.stream(ToolCallbacks.from(toolObjects.toArray()))
                .filter(cb -> permissionEvaluator.canUse(user, cb.getToolDefinition().name()))
                .toList();
    }
}
