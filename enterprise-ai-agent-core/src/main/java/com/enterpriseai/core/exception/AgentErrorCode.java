package com.enterpriseai.core.exception;

/** Agent 统一错误码。userMessage 为可直接展示给终端用户的文案，不含任何内部技术细节。 */
public enum AgentErrorCode {

    LLM_ERROR("AI 服务暂时不可用，请稍后重试", true),
    TOOL_ERROR("业务查询暂时不可用，请稍后重试", true),
    VALIDATION_ERROR("请求参数不合法，请检查后重试", false),
    PERMISSION_DENIED("您没有执行此操作的权限", false),
    RAG_ERROR("知识库检索暂时不可用，请稍后重试", true),
    PLANNING_ERROR("暂时无法理解您的请求，请换一种说法试试", false),
    BUSINESS_ERROR("业务处理失败，请稍后重试", true);

    private final String userMessage;
    private final boolean retryable;

    AgentErrorCode(String userMessage, boolean retryable) {
        this.userMessage = userMessage;
        this.retryable = retryable;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
