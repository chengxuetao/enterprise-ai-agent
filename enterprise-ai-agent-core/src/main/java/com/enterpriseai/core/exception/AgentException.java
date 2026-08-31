package com.enterpriseai.core.exception;

/**
 * Agent 域统一异常。
 * 内部细节记录在日志与审计（errorMessage），对外只暴露 code.userMessage。
 */
public class AgentException extends RuntimeException {

    private final AgentErrorCode code;

    public AgentException(AgentErrorCode code, String internalMessage) {
        super(internalMessage);
        this.code = code;
    }

    public AgentException(AgentErrorCode code, String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.code = code;
    }

    public AgentErrorCode getCode() {
        return code;
    }

    public String getUserMessage() {
        return code.getUserMessage();
    }

    public boolean isRetryable() {
        return code.isRetryable();
    }
}
