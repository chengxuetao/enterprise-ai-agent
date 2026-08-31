-- ============================================================================
-- 样例数据：两个租户（tenant-a / tenant-b），用于验证多租户隔离与四类查询。
--   mysql -u root -p < data.sql
-- 告警时间以导入时刻为基准（NOW() 偏移），保证"今天/最近7天"类查询有数据。
-- ============================================================================

USE enterprise_agent;

-- ---------- 企业（tenant-a：8 家） ----------
INSERT INTO enterprise (tenant_id, name, city, industry, registered_capital_wan, legal_person, status) VALUES
('tenant-a', '北京中科智联科技有限公司',   '北京', '科技',   5200.00, '张伟', 'ACTIVE'),
('tenant-a', '北京华夏云创科技股份有限公司', '北京', '科技',   12000.00, '李娜', 'ACTIVE'),
('tenant-a', '上海数联信息技术有限公司',   '上海', '科技',   800.00,  '王强', 'ACTIVE'),
('tenant-a', '上海恒基机械制造有限公司',   '上海', '制造',   30000.00, '赵敏', 'ACTIVE'),
('tenant-a', '深圳市绿源环保科技有限公司', '深圳', '环保',   1500.00, '陈亮', 'SUSPENDED'),
('tenant-a', '广州迅捷物流有限公司',       '广州', '物流',   600.00,  '刘洋', 'ACTIVE'),
('tenant-a', '杭州云启大数据有限公司',     '杭州', '科技',   4500.00, '孙鹏', 'ACTIVE'),
('tenant-a', '北京安泰医疗器械有限公司',   '北京', '医疗',   9000.00, '周芳', 'ACTIVE');

-- ---------- 企业（tenant-b：2 家，验证隔离） ----------
INSERT INTO enterprise (tenant_id, name, city, industry, registered_capital_wan, legal_person, status) VALUES
('tenant-b', '北京中科智联科技有限公司', '北京', '科技', 5200.00, '张伟', 'ACTIVE'),
('tenant-b', '成都锦程商贸有限公司',     '成都', '商贸', 300.00,  '吴刚', 'ACTIVE');

-- ---------- 告警（tenant-a：今天若干 + 近 7 天若干，多类型多等级） ----------
INSERT INTO alarm (tenant_id, enterprise_id, enterprise_name, alarm_type, alarm_level, occurred_at, message, status) VALUES
('tenant-a', 1, '北京中科智联科技有限公司',   '环保超标', 'HIGH',     NOW(), '废水排放指标超过限值 12%', 'OPEN'),
('tenant-a', 2, '北京华夏云创科技股份有限公司', '安全生产', 'CRITICAL', NOW(), '未按期提交安全生产自查报告', 'OPEN'),
('tenant-a', 4, '上海恒基机械制造有限公司',   '环保超标', 'MEDIUM',   NOW(), '废气排放接近限值', 'PROCESSING'),
('tenant-a', 5, '深圳市绿源环保科技有限公司', '证照过期', 'HIGH',     NOW(), '排污许可证已过期 30 天', 'OPEN'),
('tenant-a', 1, '北京中科智联科技有限公司',   '税务异常', 'MEDIUM',   DATE_SUB(NOW(), INTERVAL 1 DAY), '增值税申报延迟', 'RESOLVED'),
('tenant-a', 3, '上海数联信息技术有限公司',   '税务异常', 'LOW',      DATE_SUB(NOW(), INTERVAL 2 DAY), '发票信息待更新', 'RESOLVED'),
('tenant-a', 6, '广州迅捷物流有限公司',       '安全生产', 'HIGH',     DATE_SUB(NOW(), INTERVAL 3 DAY), '运输车辆年检逾期', 'PROCESSING'),
('tenant-a', 7, '杭州云启大数据有限公司',     '数据合规', 'MEDIUM',   DATE_SUB(NOW(), INTERVAL 4 DAY), '用户数据存储位置不合规', 'OPEN'),
('tenant-a', 4, '上海恒基机械制造有限公司',   '安全生产', 'CRITICAL', DATE_SUB(NOW(), INTERVAL 5 DAY), '特种设备检验证书过期', 'RESOLVED'),
('tenant-a', 8, '北京安泰医疗器械有限公司',   '证照过期', 'HIGH',     DATE_SUB(NOW(), INTERVAL 6 DAY), '医疗器械经营许可证临期', 'RESOLVED');

-- ---------- 告警（tenant-b：仅 1 条，验证隔离） ----------
INSERT INTO alarm (tenant_id, enterprise_id, enterprise_name, alarm_type, alarm_level, occurred_at, message, status) VALUES
('tenant-b', 9, '北京中科智联科技有限公司', '环保超标', 'LOW', NOW(), '噪音排放轻微超标', 'OPEN');
