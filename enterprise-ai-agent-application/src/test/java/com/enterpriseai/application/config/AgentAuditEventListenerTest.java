package com.enterpriseai.application.config;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.ActionExecutionStartEvent;
import com.embabel.agent.core.Action;
import com.embabel.agent.core.ActionStatusCode;
import com.embabel.agent.core.ActionStatus;
import com.embabel.agent.core.AgentProcess;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.context.UserContext;
import com.enterpriseai.core.observation.AgentExecutionRecorder;
import com.enterpriseai.domain.model.UserRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Action 审计事件监听器契约（Phase 13 补线）：
 * ActionExecutionResultEvent → recorder.onActionComplete（含失败标记）；
 * 无执行上下文的事件跳过（审计主键需要 executionId）。
 *
 * 事件经公共工厂 ActionExecutionStartEvent.resultEvent(...) 构造
 * （ActionExecutionResultEvent 的构造器为 Kotlin internal，勿直接 new）。
 * IoBinding 是 Kotlin 值类（Java 无法构造实例），产出类型解析由 typePart 直测覆盖。
 */
class AgentAuditEventListenerTest {

    private final AgentExecutionRecorder recorder = mock(AgentExecutionRecorder.class);
    private final AgentAuditEventListener listener = new AgentAuditEventListener(recorder);

    @AfterEach
    void cleanThreadLocal() {
        AgentExecutionContext.clear();
    }

    @Test
    void successfulActionResultIsMappedToAuditCallback() {
        UserContext user = new UserContext("u1", "tenant-a", "u1", Set.of(), Set.of());
        AgentExecutionContext context = AgentExecutionContext.begin(user, new UserRequest("查企业", "s1"));

        listener.onProcessEvent(resultEvent(mockAction("recognizeIntent"), ActionStatusCode.SUCCEEDED));

        verify(recorder).onActionComplete(eq(context.executionId()), eq("recognizeIntent"),
                eq(""), anyLong(), isNull());
    }

    @Test
    void failedActionResultCarriesErrorMarker() {
        UserContext user = new UserContext("u1", "tenant-a", "u1", Set.of(), Set.of());
        AgentExecutionContext context = AgentExecutionContext.begin(user, new UserRequest("查企业", "s1"));

        listener.onProcessEvent(resultEvent(mockAction("queryEnterprises"), ActionStatusCode.FAILED));

        verify(recorder).onActionComplete(eq(context.executionId()), eq("queryEnterprises"),
                eq(""), anyLong(), eq("Action failed (see server logs)"));
    }

    @Test
    void eventWithoutExecutionContextIsSkipped() {
        listener.onProcessEvent(resultEvent(mockAction("recognizeIntent"), ActionStatusCode.SUCCEEDED));

        verifyNoInteractions(recorder);
    }

    @Test
    void unrelatedProcessEventIsIgnored() {
        UserContext user = new UserContext("u1", "tenant-a", "u1", Set.of(), Set.of());
        AgentExecutionContext.begin(user, new UserRequest("查企业", "s1"));

        // 非 Action 执行结果事件不触发审计
        listener.onProcessEvent(new com.embabel.agent.api.event.ObjectAddedEvent(
                mock(com.embabel.agent.core.AgentProcess.class), new Object()));

        verifyNoInteractions(recorder);
    }

    @Test
    void typePartStripsBindingNamePrefix() {
        assertEquals("com.enterpriseai.domain.model.IntentResult",
                AgentAuditEventListener.typePart("it:com.enterpriseai.domain.model.IntentResult"));
        assertEquals("com.example.Foo", AgentAuditEventListener.typePart("result:com.example.Foo"));
        assertEquals("TypeWithoutName", AgentAuditEventListener.typePart("TypeWithoutName"));
    }

    private static ActionExecutionResultEvent resultEvent(Action action, ActionStatusCode status) {
        // 工厂以起始事件时间戳推导 runningTime（测试中近似 0ms），断言用 anyLong()
        return new ActionExecutionStartEvent(mock(AgentProcess.class), action)
                .resultEvent(new ActionStatus(Duration.ofMillis(1), status));
    }

    private static Action mockAction(String name) {
        Action action = mock(Action.class);
        when(action.getName()).thenReturn(name);
        when(action.getOutputs()).thenReturn(Set.of());
        return action;
    }
}
