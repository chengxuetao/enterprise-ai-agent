package com.enterpriseai.api.advice;

import com.embabel.agent.core.ActionException;
import com.enterpriseai.api.controller.AgentChatController;
import com.enterpriseai.api.dto.ErrorResponse;
import com.enterpriseai.core.context.AgentExecutionContext;
import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常收口：任何异常都不把堆栈、SQL、内部类名返回给客户端——
 * 对外只有 错误码 + 用户可读信息 + 是否可重试 + executionId（可查审计）。
 * 完整异常细节进服务端日志（与 executionId 关联）。
 *
 * 映射（均基于已核实的异常类型，Phase 13 增强）：
 * - AgentException              → 自带 7 类错误码（LLM_ERROR/TOOL_ERROR/VALIDATION_ERROR/
 *                                 PERMISSION_DENIED/RAG_ERROR/PLANNING_ERROR/BUSINESS_ERROR）
 * - ActionException             → cause 链含 LLM 失败签名（Spring AI retry 异常 /
 *                                 openai-java SDK 异常）→ LLM_ERROR（Transient 可重试）；
 *                                 其余 → TOOL_ERROR（Transient 可重试，Embabel 官方语义）
 * - 参数校验失败 / 请求体不可读   → VALIDATION_ERROR（400）
 * - 其他                        → 同一 LLM 签名探测，命中 → LLM_ERROR；否则 BUSINESS_ERROR（500 兜底）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** openai-java SDK 异常的包前缀（经 Embabel 的 OpenAI 通道抛出）。 */
    private static final String OPENAI_SDK_PACKAGE = "com.openai.";

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ErrorResponse> handleAgentException(AgentException e,
                                                              HttpServletRequest request) {
        log.error("AgentException code={} executionId={}: {}",
                e.getCode(), executionId(request), e.getMessage(), e);
        return ResponseEntity.status(httpStatus(e.getCode()))
                .body(new ErrorResponse(executionId(request), e.getCode().name(),
                        e.getUserMessage(), e.isRetryable()));
    }

    @ExceptionHandler(ActionException.class)
    public ResponseEntity<ErrorResponse> handleActionException(ActionException e,
                                                               HttpServletRequest request) {
        // LLM 失败分类（§14.3-a）：cause 链命中 Spring AI retry 异常或 openai-java SDK 异常
        // → LLM_ERROR（用户文案"AI 服务暂时不可用"）；否则维持 TOOL_ERROR 语义。
        LlmFailure failure = diagnoseLlmFailure(e);
        boolean retryable = failure == LlmFailure.TRANSIENT
                || (failure == LlmFailure.NONE && e instanceof ActionException.Transient);
        AgentErrorCode code = llmErrorCode(failure, AgentErrorCode.TOOL_ERROR);
        log.error("ActionException(llmFailure={}, transient={}) executionId={}: {}",
                failure, retryable, executionId(request), e.getMessage(), e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(executionId(request), code.name(),
                        code.getUserMessage(), retryable));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败 executionId={}: {}", executionId(request), detail);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(executionId(request),
                        AgentErrorCode.VALIDATION_ERROR.name(), "请求参数不合法", false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                          HttpServletRequest request) {
        // 请求体缺失/JSON 畸形属于客户端错误（§14.3-b：此前落入 500 兜底）
        log.warn("请求体不可读 executionId={}: {}", executionId(request), e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(executionId(request),
                        AgentErrorCode.VALIDATION_ERROR.name(), "请求体格式不正确", false));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e,
                                                          HttpServletRequest request) {
        // 兜底：日志保留完整堆栈，对外仅返回通用信息；
        // 命中 LLM 失败签名时按 LLM_ERROR 分类（§14.3-a）
        log.error("未预期异常 executionId={}", executionId(request), e);
        LlmFailure failure = diagnoseLlmFailure(e);
        AgentErrorCode code = llmErrorCode(failure, AgentErrorCode.BUSINESS_ERROR);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(executionId(request), code.name(),
                        code.getUserMessage(), failure == LlmFailure.TRANSIENT));
    }

    /** LLM 失败签名判定结果。 */
    private enum LlmFailure {
        /** cause 链无 LLM 签名 */
        NONE,
        /** TransientAiException：官方标记的瞬时故障（网络抖动/限流） */
        TRANSIENT,
        /** 其余 LLM 签名（NonTransientAiException / openai-java SDK 异常） */
        NON_TRANSIENT
    }

    /** 命中 LLM 签名 → LLM_ERROR；否则回落到调用方给定的兜底码。 */
    private static AgentErrorCode llmErrorCode(LlmFailure failure, AgentErrorCode fallback) {
        return failure == LlmFailure.NONE ? fallback : AgentErrorCode.LLM_ERROR;
    }

    /**
     * 沿 cause 链（含自身）探测 LLM 失败签名。
     * 异常类型签名核实自 Embabel 1.5.3 LlmRetryDecision 源码
     * （Spring AI HTTP 层抛 Transient/NonTransientAiException；
     * openai-java SDK 异常以包前缀匹配，避免编译期依赖 SDK 类型）。
     */
    private static LlmFailure diagnoseLlmFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof TransientAiException) {
                return LlmFailure.TRANSIENT;
            }
            if (t instanceof NonTransientAiException
                    || t.getClass().getName().startsWith(OPENAI_SDK_PACKAGE)) {
                return LlmFailure.NON_TRANSIENT;
            }
        }
        return LlmFailure.NONE;
    }

    /** 错误码 → HTTP 状态：权限 403、参数 400、其余 500。 */
    private static int httpStatus(AgentErrorCode code) {
        return switch (code) {
            case PERMISSION_DENIED -> 403;
            case VALIDATION_ERROR -> 400;
            default -> 500;
        };
    }

    /** 优先取控制器挂到请求属性上的 executionId；过滤器阶段无上下文则取 ThreadLocal。 */
    private static String executionId(HttpServletRequest request) {
        Object attr = request.getAttribute(AgentChatController.ATTR_EXECUTION_ID);
        if (attr != null) {
            return String.valueOf(attr);
        }
        AgentExecutionContext ctx = AgentExecutionContext.current();
        return ctx == null ? null : ctx.executionId();
    }
}
