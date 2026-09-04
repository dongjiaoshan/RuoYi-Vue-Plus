-- PLT-FARMMAP-001  农场地图：图上格子 ↔ 地块 绑定表 + 菜单
--
-- 甲方要求把地块按他们自己那张农场地图区分。地图几何（每个格子的多边形）由
-- plus-ui 的 scripts/trace-farmmap.py 从 doc-phase2/_input/map.png 描出来，落在前端
-- src/views/djs-plant/farmmap/map/regions.generated.ts —— 几何进代码，不进库。
-- 进库的只有「哪个格子挂了哪块地」这一件事，因为它是用户数据、要随时可改。
--
-- 口径：一个格子 ↔ 一个地块（1:1，Kevin 2026-09-02 拍板）。1:1 由两条 UNIQUE 同时保证：
--   uk_fmr_region  一个格子只能挂一块地
--   uk_fmr_plot    一块地只能挂在一个格子上
-- 表里**只存已绑定的行**：没有行 = 这个格子还没挂地块，解绑 = 软删。因此 plot_id NOT NULL，
-- 不需要「plot_id 可空」那种半绑定态（多个 NULL 也会让 uk_fmr_plot 失去意义）。
--
-- 覆盖率不足是已知且被设计接纳的：图上描出 176 个格子，地块表有 167 块，但两边不是同一套
-- 划分——长廊（5 片区 6 块）在图上是白缝、地头（6 块）没画成独立格子，反过来图上有水面和
-- 装饰格不对应任何地块。挂不上的地块走页面「图外地块」清单照常排产，页面顶部常驻
-- 「已挂 N / 共 167」把覆盖率明写出来，不假装全覆盖。
--
-- 号段：种植 8000-8999。父目录 8006（种植管理）已存在。本次取 8400-8402
--       （8400-8499 当前无任何 sys_menu 行占用；8300 已被「需求反馈」占用，勿复用）。
-- 版本号：仓库最大迁移文件 = 202609010700，本文件 202609030900 已留足 buffer。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：建表 IF NOT EXISTS + 菜单 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS t_plant_farm_map_region (
    id          BIGINT       NOT NULL COMMENT '主键（雪花）',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    region_key  VARCHAR(32)  NOT NULL COMMENT '图上格子业务码（regions.generated.ts 的 key，例 R-001），永不重编号',
    plot_id     BIGINT       NOT NULL COMMENT '挂的地块 FK → t_plant_plot_info.id',
    create_dept BIGINT       NULL COMMENT '创建部门',
    create_by   BIGINT       NULL COMMENT '创建人',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_by   BIGINT       NULL COMMENT '更新人',
    update_time DATETIME     NULL COMMENT '更新时间',
    del_flag    CHAR(1)      DEFAULT '0' COMMENT '删除标志',
    del_unique  BIGINT       NOT NULL DEFAULT 0 COMMENT '软删 token（软删时 SET del_unique=id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fmr_region (tenant_id, region_key, del_unique),
    UNIQUE KEY uk_fmr_plot (tenant_id, plot_id, del_unique)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='种植 - 农场地图格子↔地块绑定（PLT-FARMMAP-001）';

-- sys_menu：种植 → 种植管理(8006) → 农场地图
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (8400, '农场地图', 8006, 2, 'farmmap', 'djs-plant/farmmap/index', '',
     1, 0, 'C', '0', '0',
     'djs:plant:farmmap:list', 'guide', 1, NOW(), 'PLT-FARMMAP-001 按甲方地图看地块排产 + 格子挂地块'),
    (8401, '查询', 8400, 1, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:plant:farmmap:list', '#', 1, NOW(), 'PLT-FARMMAP-001 GET /djs/plant/farmmap/*'),
    (8402, '绑定', 8400, 2, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:plant:farmmap:bind', '#', 1, NOW(), 'PLT-FARMMAP-001 POST /bind、DELETE /bind/{regionKey}');

-- 角色授权（ADR-0020：子树必须与父目录同授权，否则子菜单成孤儿不显示）
-- ① 派生：凡是已拿到父目录 8006 的角色，同样拿 8400/8401/8402
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
         CROSS JOIN (SELECT 8400 AS menu_id UNION ALL SELECT 8401 UNION ALL SELECT 8402) m
WHERE rm.menu_id = 8006;

-- ② 显式兜底：① 命中 0 行时保证页面可见（1 超管 / 101 系统管理员 / 102 老板 / 103 管理人员）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 8400), (1, 8401), (1, 8402),
       (101, 8400), (101, 8401), (101, 8402),
       (102, 8400), (102, 8401), (102, 8402),
       (103, 8400), (103, 8401), (103, 8402);

-- 验收 query
--   SHOW CREATE TABLE t_plant_farm_map_region;
--   SELECT menu_id, menu_name, parent_id, path, component, menu_type, perms
--     FROM sys_menu WHERE menu_id BETWEEN 8400 AND 8409 ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id BETWEEN 8400 AND 8409 GROUP BY menu_id;
