package com.openai.errors;

/**
 * openai-java SDK 异常的测试替身（仅存在于 test classpath）。
 * GlobalExceptionHandler 以 "com.openai." 包前缀识别 SDK 异常而不做编译期依赖，
 * 本类用于验证该前缀匹配在真实 SDK 异常（如 com.openai.errors.OpenAIException）
 * 到达时按 LLM_ERROR 分类。
 */
public class FakeSdkException extends RuntimeException {

    public FakeSdkException(String message) {
        super(message);
    }
}
