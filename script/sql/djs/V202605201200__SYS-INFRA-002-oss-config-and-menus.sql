-- =============================================================================
-- SYS-INFRA-002: OSS STS 直传 — 配置 + 菜单权限 seed
--
-- 1. 把 ruoyi 自带 minio config (id=1) 调整为 djs dev 用桶 djs-dev（V1 dev 环境）
-- 2. 占位 aliyun-djs 配置（V1 不启用，prod 上线时 Kevin 改 access_key / sts_role_arn）
-- 3. 菜单权限 seed：
--    - admin 端 djs:common:oss:sts → 挂在通用主数据 5000 下，按钮型 (visible='1' 隐藏)
--    - 小程序端 djs:applet:oss:sts → 同样按钮型，仅做权限串占位（不展示菜单）
--
-- 上游依赖：SYS-INIT-001（sys_oss_config 已扩 sts_role_arn / sts_session_duration），
--           SYS-AUTH-001（5000 通用主数据父菜单已建）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. minio 配置：dev 用桶名 djs-dev（与本机 docker dev-minio 容器联调）
--    注意：sys_oss_config.tenant_id = '000000'（ruoyi 系统数据），不是业务 1001
-- -----------------------------------------------------------------------------
-- dev-minio 容器实际 root user/password 是 ruoyi / ruoyi123（见 docker inspect dev-minio）
UPDATE sys_oss_config
SET bucket_name          = 'djs-dev',
    access_key           = 'ruoyi',
    secret_key           = 'ruoyi123',
    endpoint             = '127.0.0.1:9000',
    domain               = '',
    is_https             = 'N',
    region               = '',
    access_policy        = '1',       -- public（dev 用，浏览器直拉 URL）
    status               = '0',       -- 启用为默认
    sts_role_arn         = '',
    sts_session_duration = 600
WHERE config_key = 'minio';

-- aliyun 配置：占位为 djs 业务桶（V1 不启用，status=1；上线时 Kevin 改 ak/sk/role_arn 后 status=0）
UPDATE sys_oss_config
SET bucket_name          = 'dongjiaoshan-prod',
    domain               = '',
    is_https             = 'Y',
    region               = 'cn-hangzhou',
    sts_role_arn         = '',                                  -- prod 启用时填 acs:ram::<account-id>:role/<sts-role>
    sts_session_duration = 600,
    status               = '1',                                 -- 停用，等真实 key
    remark               = '东角山 prod 阿里云 OSS（V1 占位，待 Kevin 填客户 RAM 子账号）'
WHERE config_key = 'aliyun';

-- -----------------------------------------------------------------------------
-- 2. 菜单权限：admin 端 + 小程序端 OSS STS 权限串
--    挂在通用主数据 5000 下作"按钮型"权限（visible='1' 隐藏，仅做 perms 串占位）
--    分配菜单 id: 5052（admin） / 5053（applet）
-- -----------------------------------------------------------------------------
INSERT INTO sys_menu
  (menu_id, menu_name,           parent_id, order_num, path, component, query_param,
   is_frame, is_cache, menu_type, visible, status, perms,
   icon, create_by, create_time, remark)
VALUES
  (5052, '申请 OSS 上传凭证(admin)', 5000, 11, '', NULL, '',
   1, 0, 'F', '1', '0', 'djs:common:oss:sts',
   '#', 1, NOW(), 'SYS-INFRA-002 admin 直传 OSS GET/POST /djs/oss/sts/*'),
  (5053, '申请 OSS 上传凭证(小程序)', 5000, 12, '', NULL, '',
   1, 0, 'F', '1', '0', 'djs:applet:oss:sts',
   '#', 1, NOW(), 'SYS-INFRA-002 小程序直传 OSS GET/POST /djs/applet/oss/sts/*');

-- -----------------------------------------------------------------------------
-- 3. 角色绑权
--    - boss(102) / manager(103) 已通过 SYS-AUTH-001 的范围 SELECT 拿到 5000-10999，
--      新增的 5052/5053 自动包含？—— 不！那个 INSERT 是一次性快照（SELECT 时刻），新增菜单要补回写
--    - system_admin(101) admin 上传必备
--    - 4 个业务 admin（104/105/106/107）admin 上传必备
--    - 小程序登录用户角色（如 mp_user）后续 ticket 接入时绑 5053
-- -----------------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 5052), -- system_admin
  (102, 5052), (102, 5053), -- boss
  (103, 5052), (103, 5053), -- manager
  (104, 5052), -- breed_admin
  (105, 5052), -- plant_admin
  (106, 5052), -- warehouse_admin
  (107, 5052); -- store_admin
