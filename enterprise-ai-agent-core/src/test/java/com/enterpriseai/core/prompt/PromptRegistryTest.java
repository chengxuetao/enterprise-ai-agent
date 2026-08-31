package com.enterpriseai.core.prompt;

import com.enterpriseai.core.exception.AgentErrorCode;
import com.enterpriseai.core.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prompt 版本管理机制：
 * 基线版本渲染 + 变量替换；配置覆盖生效（灰度）；模板缺失快速失败。
 */
class PromptRegistryTest {

    @Test
    void rendersBaselineVersionAndSubstitutesVariables() {
        PromptRegistry registry = new PromptRegistry(new PromptVersionProperties(Map.of()));
        String rendered = registry.render("test", "demo", 1, Map.of("name", "世界"));
        assertEquals("Role: test\nHello 世界, this is version 1.", rendered.stripTrailing());
        assertEquals(1, registry.activeVersion("test", "demo", 1));
    }

    @Test
    void configOverrideSelectsDifferentVersion() {
        PromptRegistry gray = new PromptRegistry(
                new PromptVersionProperties(Map.of("test/demo", 2)));
        String rendered = gray.render("test", "demo", 1, Map.of("name", "世界"));
        assertEquals("Role: test\nHello 世界, this is version 2.", rendered.stripTrailing());
        assertEquals(2, gray.activeVersion("test", "demo", 1));
    }

    @Test
    void missingTemplateFailsFastWithLlmError() {
        PromptRegistry registry = new PromptRegistry(new PromptVersionProperties(Map.of()));
        AgentException e = assertThrows(AgentException.class,
                () -> registry.render("test", "no-such-prompt", 1, Map.of()));
        assertEquals(AgentErrorCode.LLM_ERROR, e.getCode());
    }
}
