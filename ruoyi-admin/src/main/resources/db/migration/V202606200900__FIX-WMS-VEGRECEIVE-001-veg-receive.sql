-- FIX-WMS-VEGRECEIVE-001：果蔬月台收货（蔬菜处理 → 果蔬月台）。
--
-- 业务：蔬菜处理产出（毛菜处理 handle_target=2 月台 → vegetable_handle.send_platform_weight）
-- 是"已发往月台、待入果蔬间"的果蔬；本表记录"果蔬月台收货入库"动作（自产 + 外购两类）。
--   - 自产（receive_type=1）：把上游月台待入库量入到保鲜室 location_stock（按 plot_id 维度，三维互斥之一）
--   - 外购（receive_type=2）：外购果蔬产品验收入库（按 product_id 维度 + supplier_id）
--
-- 自产"待入库量"闭环（不造假）：
--   待入库 = SUM(vegetable_handle.send_platform_weight) - SUM(本表 receive_type=1 已入库 weight)，按 crop / plot 聚合
--
-- 入库写动作（service 同事务）：本表 INSERT + location_stock UPSERT（行锁增量 / 不存在则 INSERT）+ stock_flow INSERT
--   flow_type=veg_receive_in（自产）/ veg_purchase_in（外购），inout_type=IN。

CREATE TABLE IF NOT EXISTS t_warehouse_veg_receive
(
    id              BIGINT       NOT NULL COMMENT '主键（雪花）',
    receive_no      VARCHAR(32)  NULL     COMMENT '收货流水号（冗余 stock_flow.flow_no，便于 mp 回显）',
    receive_type    TINYINT      NOT NULL COMMENT '收货类型 djs_veg_receive_type：1=自产 / 2=外购',
    crop_id         BIGINT       NULL     COMMENT '作物 ID（自产 FK→t_plant_crop_info.id）/ 外购产品 ID（FK→t_warehouse_product_info.id）',
    crop_name       VARCHAR(64)  NULL     COMMENT '作物 / 产品名称（冗余，便于列表展示免 JOIN）',
    plot_id         BIGINT       NULL     COMMENT '地块 ID（仅自产 FK→t_plant_plot_info.id；外购为 NULL）',
    plot_code       VARCHAR(32)  NULL     COMMENT '地块编号（冗余，仅自产）',
    product_id      BIGINT       NULL     COMMENT '外购对应产品 ID（FK→t_warehouse_product_info.id；自产为 NULL，库存按 plot_id 维度）',
    weight          DECIMAL(12, 3) NOT NULL COMMENT '本次入库重量(kg)',
    supplier_id     BIGINT       NULL     COMMENT '供应商 ID（仅外购 FK→t_md_supplier.id）',
    supplier_name   VARCHAR(64)  NULL     COMMENT '供应商名称（冗余，仅外购）',
    location_id     BIGINT       NOT NULL COMMENT '入库库位 ID（FK→t_warehouse_location_info.id；保鲜室）',
    location_name   VARCHAR(64)  NULL     COMMENT '入库库位名称（冗余）',
    is_finish       TINYINT      NOT NULL DEFAULT 2 COMMENT '是否入库完成 djs_yes_no：1=是 / 2=否（仅自产用，标记该地块/作物入库结束）',
    receive_status  VARCHAR(20)  NOT NULL DEFAULT 'done' COMMENT '收货状态 djs_veg_receive_status：pending 待入库 / processing 入库中 / done 已入库',
    operator_id     BIGINT       NULL     COMMENT '入库人（FK→sys_user.user_id；ADR-0007 冗余最后操作人）',
    receive_time    DATETIME     NULL     COMMENT '收货入库时间',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户编号',
    create_dept     BIGINT       NULL     COMMENT '创建部门',
    create_by       BIGINT       NULL     COMMENT '创建者',
    create_time     DATETIME     NULL     COMMENT '创建时间',
    update_by       BIGINT       NULL     COMMENT '更新者',
    update_time     DATETIME     NULL     COMMENT '更新时间',
    remark          VARCHAR(500) NULL     COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0 未删 / 1 已删）',
    del_unique      BIGINT       NOT NULL DEFAULT 0   COMMENT '软删唯一性辅助列',
    PRIMARY KEY (id),
    KEY idx_veg_receive_crop (tenant_id, crop_id),
    KEY idx_veg_receive_plot (tenant_id, plot_id),
    KEY idx_veg_receive_type (tenant_id, receive_type),
    UNIQUE KEY uk_veg_receive_no (tenant_id, receive_no, del_unique)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '果蔬月台收货记录（自产二次入库 + 外购验收入库）';

-- ============ 字典 seed ============
-- 1) 收货类型 djs_veg_receive_type（dict_code 段 103200，避开已用 102xxx / 103000-103110 / 禁用 102320 / 1027xx）
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103200, '1001', '果蔬收货类型', 'djs_veg_receive_type', 1, NOW(), 'FIX-WMS-VEGRECEIVE-001 果蔬月台收货：自产 / 外购');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (103201, '1001', 1, '自产', '1', 'djs_veg_receive_type', '', 'primary', 'Y', 1, NOW(), '自产果蔬月台收货'),
    (103202, '1001', 2, '外购', '2', 'djs_veg_receive_type', '', 'success', 'N', 1, NOW(), '外购果蔬验收入库');

-- 2) 收货状态 djs_veg_receive_status
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103210, '1001', '果蔬收货状态', 'djs_veg_receive_status', 1, NOW(), 'FIX-WMS-VEGRECEIVE-001 果蔬间入库状态');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (103211, '1001', 1, '待入库', 'pending',    'djs_veg_receive_status', '', 'info',    'N', 1, NOW(), '尚未开始入库'),
    (103212, '1001', 2, '入库中', 'processing', 'djs_veg_receive_status', '', 'warning', 'N', 1, NOW(), '部分入库'),
    (103213, '1001', 3, '已入库', 'done',       'djs_veg_receive_status', '', 'success', 'N', 1, NOW(), '入库完成');

-- 3) stock_flow.flow_type 补两值（果蔬月台收货入库）—— djs_flow_type 现有最大 sort=15（102450015）
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102450016, '1001', 16, '果蔬月台入库', 'veg_receive_in',  'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-VEGRECEIVE-001 自产果蔬月台收货入库'),
    (102450017, '1001', 17, '外购果蔬入库', 'veg_purchase_in', 'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-VEGRECEIVE-001 外购果蔬验收入库');
