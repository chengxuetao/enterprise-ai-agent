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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常收口：任何异常都不把堆栈、SQL、内部类名返回给客户端——
 * 对外只有 错误码 + 用户可读信息 + 是否可重试 + executionId（可查审计）。
 * 完整异常细节进服务端日志（与 executionId 关联）。
 *
 * 映射（均基于已核实的异常类型）：
 * - AgentException       → 自带 7 类错误码（LLM_ERROR/TOOL_ERROR/VALIDATION_ERROR/
 *                          PERMISSION_DENIED/RAG_ERROR/PLANNING_ERROR/BUSINESS_ERROR）
 * - ActionException      → TOOL_ERROR；Transient 子类可重试（Embabel 官方重试语义）
 * - 参数校验失败          → VALIDATION_ERROR（400）
 * - 其他                 → BUSINESS_ERROR（500，兜底）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        // Embabel Action 失败：Transient = 官方标记的可重试（如 LLM 网络抖动）
        boolean retryable = e instanceof ActionException.Transient;
        log.error("ActionException(transient={}) executionId={}: {}",
                retryable, executionId(request), e.getMessage(), e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(executionId(request), AgentErrorCode.TOOL_ERROR.name(),
                        AgentErrorCode.TOOL_ERROR.getUserMessage(), retryable));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e,
                                                          HttpServletRequest request) {
        // 兜底：日志保留完整堆栈，对外仅返回通用信息
        log.error("未预期异常 executionId={}", executionId(request), e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse(executionId(request),
                        AgentErrorCode.BUSINESS_ERROR.name(),
                        AgentErrorCode.BUSINESS_ERROR.getUserMessage(), false));
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
