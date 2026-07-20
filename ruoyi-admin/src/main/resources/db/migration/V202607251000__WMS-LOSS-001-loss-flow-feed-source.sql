-- ============================================================
-- WMS-LOSS-001  统一损耗流水台账 + 饲喂来源（仓库-admin 6/24 行58/59/61/62/64）
-- ============================================================
-- 行58：所有环节损耗在原有逻辑上「加新逻辑」双写一张统一损耗流水明细表
--       （原 stock_flow loss/check_out + 各业务表 loss_weight 列保留做展示，本表做集中明细 + 总览/详情数据源）。
-- 行59/61/62：损耗类型枚举 djs_loss_type（果蔬5 + 其他2 + 白条2，去重共 7 值）。
-- 行64：feed_log 加饲喂来源字段 feed_type（毛菜间 / 仓库）+ 补 product_id/operator_id/location_id
--       （行56/57/55 要按产品归集 + 显示饲喂位置/操作人）。
-- sys_dict_type / sys_dict_data 为 ruoyi 框架表；dict_code 取已确认空号段 1007030-1007036 / 1007040-1007041。
-- 跑后须 bash script/sql/djs/_post-init.sh flush redis 字典缓存。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 统一损耗流水表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_warehouse_loss_flow (
    id              BIGINT          NOT NULL COMMENT '主键（雪花）',
    loss_date       DATETIME        NOT NULL COMMENT '损耗发生时间',
    product_id      BIGINT          NULL     COMMENT '产品 ID（FK → t_warehouse_product_info.id）',
    product_code    VARCHAR(64)     NULL     COMMENT '产品编码（冗余快照，行58 核心字段）',
    product_name    VARCHAR(128)    NULL     COMMENT '产品名称（冗余快照）',
    product_unit    VARCHAR(16)     NULL     COMMENT '单位（冗余快照）',
    belong_type     VARCHAR(32)     NULL     COMMENT '业态归属（冗余，按品类聚合/过滤；字典 djs_belong_type）',
    loss_type       VARCHAR(20)     NOT NULL COMMENT '损耗类型（字典 djs_loss_type）',
    loss_weight     DECIMAL(12, 3)  NOT NULL COMMENT '损耗量',
    location_id     BIGINT          NULL     COMMENT '损耗位置（库位 FK → t_warehouse_location_info.id）',
    location_name   VARCHAR(64)     NULL     COMMENT '损耗位置名称（冗余快照）',
    operator_id     BIGINT          NULL     COMMENT '操作人（系统自动算的损耗如生产损耗为空）',
    source_biz_type VARCHAR(20)     NULL     COMMENT '来源业务（veg_handle/transport/mat/cut/check/production）',
    source_biz_id   BIGINT          NULL     COMMENT '来源记录 ID（追溯回指源单）',
    source_flow_id  BIGINT          NULL     COMMENT '关联 stock_flow.id（若损耗同时是一条出库流水）',
    ear_no          VARCHAR(32)     NULL     COMMENT '猪只耳号 / 白条号（追溯标签，白条损耗用）',
    plot_id         BIGINT          NULL     COMMENT '地块 ID（追溯标签，果蔬损耗用）',
    remark          VARCHAR(500)    NULL     COMMENT '备注',
    tenant_id       VARCHAR(20)     NOT NULL DEFAULT '1001' COMMENT '租户编号（=农场编码，行58）',
    create_by       BIGINT          NULL     COMMENT '创建者',
    create_time     DATETIME        NULL     COMMENT '创建时间',
    update_by       BIGINT          NULL     COMMENT '更新者',
    update_time     DATETIME        NULL     COMMENT '更新时间',
    create_dept     BIGINT          NULL     COMMENT '创建部门',
    del_flag        CHAR(1)         NOT NULL DEFAULT '0' COMMENT '删除标记（0 未删 / 1 已删）',
    PRIMARY KEY (id),
    KEY idx_loss_date (loss_date),
    KEY idx_loss_product (product_id),
    KEY idx_loss_type (loss_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '统一损耗流水台账（WMS-LOSS-001）';

-- ------------------------------------------------------------
-- 2. 损耗类型字典 djs_loss_type（行59/61/62 去重 7 值）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1007003, '1001', '损耗类型', 'djs_loss_type', 1, NOW(), 'WMS-LOSS-001 统一损耗台账损耗类型');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1007030, '1001', 1, '毛菜处理损耗', 'veg_handle_loss', 'djs_loss_type', '', 'warning', 'N', 1, NOW(), '果蔬：毛菜间称重−鲜品库入库−发往月台−饲喂'),
    (1007031, '1001', 2, '运输损耗',     'transport_loss',  'djs_loss_type', '', 'warning', 'N', 1, NOW(), '果蔬：发往月台重−月台接收重'),
    (1007032, '1001', 3, '录入损耗',     'manual_loss',     'djs_loss_type', '', 'info',    'N', 1, NOW(), '物资领用时手动录入的损耗量'),
    (1007033, '1001', 4, '生产损耗',     'production_loss', 'djs_loss_type', '', 'danger',  'N', 1, NOW(), '仅原材料：出库量−生产消耗−录入损耗−生产退回（操作人空）'),
    (1007034, '1001', 5, '盘点损耗',     'check_loss',      'djs_loss_type', '', 'danger',  'N', 1, NOW(), '盘点计损（盘亏）'),
    (1007035, '1001', 6, '预冷损耗',     'precool_loss',    'djs_loss_type', '', 'warning', 'N', 1, NOW(), '白条：入白条库重−出白条库重'),
    (1007036, '1001', 7, '分割损耗',     'cut_loss',        'djs_loss_type', '', 'warning', 'N', 1, NOW(), '白条：白条出库重−所有分割产品重量之和');

-- ------------------------------------------------------------
-- 3. feed_log 加饲喂来源 + 产品/操作人/库位（行64 + 行56/57/55 按产品归集 + 显示位置/操作人）
--    全列 NULL 默认，兼容现有历史行。
-- ------------------------------------------------------------
ALTER TABLE t_warehouse_feed_log
    ADD COLUMN feed_type    VARCHAR(20) NULL COMMENT '饲喂来源（字典 djs_feed_type：veg_handle 毛菜间 / warehouse 仓库）' AFTER crop_name,
    ADD COLUMN product_id   BIGINT      NULL COMMENT '产品 ID（按产品归集饲喂量用；毛菜间来源经 crop→related_product 反解可空）' AFTER feed_type,
    ADD COLUMN location_id  BIGINT      NULL COMMENT '饲喂位置（库位 FK；仓库领用饲喂来源有值）' AFTER product_id,
    ADD COLUMN operator_id  BIGINT      NULL COMMENT '操作人 user_id' AFTER location_id;

-- ------------------------------------------------------------
-- 4. 饲喂来源字典 djs_feed_type（行64 2 值）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (1007004, '1001', '饲喂来源', 'djs_feed_type', 1, NOW(), 'WMS-LOSS-001 饲料饲喂来源（行64）');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1007040, '1001', 1, '毛菜间', 'veg_handle', 'djs_feed_type', '', 'success', 'N', 1, NOW(), '毛菜处理间去向选饲料饲喂'),
    (1007041, '1001', 2, '仓库',   'warehouse',  'djs_feed_type', '', 'primary', 'N', 1, NOW(), '仓库物资领用选饲料饲喂');
