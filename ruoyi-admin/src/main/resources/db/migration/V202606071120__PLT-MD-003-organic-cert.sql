-- ============================================================
-- PLT-MD-003 有机认证（土地 + 作物 + 多对多）
--
-- 1. t_plant_plot_organic 土地有机证书（doc/11 §1.4）
-- 2. t_plant_organic_plotno 地块-证书关联（doc/11 §1.5）
-- 3. t_plant_crop_organic 果蔬有机证书（doc/11 §1.6）
-- 4. sys_config 预警阈值
-- 5. sys_menu seed 8050-8064（doc/CLAUDE.md §6 种植段二级 8050-8064 有机认证）
--
-- xlsx 笔误清理：organic_vaild → organic_valid / crop_vaild → crop_cert_valid /
--                is_waring → is_warning / t_plant_crop_crop → t_plant_crop_organic
--
-- baseline D2 占位三表（V202605200902）schema 与权威 doc/11 偏差大（缺 OSS 图片字段、
-- 字段名笔误、缺软删 token 等），三表数据均 0 行，安全 DROP + CREATE 重建。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_plot_organic 土地有机证书（doc/11 §1.4）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_plot_organic;
CREATE TABLE t_plant_plot_organic (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id               VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    organic_no              VARCHAR(64)   NOT NULL                COMMENT '证书编号（用户手填，例 GB-2026-001）',
    organic_company         VARCHAR(128)  NOT NULL                COMMENT '颁发单位（例 南京国环）',
    organic_valid           DATE          NOT NULL                COMMENT '证书有效期到期日',
    organic_image_preview   VARCHAR(512)  NULL                    COMMENT '缩略图 OSS ossId（单张）',
    organic_image_url       VARCHAR(2048) NULL                    COMMENT '原图 OSS ossIds 逗号分隔（多张）',
    is_warning              TINYINT       NOT NULL DEFAULT 2      COMMENT '字典 djs_yes_no：1=预警 / 2=正常（默认 2）',
    create_dept             BIGINT        NULL                    COMMENT '创建部门',
    create_by               BIGINT        NULL                    COMMENT '创建人',
    create_time             DATETIME      NULL                    COMMENT '创建时间',
    update_by               BIGINT        NULL                    COMMENT '更新人',
    update_time             DATETIME      NULL                    COMMENT '更新时间',
    remark                  VARCHAR(500)  NULL                    COMMENT '备注',
    del_flag                CHAR(1)       NOT NULL DEFAULT '0'    COMMENT '删除标志',
    del_unique              BIGINT        NOT NULL DEFAULT 0      COMMENT "软删 token（update del_flag='1' 时 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_organic_no (tenant_id, organic_no, del_unique),
    KEY idx_organic_valid (tenant_id, organic_valid),
    KEY idx_is_warning (tenant_id, is_warning)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 土地有机证书（PLT-MD-003）';


-- ------------------------------------------------------------
-- 2. t_plant_organic_plotno 证书-地块关联（doc/11 §1.5）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_organic_plotno;
CREATE TABLE t_plant_organic_plotno (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    organic_id      BIGINT       NOT NULL                COMMENT 'FK → t_plant_plot_organic.id',
    plot_id         BIGINT       NOT NULL                COMMENT 'FK → t_plant_plot_info.id',
    create_dept     BIGINT       NULL                    COMMENT '创建部门',
    create_by       BIGINT       NULL                    COMMENT '创建人',
    create_time     DATETIME     NULL                    COMMENT '创建时间',
    update_by       BIGINT       NULL                    COMMENT '更新人',
    update_time     DATETIME     NULL                    COMMENT '更新时间',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0      COMMENT "软删 token（编辑 + 软删旧关联时 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_organic_plot (tenant_id, organic_id, plot_id, del_unique),
    KEY idx_organic (tenant_id, organic_id),
    KEY idx_plot (tenant_id, plot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 证书-地块关联表（PLT-MD-003）';


-- ------------------------------------------------------------
-- 3. t_plant_crop_organic 果蔬有机证书（doc/11 §1.6）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_crop_organic;
CREATE TABLE t_plant_crop_organic (
    id                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id               VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    crop_cert_no            VARCHAR(64)   NOT NULL                COMMENT '果蔬证书编号（用户手填）',
    crop_cert_company       VARCHAR(128)  NOT NULL                COMMENT '颁发单位',
    crop_cert_valid         DATE          NOT NULL                COMMENT '证书有效期到期日',
    crop_id                 BIGINT        NOT NULL                COMMENT 'FK → t_plant_crop_info.id（一证书对一作物）',
    crop_image_preview      VARCHAR(512)  NULL                    COMMENT '缩略图 OSS ossId（单张）',
    crop_image_url          VARCHAR(2048) NULL                    COMMENT '原图 OSS ossIds 逗号分隔（多张）',
    is_warning              TINYINT       NOT NULL DEFAULT 2      COMMENT '字典 djs_yes_no：1=预警 / 2=正常',
    create_dept             BIGINT        NULL                    COMMENT '创建部门',
    create_by               BIGINT        NULL                    COMMENT '创建人',
    create_time             DATETIME      NULL                    COMMENT '创建时间',
    update_by               BIGINT        NULL                    COMMENT '更新人',
    update_time             DATETIME      NULL                    COMMENT '更新时间',
    remark                  VARCHAR(500)  NULL                    COMMENT '备注',
    del_flag                CHAR(1)       NOT NULL DEFAULT '0'    COMMENT '删除标志',
    del_unique              BIGINT        NOT NULL DEFAULT 0      COMMENT '软删 token',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_cert_no (tenant_id, crop_cert_no, del_unique),
    KEY idx_crop_id (tenant_id, crop_id),
    KEY idx_crop_cert_valid (tenant_id, crop_cert_valid),
    KEY idx_is_warning (tenant_id, is_warning)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 果蔬有机证书（PLT-MD-003）';


-- ------------------------------------------------------------
-- 4. sys_config 预警阈值
--    config_type='Y' = 系统内置（admin UI 不可删）；管理员可改 config_value
-- ------------------------------------------------------------
INSERT INTO sys_config (
    config_id, tenant_id, config_name, config_key, config_value, config_type,
    create_by, create_time, remark
) VALUES (
    200, '1001', '有机证书到期预警天数', 'plant.organic.warning_days', '60', 'Y',
    1, NOW(), 'PLT-MD-003 默认 60 天（土地 + 作物证书共用）'
) ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);


-- ------------------------------------------------------------
-- 5. sys_menu seed（8050-8064）
--    8050 土地有机认证（C 类）+ 8051-8055 5 按钮权限
--    8060 果蔬有机认证（C 类）+ 8061-8065 5 按钮权限
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 土地有机认证
    (8050, '土地有机认证', 8000, 4, 'plotOrganic', 'djs-plant/plotOrganic/index', '',
     1, 0, 'C', '0', '0', 'djs:plant:plotOrganic:list', 'star', 1, NOW(), 'PLT-MD-003'),
    (8051, '土地证书查询', 8050, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plotOrganic:list',   '#', 1, NOW(), 'PLT-MD-003'),
    (8052, '土地证书新增', 8050, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plotOrganic:add',    '#', 1, NOW(), 'PLT-MD-003'),
    (8053, '土地证书编辑', 8050, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plotOrganic:edit',   '#', 1, NOW(), 'PLT-MD-003'),
    (8054, '土地证书删除', 8050, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plotOrganic:remove', '#', 1, NOW(), 'PLT-MD-003'),
    (8055, '土地证书导出', 8050, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:plotOrganic:export', '#', 1, NOW(), 'PLT-MD-003'),

    -- 果蔬有机认证
    (8060, '果蔬有机认证', 8000, 5, 'cropOrganic', 'djs-plant/cropOrganic/index', '',
     1, 0, 'C', '0', '0', 'djs:plant:cropOrganic:list', 'star', 1, NOW(), 'PLT-MD-003'),
    (8061, '果蔬证书查询', 8060, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:cropOrganic:list',   '#', 1, NOW(), 'PLT-MD-003'),
    (8062, '果蔬证书新增', 8060, 2, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:cropOrganic:add',    '#', 1, NOW(), 'PLT-MD-003'),
    (8063, '果蔬证书编辑', 8060, 3, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:cropOrganic:edit',   '#', 1, NOW(), 'PLT-MD-003'),
    (8064, '果蔬证书删除', 8060, 4, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:cropOrganic:remove', '#', 1, NOW(), 'PLT-MD-003'),
    (8065, '果蔬证书导出', 8060, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:plant:cropOrganic:export', '#', 1, NOW(), 'PLT-MD-003');

-- 默认 role_id=1 super-admin 关联本 ticket 全部菜单
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 8050 AND 8065;
