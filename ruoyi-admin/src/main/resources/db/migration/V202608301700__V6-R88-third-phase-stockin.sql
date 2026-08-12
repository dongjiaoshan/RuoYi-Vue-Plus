-- ============================================================================
-- V6 row88：三期作物入库管理（admin 新模块，挂「库存管理」下、紧邻「毛菜间出库管理」）
--
-- 甲方口径：「三期」= 该作物的第三茬/第三期（不是开发三期）。
--   新增入库弹框：入库产品（仅果蔬类原材料）+ 入库量(kg，3 位小数) + 入库库位（仅毛菜保鲜室 L0006）。
--   保存后：产品入库（stock_flow + location_stock，口径与「采摘去向[毛菜保鲜室]入库」完全一致）
--         + 记一条本模块操作记录（含作物信息快照）
--         + 在该产品对应作物「当前在种」的地块上打【三期】标识。
--   入库方式（admin 入库记录页的 djs_flow_type）= 新增值 third_phase_in「三期作物入库」。
--
-- 本文件四件事：
--   1. 新建 t_warehouse_third_phase_in（本模块操作台账）
--   2. t_plant_plot_info 加 third_phase 列（【三期】标识打在地块上 —— Kevin 2026-08-12 拍板）
--   3. djs_flow_type 补 third_phase_in
--   4. sys_menu 9140-9143 + 角色授权
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 三期作物入库记录表
--    一次「新增入库」= 一行。作物信息（crop_id/crop_name）、产品名、库位名、地块名串
--    全部按写入当时的值做快照：作物改名 / 地块调整之后，历史入库记录仍要显示当时录的是什么。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_warehouse_third_phase_in (
    id            BIGINT        NOT NULL                COMMENT '主键（雪花）',
    tenant_id     VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    in_time       DATETIME      NOT NULL                COMMENT '入库时间（列表显示到时分）',
    crop_id       BIGINT        NULL                    COMMENT '作物 FK → t_plant_crop_info.id（多作物命中时取 id 最小的一个）',
    crop_name     VARCHAR(128)  NULL                    COMMENT '作物名称快照（多作物命中时逗号拼接全部）',
    product_id    BIGINT        NOT NULL                COMMENT '入库产品 FK → t_warehouse_product_info.id',
    product_name  VARCHAR(128)  NULL                    COMMENT '产品名称快照',
    stock_num     DECIMAL(12,3) NOT NULL                COMMENT '入库量（kg，3 位小数）',
    location_id   BIGINT        NOT NULL                COMMENT '入库库位 FK → t_warehouse_location_info.id（恒为毛菜鲜品库 L0006）',
    location_name VARCHAR(64)   NULL                    COMMENT '入库库位名称快照',
    plot_ids      VARCHAR(512)  NULL                    COMMENT '本次打【三期】标识的地块 id 逗号串（无在种地块时为空）',
    plot_names    VARCHAR(512)  NULL                    COMMENT '本次打【三期】标识的地块名逗号串（无在种地块时为空，列表可直接看出没打上）',
    flow_id       BIGINT        NULL                    COMMENT '本次入库产生的 t_warehouse_stock_flow.id（对账用）',
    operator_id   BIGINT        NULL                    COMMENT '入库人 FK → sys_user.user_id',
    remark        VARCHAR(500)  NULL                    COMMENT '备注',
    create_by     BIGINT        NULL                    COMMENT '创建者',
    create_time   DATETIME      NULL                    COMMENT '创建时间',
    update_by     BIGINT        NULL                    COMMENT '更新者',
    update_time   DATETIME      NULL                    COMMENT '更新时间',
    create_dept   BIGINT        NULL                    COMMENT '创建部门',
    del_flag      CHAR(1)       NOT NULL DEFAULT '0'    COMMENT '软删标记 0=正常 / 1=已删',
    del_unique    BIGINT        NOT NULL DEFAULT 0      COMMENT '软删唯一辅助列（删除时写 id）',
    PRIMARY KEY (id),
    KEY idx_tpi_time (tenant_id, in_time),
    KEY idx_tpi_crop (tenant_id, crop_id),
    KEY idx_tpi_product (tenant_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '三期作物入库记录（V6 row88）';

-- ----------------------------------------------------------------------------
-- 2. 地块【三期】标识
--    单向置位：三期作物入库时把命中的在种地块置 1；不提供自动清零（甲方没提清除口径，
--    需要清时走 admin 地块编辑 / 数据修复，别在这里发明一套"下一茬自动复位"）。
-- ----------------------------------------------------------------------------
ALTER TABLE t_plant_plot_info
    ADD COLUMN third_phase TINYINT(1) NOT NULL DEFAULT 0 COMMENT '三期作物标识 0否 1是（V6 row88 三期作物入库时置 1）' AFTER plot_status;

CREATE INDEX idx_plot_third_phase ON t_plant_plot_info (tenant_id, third_phase);

-- ----------------------------------------------------------------------------
-- 3. 入库方式字典：djs_flow_type 补 third_phase_in
--    dict_sort 接在现有最大值 28 之后 = 29；dict_code 沿用该字典既有号段 1024500NN。
--    ⚠️ admin「入库记录」页的入库方式下拉是按 value 白名单过滤的（plus-ui
--    views/djs-warehouse/flow/in/index.vue FLOW_TYPE_IN_VALUES），前端要同步加 third_phase_in，
--    否则这类流水在该页筛不出来。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
                                  css_class, list_class, is_default, create_by, create_time, remark)
VALUES (102450029, '1001', 29, '三期作物入库', 'third_phase_in', 'djs_flow_type',
        '', 'success', 'N', 1, NOW(), 'V6-R88 三期作物入库');

-- ----------------------------------------------------------------------------
-- 4. 菜单 + 角色授权
--    位置：库存管理(9302) 下，紧跟「毛菜间出库管理」(9134, order_num=6) → 取 order_num=7。
--    腾位 UPDATE 不幂等（裸重跑会把同一批菜单反复后移），故加存在性守卫。
-- ----------------------------------------------------------------------------
SET @tpi_menu_exists := (SELECT COUNT(*) FROM sys_menu WHERE menu_id = 9140);
SET @tpi_ddl := IF(@tpi_menu_exists = 0,
    'UPDATE sys_menu SET order_num = order_num + 1 WHERE parent_id = 9302 AND order_num >= 7',
    'SELECT 1');
PREPARE tpi_stmt FROM @tpi_ddl;
EXECUTE tpi_stmt;
DEALLOCATE PREPARE tpi_stmt;

INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                             is_frame, is_cache, menu_type, visible, status, perms, icon,
                             create_dept, create_by, create_time, remark)
