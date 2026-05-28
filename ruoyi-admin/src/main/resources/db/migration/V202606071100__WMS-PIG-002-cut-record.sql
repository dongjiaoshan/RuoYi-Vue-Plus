-- ============================================================
-- WMS-PIG-002 分割工序记录
--
-- 1. t_warehouse_bar_info 白条主表 —— baseline 占位与 doc/11 §2.8 字段偏离，DROP+CREATE 重建
-- 2. t_warehouse_product_inhouse 过程产品（分割产出 / 蔬菜处理产出，doc/11 §2.7）—— 新建
-- 3. t_warehouse_pig_cut_record 分割记录主表（本 ticket 核心）—— 新建
-- 4. 字典 4 新建：djs_bar_status / djs_bar_in_method / djs_bar_out_method / djs_pig_cut_part
-- 5. sys_menu seed 9076-9084
--
-- 跨域事务：service 层 @Transactional 控制 3 阶段（白条领用 / 出库称重 / 出库完成）；
-- 每阶段同事务跨 cut_record + bar_info + product_inhouse + stock_flow 4 表，任一步失败整体回滚。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_bar_info 白条主表（重建，对齐 doc/11 §2.8 字段权威）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_bar_info;
CREATE TABLE t_warehouse_bar_info (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    bar_id              VARCHAR(32)  NOT NULL COMMENT '业务码 BAR+yyMMdd+4 位（doc/11 §2.8 R6）',
    marketing_time      DATETIME     NULL COMMENT '出栏时间（doc/11 §2.8 R7 改名，xlsx out_time）',
    marketing_weight    DECIMAL(12,3) NULL COMMENT '出栏重量 kg（doc/11 §2.8 R8）',
    arrive_time         DATETIME     NULL COMMENT '燎毛间到场时间',
    arrive_weight       DECIMAL(12,3) NULL COMMENT '到场重量 kg（燎毛前过磅）',
    ear_no              VARCHAR(32)  NULL COMMENT '猪只耳号（FK 关联 t_farm_pig_info.ear_no；外购无耳号）',
    in_weight           DECIMAL(12,3) NULL COMMENT '燎毛后入库重量 kg',
    in_time             DATETIME     NULL COMMENT '入库时间',
    in_method           TINYINT      NULL COMMENT '1=燎毛间 / 2=分割间（djs_bar_in_method）',
    out_time            DATETIME     NULL COMMENT '出白条库时间（本 ticket cut_done 阶段写入）',
    out_weight          DECIMAL(12,3) NULL COMMENT '出库重量 kg（本 ticket cut_done 阶段写入 = in_weight - drip_loss）',
    out_method          TINYINT      NULL COMMENT '1=发货领用 / 2=分割间（djs_bar_out_method；本 ticket 分割工序写 2）',
    acid_remove_time    INT          NULL COMMENT '排酸时长（分钟，本 ticket cut_done 阶段写入 = cut_done_time - pickup_time）',
    acid_remove_loss    DECIMAL(12,3) NULL COMMENT '排酸损耗 kg（本 ticket cut_done 阶段写入 = drip_loss）',
    buy_date            DATE         NULL COMMENT '采购日期（仅外购）',
    buy_weight          DECIMAL(12,3) NULL COMMENT '采购重量（仅外购）',
    supplier_id         BIGINT       NULL COMMENT '供应商 ID（仅外购）',
    mark_id             VARCHAR(32)  NULL COMMENT '标识号',
    status              VARCHAR(16)  NOT NULL DEFAULT 'pending_singe' COMMENT '字典 djs_bar_status 7 态',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建者',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NULL COMMENT '更新时间',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bar_id (tenant_id, bar_id, del_unique),
    KEY idx_ear_no (tenant_id, ear_no),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='白条信息表（doc/11 §2.8，重建以对齐字段权威）';

-- ------------------------------------------------------------
-- 2. t_warehouse_product_inhouse 过程产品（分割产出 / 蔬菜处理产出，doc/11 §2.7）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_product_inhouse;
CREATE TABLE t_warehouse_product_inhouse (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    produce_date        DATE         NOT NULL COMMENT '生产日期',
    product_id          BIGINT       NOT NULL COMMENT 'FK → t_warehouse_product_info.id（分割品标准 SKU）',
    product_name        VARCHAR(128) NOT NULL COMMENT '产品名称（冗余）',
    product_type        TINYINT      NOT NULL COMMENT 'djs_product_type 1=自产 / 2=外购 / 3=礼盒',
    product_unit        VARCHAR(16)  NOT NULL COMMENT '计量单位',
    product_spec        VARCHAR(64)  NULL COMMENT '规格',
    plot_id             BIGINT       NULL COMMENT '蔬菜来源地块（D9 WMS-VEG-001 用）',
    ear_no              VARCHAR(32)  NULL COMMENT '猪肉来源耳号（冗余便于查询）',
    product_sort        INT          NULL COMMENT '产品序号',
    product_weight      DECIMAL(12,3) NOT NULL COMMENT '产品重量 kg',
    produce_time        DATETIME     NOT NULL COMMENT '生产时间',
    white_bar_id        BIGINT       NULL COMMENT 'FK → t_warehouse_bar_info.id（分割时关联）',
    cut_part            VARCHAR(32)  NULL COMMENT '分割部位 djs_pig_cut_part：lean/part/bone/skin/scrap',
    material_id         BIGINT       NULL COMMENT '原材料 ID',
    material_consume    DECIMAL(12,3) NULL COMMENT '原材料耗用 kg',
    location_id         BIGINT       NULL COMMENT '入库库位 FK → t_warehouse_location_info.id（冻品库 / 蔬菜鲜品库）',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建者',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NULL COMMENT '更新时间',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    KEY idx_white_bar (tenant_id, white_bar_id),
    KEY idx_plot (tenant_id, plot_id),
    KEY idx_product (tenant_id, product_id),
    KEY idx_produce_date (tenant_id, produce_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='过程产品（非发货）信息表（doc/11 §2.7）';

-- ------------------------------------------------------------
-- 3. t_warehouse_pig_cut_record 分割记录主表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_pig_cut_record;
CREATE TABLE t_warehouse_pig_cut_record (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id           VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    cut_id              VARCHAR(32)  NOT NULL COMMENT '业务码 CUT+yyMMdd+4 位（本表幂等键）',
    white_bar_id        BIGINT       NOT NULL COMMENT 'FK → t_warehouse_bar_info.id（被分割的白条）',
    bar_id              VARCHAR(32)  NOT NULL COMMENT '冗余 bar_info.bar_id 便于查询',
    ear_no              VARCHAR(32)  NULL COMMENT '冗余白条耳号',
    pickup_time         DATETIME     NOT NULL COMMENT '白条领用时间（pickup 阶段写）',
    cut_start_time      DATETIME     NULL COMMENT '分割开始时间（cutOut 首次提交阶段写）',
    cut_done_time       DATETIME     NULL COMMENT '出库完成时间（cutDone 阶段写）',
    pickup_weight       DECIMAL(12,3) NULL COMMENT '领用重量 kg（= bar_info.in_weight 快照）',
    drip_loss           DECIMAL(12,3) NULL COMMENT '滴水损失 kg（cutDone 阶段写）',
    acid_remove_minutes INT          NULL COMMENT '排酸时长（min，= cut_done_time - pickup_time）',
    operator_id         BIGINT       NULL COMMENT 'mp 分割师 FK → sys_user.user_id',
    location_id         BIGINT       NULL COMMENT '分割产出入冻品库 location FK',
    target_store_id     BIGINT       NULL COMMENT '指定目标门店（V1 可空）',
    target_demand_id    BIGINT       NULL COMMENT '关联需求 FK → t_warehouse_demand_manage.id（V1 可空）',
    is_half             TINYINT      NOT NULL DEFAULT 2 COMMENT '是否半扇分割 1=是 / 2=否（整只）',
    cut_status          VARCHAR(16)  NOT NULL DEFAULT 'pending_pickup' COMMENT 'pending_pickup / picked / cutting / done',
    proof_oss_ids       VARCHAR(500) NULL COMMENT '凭证图 OSS IDs CSV（biz_type=warehouse_pig_cut）',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建者',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NULL COMMENT '更新时间',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cut_id (tenant_id, cut_id, del_unique),
    KEY idx_white_bar (tenant_id, white_bar_id),
    KEY idx_status (tenant_id, cut_status),
    KEY idx_pickup_time (tenant_id, pickup_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪肉分割工序记录单（WMS-PIG-002）';

-- ------------------------------------------------------------
-- 4. 字典 seed（dict_id 102200-102239；dict_code 1022000-1022304）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark) VALUES
    (102200, '1001', '白条状态', 'djs_bar_status', 1, NOW(), 'WMS-PIG-002 doc/11 §2.8'),
    (102210, '1001', '白条入库方式', 'djs_bar_in_method', 1, NOW(), 'WMS-PIG-002 doc/11 §2.8 R14'),
    (102220, '1001', '白条出库方式', 'djs_bar_out_method', 1, NOW(), 'WMS-PIG-002 doc/11 §2.8 R17'),
    (102230, '1001', '猪肉分割部位', 'djs_pig_cut_part', 1, NOW(), 'WMS-PIG-002 doc/06');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    -- djs_bar_status 7 态
    (1022000, '1001', 0, '待燎毛', 'pending_singe', 'djs_bar_status', '', 'info',    'Y', 1, NOW()),
    (1022001, '1001', 1, '燎毛中',  'singing',       'djs_bar_status', '', 'warning', 'N', 1, NOW()),
    (1022002, '1001', 2, '待入库',  'singed',        'djs_bar_status', '', 'warning', 'N', 1, NOW()),
    (1022003, '1001', 3, '已入库',  'in_stock',      'djs_bar_status', '', 'success', 'N', 1, NOW()),
    (1022004, '1001', 4, '待分割',  'pending_cut',   'djs_bar_status', '', 'info',    'N', 1, NOW()),
    (1022005, '1001', 5, '分割中',  'cutting',       'djs_bar_status', '', 'warning', 'N', 1, NOW()),
    (1022006, '1001', 6, '分割完成', 'cut_done',     'djs_bar_status', '', 'success', 'N', 1, NOW()),
    -- djs_bar_in_method
    (1022100, '1001', 0, '燎毛间', '1', 'djs_bar_in_method', '', 'primary', 'Y', 1, NOW()),
    (1022101, '1001', 1, '分割间', '2', 'djs_bar_in_method', '', 'success', 'N', 1, NOW()),
    -- djs_bar_out_method
    (1022200, '1001', 0, '发货领用', '1', 'djs_bar_out_method', '', 'primary', 'Y', 1, NOW()),
    (1022201, '1001', 1, '分割间',   '2', 'djs_bar_out_method', '', 'success', 'N', 1, NOW()),
    -- djs_pig_cut_part
    (1022300, '1001', 0, '精瘦肉', 'lean',  'djs_pig_cut_part', '', 'success', 'Y', 1, NOW()),
    (1022301, '1001', 1, '部位肉', 'part',  'djs_pig_cut_part', '', 'success', 'N', 1, NOW()),
    (1022302, '1001', 2, '骨类',   'bone',  'djs_pig_cut_part', '', 'info',    'N', 1, NOW()),
    (1022303, '1001', 3, '猪皮',   'skin',  'djs_pig_cut_part', '', 'info',    'N', 1, NOW()),
    (1022304, '1001', 4, '碎料',   'scrap', 'djs_pig_cut_part', '', 'warning', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 5. sys_menu seed（9076-9084；仓库段 9000-9999，PIG-002 占 9076-9085）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 父菜单（C 类，admin 列表页）
    (9076, '分割记录', 9000, 4, 'pigCut', 'djs-warehouse/pigCut/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:pigCut:list', 'cut', 1, NOW(), 'WMS-PIG-002'),

    -- admin 权限（只 list / export，无 add/edit/remove）
    (9077, '分割查询', 9076, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:pigCut:list', '#', 1, NOW(), 'WMS-PIG-002'),
    (9081, '分割导出', 9076, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:pigCut:export', '#', 1, NOW(), 'WMS-PIG-002'),

    -- mp 端权限（3 阶段提交）
    (9082, '白条领用', 9076, 6, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pigCut:in', '#', 1, NOW(), 'WMS-PIG-002 mp'),
    (9083, '出库称重', 9076, 7, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pigCut:out', '#', 1, NOW(), 'WMS-PIG-002 mp'),
    (9084, '出库完成', 9076, 8, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:pigCut:done', '#', 1, NOW(), 'WMS-PIG-002 mp');

-- ------------------------------------------------------------
-- 6. 分割品标准 SKU seed（同 D8 #11 决策 a，PROD-PIG-<PART>-01；
--    PigCutRecordServiceImpl.submitCutOut 按 cut_part 查 productId 写入 product_inhouse + stock_flow）
-- ------------------------------------------------------------
INSERT INTO t_warehouse_product_info
    (id, tenant_id, product_id, product_name, product_type, product_unit,
     belong_type, product_attr, product_workshop, product_status, is_delivery, is_buy_out,
     del_flag, del_unique, create_time)
VALUES
    (100000000000000101, '1001', 'PROD-PIG-LEAN-01',  '猪肉·精瘦肉', 1, 'kg', 'pork', 1, 2, 0, 1, 0, '0', 0, NOW()),
    (100000000000000102, '1001', 'PROD-PIG-PART-01',  '猪肉·部位肉', 1, 'kg', 'pork', 1, 2, 0, 1, 0, '0', 0, NOW()),
    (100000000000000103, '1001', 'PROD-PIG-BONE-01',  '猪肉·骨类',   1, 'kg', 'pork', 1, 2, 0, 1, 0, '0', 0, NOW()),
    (100000000000000104, '1001', 'PROD-PIG-SKIN-01',  '猪肉·猪皮',   1, 'kg', 'pork', 1, 2, 0, 1, 0, '0', 0, NOW()),
    (100000000000000105, '1001', 'PROD-PIG-SCRAP-01', '猪肉·碎料',   1, 'kg', 'pork', 1, 2, 0, 1, 0, '0', 0, NOW())
ON DUPLICATE KEY UPDATE
    product_name = VALUES(product_name),
    product_type = VALUES(product_type),
    product_unit = VALUES(product_unit),
    belong_type  = VALUES(belong_type),
    product_attr = VALUES(product_attr),
    product_workshop = VALUES(product_workshop),
    product_status = VALUES(product_status),
    is_delivery  = VALUES(is_delivery),
    is_buy_out   = VALUES(is_buy_out);
