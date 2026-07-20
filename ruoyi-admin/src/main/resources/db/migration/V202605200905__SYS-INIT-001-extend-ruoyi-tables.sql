-- ============================================================
-- SYS-INIT-001 — 步骤 3：扩展 ruoyi 自带 sys_* 表
-- 生成时间: 2026-05-20
-- 内容：sys_user / sys_oss_config / sys_client / sys_post 扩字段
-- 引用: doc/06-实现描述.md SYS-AUTH-001 / SYS-INFRA-002 / SYS-INFRA-006
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------
-- 1. sys_user 加多农场 + 微信字段
--    farm_id 类型对齐 sys_user.tenant_id（VARCHAR(20)）
-- ----------------------------------------------------------------
ALTER TABLE sys_user
  ADD COLUMN farm_id              VARCHAR(20)  DEFAULT NULL COMMENT '当前默认农场 id（V1 默认 "1001"）',
  ADD COLUMN current_farm_id      VARCHAR(20)  DEFAULT NULL COMMENT '当前激活的农场 id（用户切换后更新）',
  ADD COLUMN accessible_farm_ids  VARCHAR(500) DEFAULT NULL COMMENT '可访问的农场 id 列表（逗号分隔，多农场用户用）',
  ADD COLUMN wx_openid            VARCHAR(64)  DEFAULT NULL COMMENT '微信 openid',
  ADD COLUMN wx_unionid           VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid',
  ADD INDEX idx_wx_openid (wx_openid);

-- ----------------------------------------------------------------
-- 2. sys_oss_config 加 STS 字段
-- ----------------------------------------------------------------
ALTER TABLE sys_oss_config
  ADD COLUMN sts_role_arn         VARCHAR(200) DEFAULT NULL COMMENT 'STS 角色 ARN（阿里云）',
  ADD COLUMN sts_session_duration INT          DEFAULT 3600 COMMENT 'STS 会话有效期（秒）';

-- ----------------------------------------------------------------
-- 3. sys_client 新增小程序客户端
--    id 显式赋值（ruoyi sys_client 无 AUTO_INCREMENT，dev seed 用低位 id）
--    client_secret 是 dev 占位，prod 部署时由运维替换
-- ----------------------------------------------------------------
INSERT INTO sys_client
  (id, client_id, client_key, client_secret, grant_type, device_type, active_timeout, timeout, status, del_flag, create_time, update_time)
VALUES
  (3, 'mp-applet-dongjiaoshan', 'mp_dongjiaoshan', 'djs_mp_dev_placeholder_replace_in_prod', 'wechat,password', 'mp', 1800, 604800, '0', '0', NOW(), NOW());

-- ----------------------------------------------------------------
-- 4. sys_post 加微信角色码
-- ----------------------------------------------------------------
ALTER TABLE sys_post
  ADD COLUMN wx_role_code VARCHAR(64) DEFAULT NULL COMMENT '小程序角色码（pig_keeper / planter / warehouse_keeper / store_clerk 等）';