VALUES (9140, '三期作物入库管理', 9302, 7, 'thirdPhaseIn', 'djs-warehouse/thirdPhaseIn/index', '',
        1, 0, 'C', '0', '0', 'djs:warehouse:thirdPhaseIn:list', 'upload', 103, 1, NOW(), 'V6-R88'),
       (9141, '三期作物入库查询', 9140, 1, '', NULL, '',
        1, 0, 'F', '0', '0', 'djs:warehouse:thirdPhaseIn:list', '#', 103, 1, NOW(), 'V6-R88'),
       (9142, '三期作物入库新增', 9140, 2, '', NULL, '',
        1, 0, 'F', '0', '0', 'djs:warehouse:thirdPhaseIn:add', '#', 103, 1, NOW(), 'V6-R88'),
       (9143, '三期作物入库导出', 9140, 3, '', NULL, '',
        1, 0, 'F', '0', '0', 'djs:warehouse:thirdPhaseIn:export', '#', 103, 1, NOW(), 'V6-R88');

-- 授权口径 = 派生自兄弟菜单「毛菜间出库管理」(9134) 的既有角色集合（同属毛菜间日常作业），
-- 不写死 role_id，9134 的角色调整时这条不会变成一份对不上的副本。
-- ADR-0020：整条子树都要授（含 C 菜单本身），否则子按钮成孤儿。
-- ⚠️ 这条授权的作用是**前端菜单/按钮显隐**，不是安全边界（Kevin 2026-08-06：后端不做硬鉴权）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 9140 AS menu_id UNION ALL SELECT 9141 UNION ALL SELECT 9142 UNION ALL SELECT 9143) m
WHERE rm.menu_id = 9134;

-- 超级管理员显式补授（与 V202608280200 / V202608300600 同口径：9134 万一漏授时不至于全员看不见）。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 9140), (1, 9141), (1, 9142), (1, 9143);
