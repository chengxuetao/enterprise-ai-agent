package com.enterpriseai.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Enterprise AI Agent 启动类。
 * 组件扫描范围显式指定为 com.enterpriseai（主类位于 .application 子包，
 * 默认扫描只覆盖本包——不显式声明会导致其他模块 bean 全部丢失）；
 * ConfigurationPropertiesScan 同理注册各模块的 @ConfigurationProperties
 * （agent.tools.permission.* 与 agent.prompts.* 等）。
 * JPA 仓库与实体的扫描包与组件扫描相互独立，需显式指向 infra 模块；
 * 注意 Boot 4 中 @EntityScan 已迁移至 org.springframework.boot.persistence.autoconfigure。
 */
@SpringBootApplication(scanBasePackages = "com.enterpriseai")
@ConfigurationPropertiesScan(basePackages = "com.enterpriseai")
@EnableJpaRepositories(basePackages = "com.enterpriseai.infra.persistence.repository")
@EntityScan(basePackages = "com.enterpriseai.infra.persistence.entity")
public class EnterpriseAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAiAgentApplication.class, args);
    }
}
