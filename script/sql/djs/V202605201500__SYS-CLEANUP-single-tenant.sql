-- =============================================================================
-- SYS-CLEANUP-single-tenant  V1 单租户规整
-- =============================================================================
-- V1 只服务一个农场（东角山有机生态农场）。
-- 本脚本在 ruoyi 基础 seed + 所有 djs DDL 之后执行：
--   1) 把 ruoyi 默认 '000000' 租户 rename 为业务租户 '1001'
--   2) 把所有 ruoyi 自带 sys_* 表的 tenant_id='000000' 行 rebind 到 '1001'
-- 跑完后：整库不存在 tenant_id='000000'，sys_tenant 唯一一行 = 1001 东角山农场。
--
-- 多租户拦截器 `tenant.enable=true` 保留：单租户场景无副作用，V2 多农场启用即生效。
-- =============================================================================

-- 1. 重命名 ruoyi 默认租户 → 东角山农场
UPDATE sys_tenant SET
  tenant_id         = '1001',
  company_name      = '东角山有机生态农场',
  contact_user_name = '王奎',
  contact_phone     = '13800000000',
  address           = '广州市从化区',
  intro             = '东角山有机生态农场 V1 默认租户'
WHERE tenant_id = '000000';

-- 2. ruoyi 自带 sys_* 表数据 rebind 到 '1001'
UPDATE sys_user        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_role        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dept        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_post        SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dict_type   SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_dict_data   SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_config      SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_notice      SET tenant_id = '1001' WHERE tenant_id = '000000';
UPDATE sys_oss_config  SET tenant_id = '1001' WHERE tenant_id = '000000';

-- =============================================================================
-- 验收 query
--   SELECT (
--     (SELECT COUNT(*) FROM sys_tenant    WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_user       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_role       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dept       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_post       WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dict_type  WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_dict_data  WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_config     WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_notice     WHERE tenant_id <> '1001') +
--     (SELECT COUNT(*) FROM sys_oss_config WHERE tenant_id <> '1001')
--   ) AS not_1001_rows;
--   -- 期望 = 0
-- =============================================================================
