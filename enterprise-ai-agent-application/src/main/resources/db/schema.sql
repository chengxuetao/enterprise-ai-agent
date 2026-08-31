-- ============================================================================
-- Enterprise AI Agent — MySQL schema（手动执行；生产建议纳入 Flyway）
--   mysql -u root -p < schema.sql
-- ============================================================================

CREATE DATABASE IF NOT EXISTS enterprise_agent
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE enterprise_agent;

-- ---------- 企业表 ----------
CREATE TABLE IF NOT EXISTS enterprise (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id               VARCHAR(64)  NOT NULL COMMENT '租户ID',
    name                    VARCHAR(128) NOT NULL COMMENT '企业名称',
    city                    VARCHAR(32)           DEFAULT NULL COMMENT '城市',
    industry                VARCHAR(32)           DEFAULT NULL COMMENT '行业',
    registered_capital_wan  DECIMAL(15,2)         DEFAULT NULL COMMENT '注册资本（万元）',
    legal_person            VARCHAR(32)           DEFAULT NULL COMMENT '法人',
    status                  VARCHAR(16)           DEFAULT NULL COMMENT '状态：ACTIVE/SUSPENDED/CANCELLED',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enterprise_tenant (tenant_id),
    KEY idx_enterprise_tenant_name (tenant_id, name),
    KEY idx_enterprise_tenant_city (tenant_id, city)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '企业信息表（多租户）';

-- ---------- 告警表 ----------
CREATE TABLE IF NOT EXISTS alarm (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id         VARCHAR(64)  NOT NULL COMMENT '租户ID',
    enterprise_id     BIGINT                DEFAULT NULL COMMENT '关联企业',
    enterprise_name   VARCHAR(128)          DEFAULT NULL COMMENT '企业名称（冗余）',
    alarm_type        VARCHAR(32)           DEFAULT NULL COMMENT '告警类型',
    alarm_level       VARCHAR(16)           DEFAULT NULL COMMENT '等级：CRITICAL/HIGH/MEDIUM/LOW',
    occurred_at       DATETIME     NOT NULL COMMENT '发生时间',
    message           VARCHAR(512)          DEFAULT NULL COMMENT '告警内容',
    status            VARCHAR(16)           DEFAULT NULL COMMENT '状态：OPEN/PROCESSING/RESOLVED',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_alarm_tenant_time (tenant_id, occurred_at),
    KEY idx_alarm_tenant_enterprise (tenant_id, enterprise_name),
    KEY idx_alarm_tenant_level (tenant_id, alarm_level)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '企业告警表（多租户）';

-- ---------- Agent 执行审计主表 ----------
CREATE TABLE IF NOT EXISTS agent_execution (
    execution_id   VARCHAR(36) NOT NULL COMMENT '执行ID（UUID，日志/指标关联键）',
    session_id     VARCHAR(64) NOT NULL COMMENT '会话ID',
    tenant_id      VARCHAR(64) NOT NULL COMMENT '租户ID',
    user_id        VARCHAR(64)          DEFAULT NULL COMMENT '用户ID',
    user_input     TEXT                  DEFAULT NULL COMMENT '用户输入（脱敏后）',
    intent         VARCHAR(32)           DEFAULT NULL COMMENT '识别的意图',
    status         VARCHAR(16) NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED',
    error_message  TEXT                  DEFAULT NULL COMMENT '错误信息',
    start_time     DATETIME    NOT NULL COMMENT '开始时间',
    end_time       DATETIME              DEFAULT NULL COMMENT '结束时间',
    duration_ms    BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',
    PRIMARY KEY (execution_id),
    KEY idx_agent_exec_tenant_time (tenant_id, start_time),
    KEY idx_agent_exec_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 执行审计';

-- ---------- Agent Action 执行审计明细 ----------
CREATE TABLE IF NOT EXISTS agent_action_execution (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    execution_id  VARCHAR(36) NOT NULL COMMENT '关联 agent_execution.execution_id',
    action_name   VARCHAR(64) NOT NULL COMMENT 'Action 名称',
    outcome_type  VARCHAR(128)          DEFAULT NULL COMMENT '产出类型',
    latency_ms    BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',
    error         TEXT                  DEFAULT NULL COMMENT '错误信息',
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_agent_action_exec_id (execution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent Action 执行审计';
