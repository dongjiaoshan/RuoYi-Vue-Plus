-- ============================================================
-- V6-R146 追溯码配置管理：追溯码页面配置表 + 系统管理→数据管理下的新菜单
-- ============================================================
-- 甲方原话 3 条：数据管理下新增菜单【追溯码配置管理】；列表为 追溯码名称 / 基地介绍页图片 /
-- 更新时间 / 更新人 / 操作，无搜索条件、操作列只有上传图片；追溯码 H5 里「基地介绍」原本展开一个
-- 写死版式的 HTML 页，改成打开这里上传的那张图片。
--
-- 号段：系统底座 5000-5999，数据管理目录 5700 已建（V202608300600），本次取 5705 / 5706 / 5707
--       （5705-5799 当前无任何 sys_menu 行占用，不会继承历史授权）。
-- 版本号：目标库 flyway max = 202608311000，仓库最大迁移文件 = 202608311400，本文件 202609010700 已留足 buffer。
-- 无字典变更，无需 flush redis。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 追溯码页面配置表
--    pork / veg 各一行，行由本迁移预置；页面只换图，不提供新增 / 删除
--    （两行是配置项不是业务数据，删掉一行 = 公开端点取不到图）。
--    存 ossId 不存 URL：OSS 域名 / 签名会变，URL 由 admin 端与公开端各自解析。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_warehouse_trace_page_config (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    code_type               VARCHAR(16)  NOT NULL                 COMMENT '追溯码类型：pork=猪肉 / veg=果蔬（对齐 t_warehouse_trace_code.code_type）',
    config_name             VARCHAR(64)  NOT NULL                 COMMENT '追溯码名称（列表展示用固定名）',
    base_intro_image_oss_id VARCHAR(32)  NULL                     COMMENT '基地介绍页图片 ossId（sys_oss.oss_id，单图；空=未配置，H5 回落内置版式）',
    create_dept             BIGINT       NULL                     COMMENT '创建部门',
    create_by               BIGINT       NULL                     COMMENT '创建者',
    create_time             DATETIME     NULL                     COMMENT '创建时间',
    update_by               BIGINT       NULL                     COMMENT '更新者',
    update_time             DATETIME     NULL                     COMMENT '更新时间',
    del_flag                CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code_type (tenant_id, code_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='追溯码页面配置表（V6-R146）';

-- ------------------------------------------------------------
-- 2) 预置两行（图片留空）。
--    幂等靠 UNIQUE(tenant_id, code_type) + INSERT IGNORE，重跑不报错也不重复。
--    pork 先插，列表按 id ASC 排时「猪肉追溯码」在上。
-- ------------------------------------------------------------
INSERT IGNORE INTO t_warehouse_trace_page_config
    (tenant_id, code_type, config_name, base_intro_image_oss_id, create_by, create_time, del_flag)
VALUES
    ('1001', 'pork', '猪肉追溯码', NULL, 1, NOW(), '0'),
    ('1001', 'veg',  '果蔬追溯码', NULL, 1, NOW(), '0');

-- ------------------------------------------------------------
-- 3) 菜单：系统管理 → 数据管理 → 追溯码配置管理（+ 查询 / 上传图片 两个按钮权限）
--    perms 串定为 djs:common:traceCodeConfig:*，而代码物理位置在 ruoyi-djs-warehouse 的 trace 包
--    —— 追溯表与写码 hook 都在 warehouse（避免 warehouse→store 反向依赖），不是放错模块。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5705, '追溯码配置管理', 5700, 3, 'traceCodeConfig', 'djs-common/traceCodeConfig/index', '',
     1, 0, 'C', '0', '0',
     'djs:common:traceCodeConfig:list', 'upload', 1, NOW(), 'V6-R146'),
    (5706, '查询追溯码配置', 5705, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:common:traceCodeConfig:query', '#', 1, NOW(), 'V6-R146'),
    (5707, '上传基地介绍图', 5705, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:common:traceCodeConfig:upload', '#', 1, NOW(), 'V6-R146');

-- ------------------------------------------------------------
-- 4) 角色授权：派生自父目录「数据管理」(5700) 本身的角色集合（不写死 role_id），再显式补超级管理员。
--    ADR-0020：授权覆盖整条子树。「能看见数据管理这个目录的人，就该看见它下面的功能」是稳定语义，
--    挂到兄弟菜单上则是偶然耦合。
--
--    🔴 待甲方/Kevin 定：本菜单按甲方要求挂在「系统管理」下，没有系统管理目录权限的角色
--    （老板 / 管理人员）看不到它。要开给他们得连「系统管理」顶层目录一起授权，
--    那是权限结构层面的变更，不在本条范围内自行决定（与 V6-R140 同一处坑）。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 5705 AS menu_id UNION ALL SELECT 5706 UNION ALL SELECT 5707) m
WHERE rm.menu_id = 5700;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 5700), (1, 5705), (1, 5706), (1, 5707);
