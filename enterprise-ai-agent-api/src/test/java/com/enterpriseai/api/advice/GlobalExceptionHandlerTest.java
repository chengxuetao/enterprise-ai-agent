package com.enterpriseai.api.advice;

import com.embabel.agent.core.ActionException;
import com.enterpriseai.api.dto.ErrorResponse;
import com.openai.errors.FakeSdkException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 错误分类契约（§14.3-a/b）：
 * - cause 链含 Spring AI retry 异常 / openai-java SDK 异常 → LLM_ERROR（Transient 可重试）
 * - ActionException 无 LLM 签名 → TOOL_ERROR（Embabel Transient 子类可重试）
 * - 请求体不可读 → 400 VALIDATION_ERROR
 * - 其余 → BUSINESS_ERROR 兜底
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void executionExceptionWrappingTransientAiFailureMapsToLlmError() {
        // 真实链路形态：AgentInvocation.invoke 经 CompletableFuture.get() 上抛 ExecutionException
        ExecutionException e = new ExecutionException(new ActionException("action failed",
                new TransientAiException("connect timeout")));

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(e, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("LLM_ERROR", response.getBody().code());
        assertTrue(response.getBody().retryable());
    }

    @Test
    void openAiSdkFailureInCauseChainMapsToLlmError() {
        // openai-java SDK 异常以包前缀识别（生产分类器不依赖 SDK 编译类型，
        // 测试以同名包下的伪装类验证前缀匹配）
        RuntimeException e = new RuntimeException(new FakeSdkException("401 unauthorized"));

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(e, request);

        assertEquals("LLM_ERROR", response.getBody().code());
        assertFalse(response.getBody().retryable());
    }

    @Test
    void nonTransientAiFailureMapsToLlmErrorNotRetryable() {
        ActionException e = new ActionException("llm rejected",
                new NonTransientAiException("401"));

        ResponseEntity<ErrorResponse> response = handler.handleActionException(e, request);

        assertEquals("LLM_ERROR", response.getBody().code());
        assertFalse(response.getBody().retryable());
    }

    @Test
    void actionExceptionWithoutLlmSignatureKeepsToolErrorSemantics() {
        // Kotlin 默认参数在 Java 侧需显式传 null
        ActionException.Transient e = new ActionException.Transient("db down", null);

        ResponseEntity<ErrorResponse> response = handler.handleActionException(e, request);

        assertEquals("TOOL_ERROR", response.getBody().code());
        assertTrue(response.getBody().retryable());
    }

    @Test
    void unrelatedExceptionStillFallsBackToBusinessError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("boom"), request);

        assertEquals("BUSINESS_ERROR", response.getBody().code());
        assertFalse(response.getBody().retryable());
    }

    @Test
    void unreadableBodyMapsTo400ValidationError() {
        HttpMessageNotReadableException e =
                new HttpMessageNotReadableException("bad json", mock(org.springframework.http.HttpInputMessage.class));

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(e, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertFalse(response.getBody().retryable());
    }
}
