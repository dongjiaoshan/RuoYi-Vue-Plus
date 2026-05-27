-- ============================================================
-- WMS-PIG-001 燎毛工序记录单
--
-- 1. t_warehouse_pig_burn_record 燎毛记录主表
-- 2. 字典 seed：djs_burn_status（pending / done）
-- 3. sys_menu seed：9070 燎毛记录（admin 只读列表 list/export）
--
-- 燎毛记录提交后联动 INSERT t_warehouse_stock_flow + UPDATE
-- t_warehouse_location_stock（扣减 ear_no 维度白条库存）。
-- 同事务原子：service 层 @Transactional 控制；任一步失败整体回滚。
--
-- 不动 t_warehouse_bar_info（CROSS-FLOW-001 D10 范围）。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_pig_burn_record 燎毛记录主表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_pig_burn_record;
CREATE TABLE t_warehouse_pig_burn_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    burn_id         VARCHAR(32)  NOT NULL COMMENT '业务码 BURN+YYMMDD+4 位序号（本表幂等键）',
    ear_no          VARCHAR(32)  NOT NULL COMMENT '猪只耳号（关联 t_farm_pig_info.ear_no，要求 current_status=END）',
    burn_time       DATETIME     NOT NULL COMMENT '燎毛时间（工序时间）',
    arrive_weight   DECIMAL(12,3) NOT NULL COMMENT '到场重量 kg（燎毛前过磅）',
    burn_weight     DECIMAL(12,3) NOT NULL COMMENT '燎毛后重量 kg（白条入库重量）',
    loss_weight     DECIMAL(12,3) NOT NULL COMMENT '损耗重量 kg（= arrive_weight - burn_weight，service 校验 ≥ 0）',
    burn_status     VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '字典 djs_burn_status：pending=待处理 / done=已完成（已扣库存）',
    operator_id     BIGINT       NULL COMMENT 'mp 提交人 FK → sys_user.user_id（ADR-0007）',
    location_id     BIGINT       NULL COMMENT '入白条库的库位 FK → t_warehouse_location_info.id（done 状态必填）',
    proof_oss_ids   VARCHAR(500) NULL COMMENT '凭证图 OSS IDs CSV（CameraUploadWithWatermark biz_type=warehouse_pig_burn）',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建者',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NULL COMMENT '更新时间',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_burn_id (tenant_id, burn_id, del_unique),
    KEY idx_ear_no (tenant_id, ear_no),
    KEY idx_burn_time (tenant_id, burn_time),
    KEY idx_burn_status (tenant_id, burn_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='燎毛工序记录单（WMS-PIG-001）';

-- ------------------------------------------------------------
-- 2. 字典 seed djs_burn_status
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102100, '1001', '燎毛状态', 'djs_burn_status', 1, NOW(), 'WMS-PIG-001 燎毛工序记录状态');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1021000, '1001', 0, '待处理', 'pending', 'djs_burn_status', '', 'info',    'Y', 1, NOW()),
    (1021001, '1001', 1, '已完成', 'done',    'djs_burn_status', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. sys_menu seed
--    9070 燎毛记录（C 类，admin 只读列表）
--    9071 list 权限 + 9075 export 权限（写入由 mp 端走）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9070, '燎毛记录', 9000, 3, 'pigBurn', 'djs-warehouse/pigBurn/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:pigBurn:list', 'edit', 1, NOW(), 'WMS-PIG-001'),

    (9071, '燎毛查询', 9070, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:pigBurn:list',   '#', 1, NOW(), 'WMS-PIG-001'),
    (9075, '燎毛导出', 9070, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:pigBurn:export', '#', 1, NOW(), 'WMS-PIG-001');
