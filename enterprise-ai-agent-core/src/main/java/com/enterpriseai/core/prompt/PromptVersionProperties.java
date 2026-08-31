package com.enterpriseai.core.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Prompt 活动版本配置（灰度开关）。
 *
 * key 为 "{category}/{name}"，value 为覆盖后的版本号。未配置的 Prompt 使用
 * 代码中声明的基线版本——即"改配置即可灰度，不改代码"：
 *
 * <pre>
 * agent:
 *   prompts:
 *     active-versions:
 *       intent/intent-recognition: 2   # 类路径需存在 intent-recognition-v2.st
 * </pre>
 *
 * 版本切换是无状态的：调用点传基线版本，本配置决定实际渲染版本，
 * 因此回滚 = 删掉一行配置。
 */
@ConfigurationProperties(prefix = "agent.prompts")
public record PromptVersionProperties(Map<String, Integer> activeVersions) {

    public PromptVersionProperties {
        activeVersions = activeVersions == null ? Map.of() : Map.copyOf(activeVersions);
    }

    /** 返回实际生效的版本：配置覆盖优先，否则代码声明版本。 */
    public int resolve(String category, String name, int codeVersion) {
        Integer overridden = activeVersions.get(category + "/" + name);
        return overridden == null ? codeVersion : overridden;
    }
}
