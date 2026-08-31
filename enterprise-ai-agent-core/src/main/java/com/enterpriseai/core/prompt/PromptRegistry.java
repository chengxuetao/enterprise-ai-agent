package com.enterpriseai.core.prompt;

import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板注册中心。
 *
 * 约定：
 * - 模板位于 classpath:/prompts/{category}/{name}-v{version}.st
 * - 占位符形如 {question}（ST4 风格），本类做显式替换，不引入模板引擎依赖；
 * - 调用方在代码中显式声明【基线】版本；实际生效版本 = 配置覆盖
 *   （agent.prompts.active-versions.{category/name}）优先，否则基线版本，
 *   实现"加文件 + 改一行配置"即可灰度新 Prompt，回滚即删配置；
 * - 模板缺失属于配置错误，快速失败。
 */
@Component
public class PromptRegistry {

    private final PromptVersionProperties versionProperties;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public PromptRegistry(PromptVersionProperties versionProperties) {
        this.versionProperties = versionProperties;
    }

    /** 实际生效的版本号（供审计/日志记录使用）。 */
    public int activeVersion(String category, String name, int codeVersion) {
        return versionProperties.resolve(category, name, codeVersion);
    }

    public String render(String category, String name, int version, Map<String, String> variables) {
        int effective = versionProperties.resolve(category, name, version);
        String key = category + "/" + name + "-v" + effective + ".st";
        String template = cache.computeIfAbsent(key, this::load);
        String result = template;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            result = result.replace("{" + e.getKey() + "}", value);
        }
        return result;
    }

    private String load(String path) {
        try (InputStream in = PromptRegistry.class.getResourceAsStream("/prompts/" + path)) {
            if (in == null) {
                throw new AgentException(AgentErrorCode.LLM_ERROR,
                        "Prompt template not found on classpath: prompts/" + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AgentException(AgentErrorCode.LLM_ERROR, "Failed to read prompt template: " + path, e);
        }
    }
}
