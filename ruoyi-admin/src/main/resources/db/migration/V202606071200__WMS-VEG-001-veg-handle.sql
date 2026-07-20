-- ============================================================
-- WMS-VEG-001 毛菜处理 + 果蔬月台
--
-- 1. t_warehouse_planting_record（DROP + CREATE，D2 baseline 占位字段不全）
-- 2. t_warehouse_vegetable_handle 毛菜处理汇总
-- 3. t_warehouse_handle_record 毛菜处理记录流水
-- 4. 字典 2 新建：djs_veg_handle_status / djs_record_type
-- 5. sys_menu seed 9090-9098
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_planting_record（doc/11 §2.14 完整字段）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_planting_record;
CREATE TABLE t_warehouse_planting_record (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001',
    plot_id         BIGINT       NOT NULL                COMMENT 'FK → t_plant_plot_info.id',
    crop_id         BIGINT       NOT NULL                COMMENT 'FK → t_plant_crop_info.id',
    plot_name       VARCHAR(64)  NULL                    COMMENT '冗余',
    crop_name       VARCHAR(64)  NULL                    COMMENT '冗余',
    plant_date      DATE         NULL                    COMMENT '种植日期',
    harvest_date    DATE         NOT NULL                COMMENT '采摘日期',
    harvest_weight  DECIMAL(12,3) NOT NULL               COMMENT '收获产量(kg)',
    expect_yield    DECIMAL(12,3) NULL                   COMMENT '预计产量',
    avg_yield       DECIMAL(12,3) NULL                   COMMENT '平均亩产',
    is_loss         TINYINT      NULL                    COMMENT '是否损失 1=是/2=否',
    disaster_record VARCHAR(500) NULL                    COMMENT '灾害记录（多个逗号分隔）',
    team_id         BIGINT       NULL                    COMMENT '班组 ID',
    team_name       VARCHAR(64)  NULL                    COMMENT '班组名称',
    data_date       DATETIME     NOT NULL                COMMENT '数据生成时间（一般同采摘时间）',
    handle_status   VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_veg_handle_status：mp 处理工领取标记',
    create_dept     BIGINT       NULL,
    create_by       BIGINT       NULL,
    create_time     DATETIME     NULL,
    update_by       BIGINT       NULL,
    update_time     DATETIME     NULL,
    remark          VARCHAR(500) NULL,
    del_flag        CHAR(1)      NULL DEFAULT '0',
    del_unique      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plot_crop (tenant_id, plot_id, crop_id),
    KEY idx_handle_status (tenant_id, handle_status),
    KEY idx_data_date (tenant_id, data_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库视角种植记录（doc/11 §2.14；WMS-VEG-001 建表 / D14 CROSS-FLOW-002 listener 写入）';

-- ------------------------------------------------------------
-- 2. t_warehouse_vegetable_handle 毛菜处理汇总（doc/11 §2.12）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_vegetable_handle;
CREATE TABLE t_warehouse_vegetable_handle (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id               VARCHAR(20)  NOT NULL DEFAULT '1001',
    planting_record_id      BIGINT       NULL            COMMENT 'FK → t_warehouse_planting_record.id（上游来源）',
    plot_id                 BIGINT       NOT NULL        COMMENT 'FK → t_plant_plot_info.id',
    crop_id                 BIGINT       NOT NULL        COMMENT 'FK → t_plant_crop_info.id',
    product_id              BIGINT       NULL            COMMENT 'FK → t_warehouse_product_info.id（belong_type=vegetable）',
    pick_start_time         DATETIME     NOT NULL        COMMENT '采摘开始时间',
    pick_end_time           DATETIME     NULL            COMMENT '采摘结束时间',
    picked_weight           DECIMAL(12,3) NULL DEFAULT 0 COMMENT '已摘(kg)',
    handled_weight          DECIMAL(12,3) NULL DEFAULT 0 COMMENT '处理后(kg) = 月台 + 入库',
    feed_weight             DECIMAL(12,3) NULL DEFAULT 0 COMMENT '饲料饲喂(kg)',
    send_platform_weight    DECIMAL(12,3) NULL DEFAULT 0 COMMENT '发往蔬菜月台(kg)',
    stock_in_weight         DECIMAL(12,3) NULL DEFAULT 0 COMMENT '入库(kg)',
    loss_weight             DECIMAL(12,3) NULL DEFAULT 0 COMMENT '损耗(kg) = 已摘 - 处理 - 饲喂',
    is_weighed              TINYINT      NOT NULL DEFAULT 2 COMMENT '1=是 / 2=否',
    is_finish               TINYINT      NOT NULL DEFAULT 2 COMMENT '1=是 / 2=否',
    handle_status           VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'djs_veg_handle_status pending/processing/done',
    create_dept             BIGINT       NULL,
    create_by               BIGINT       NULL,
    create_time             DATETIME     NULL,
    update_by               BIGINT       NULL,
    update_time             DATETIME     NULL,
    remark                  VARCHAR(500) NULL,
    del_flag                CHAR(1)      NULL DEFAULT '0',
    del_unique              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_planting_record (tenant_id, planting_record_id),
    KEY idx_plot_crop_date (tenant_id, plot_id, crop_id, pick_start_time),
    KEY idx_status (tenant_id, handle_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='毛菜处理间汇总（doc/11 §2.12）';

-- ------------------------------------------------------------
-- 3. t_warehouse_handle_record 毛菜处理流水（doc/11 §2.13）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_handle_record;
CREATE TABLE t_warehouse_handle_record (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001',
    handle_id       BIGINT        NOT NULL                COMMENT 'FK → t_warehouse_vegetable_handle.id',
    plot_id         BIGINT        NOT NULL                COMMENT '冗余 plot_id',
    crop_id         BIGINT        NOT NULL                COMMENT '冗余 crop_id',
    record_type     TINYINT       NOT NULL                COMMENT 'djs_record_type 1=采收 / 2=处理',
    record_weight   DECIMAL(12,3) NOT NULL                COMMENT '本次重量(kg)',
    is_weighed      TINYINT       NULL                    COMMENT '1=是/2=否',
    is_finish       TINYINT       NULL                    COMMENT '1=是/2=否',
    handle_target   TINYINT       NULL                    COMMENT 'djs_handle_target 1=入库/2=月台/3=饲料（record_type=2 必填）',
    location_id     BIGINT        NULL                    COMMENT '入库库位 FK → t_warehouse_location_info.id（handle_target=1 必填）',
    handle_user     BIGINT        NOT NULL                COMMENT 'FK → sys_user.user_id',
    handle_time     DATETIME      NOT NULL                COMMENT '处理时间',
    proof_oss_ids   VARCHAR(500)  NULL                    COMMENT 'CameraUploadWithWatermark biz_type=warehouse_veg_handle',
    remark          VARCHAR(500)  NULL,
    create_dept     BIGINT        NULL,
    create_by       BIGINT        NULL,
    create_time     DATETIME      NULL,
    update_by       BIGINT        NULL,
    update_time     DATETIME      NULL,
    del_flag        CHAR(1)       NULL DEFAULT '0',
    del_unique      BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_handle_id (tenant_id, handle_id),
    KEY idx_handle_time (tenant_id, handle_time),
    KEY idx_handle_user (tenant_id, handle_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='毛菜处理记录流水（doc/11 §2.13）';

-- ------------------------------------------------------------
-- 4. 字典 seed（ADR-0004 djs_ 命名）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (102300, '1001', '蔬菜处理状态', 'djs_veg_handle_status', 1, NOW(), 'WMS-VEG-001'),
    (102310, '1001', '处理记录类型', 'djs_record_type',       1, NOW(), 'WMS-VEG-001 doc/11 §2.13 R8');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1023000, '1001', 0, '待处理', 'pending',    'djs_veg_handle_status', '', 'info',    'Y', 1, NOW()),
    (1023001, '1001', 1, '处理中', 'processing', 'djs_veg_handle_status', '', 'warning', 'N', 1, NOW()),
    (1023002, '1001', 2, '已完成', 'done',       'djs_veg_handle_status', '', 'success', 'N', 1, NOW()),
    (1023100, '1001', 0, '采收', '1', 'djs_record_type', '', 'primary', 'Y', 1, NOW()),
    (1023101, '1001', 1, '处理', '2', 'djs_record_type', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 5. sys_menu seed（仓库 9000-9999 / 蔬菜处理段 9090-9099 ADR-0006）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9090, '毛菜处理', 9000, 5, 'vegHandle', 'djs-warehouse/vegHandle/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:vegHandle:list', 'leaf', 1, NOW(), 'WMS-VEG-001'),

    (9091, '处理查询', 9090, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:vegHandle:list',                       '#', 1, NOW(), 'WMS-VEG-001'),
    (9095, '处理导出', 9090, 5, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:vegHandle:export',                     '#', 1, NOW(), 'WMS-VEG-001'),
    (9096, '处理录入', 9090, 6, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:vegHandle:handle',              '#', 1, NOW(), 'WMS-VEG-001 mp'),
    (9097, '月台入库', 9090, 7, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:vegHandle:platform',            '#', 1, NOW(), 'WMS-VEG-001 mp'),
    (9098, '蔬菜入库', 9090, 8, '', '', '', 1, 0, 'F', '0', '0',
     'djs:applet:warehouse:vegHandle:stockIn',             '#', 1, NOW(), 'WMS-VEG-001 mp');

-- ------------------------------------------------------------
-- 6. mp role 默认补 mp 端 3 个 perm（admin role 由 ruoyi sys_admin 拥有全部）
--    若 mp 专用 role 不存在则跳过；本 seed 不强依赖 role 配置
-- ------------------------------------------------------------
