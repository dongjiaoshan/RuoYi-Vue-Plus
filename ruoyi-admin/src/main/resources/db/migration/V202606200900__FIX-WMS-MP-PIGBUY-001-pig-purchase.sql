-- ============================================================
-- FIX-WMS-MP-PIGBUY-001 外购猪只到货入库
--
-- 1. t_warehouse_pig_purchase 外购猪只到货登记主表
-- 2. 字典 seed：djs_pig_source（活猪 live / 白条 white_bar）
-- 3. 字典 seed：djs_pig_purchase_status（待处理 pending / 已处理 done）
--
-- 口径（D-FIX-7 #4.3）：外购活猪 / 白条独立登记到货入库，作为燎毛 / 分割的
-- 另一来源（与自养出栏并列），**不进养殖 BRD 引种**（引种=繁殖群）。
-- 本卡范围：建表 + 到货登记 + 外购待处理列表（mp）。与 burn 合流（并入
-- 燎毛 pendingList）留 follow-up，不改 burn 文件。
--
-- 独占子域：仅本表 + 自有 controller，不动 purchase / burn 共享模块。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_warehouse_pig_purchase 外购猪只到货登记主表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_warehouse_pig_purchase;
CREATE TABLE t_warehouse_pig_purchase (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（MP snowflake）',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    purchase_no     VARCHAR(32)  NOT NULL COMMENT '业务码 PBUY+YYMMDD+4 位序号（本表幂等键）',
    source_type     VARCHAR(16)  NOT NULL COMMENT '来源类型 字典 djs_pig_source：live=活猪 / white_bar=白条',
    quantity        INT          NOT NULL COMMENT '到货数量（头/条，service 校验 > 0）',
    arrive_weight   DECIMAL(12,3) NOT NULL COMMENT '到货重量 kg（service 校验 > 0）',
    supplier_name   VARCHAR(128) NOT NULL COMMENT '供应商名称（自由文本）',
    arrive_time     DATETIME     NOT NULL COMMENT '到货时间',
    purchase_status VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '处理状态 字典 djs_pig_purchase_status：pending=待处理 / done=已处理（已进燎毛/分割）',
    operator_id     BIGINT       NULL COMMENT '登记人 FK → sys_user.user_id（LoginHelper 注入，ADR-0007）',
    proof_oss_ids   VARCHAR(500) NULL COMMENT '到货凭证图 OSS IDs CSV（CameraUploadWithWatermark）',
    create_dept     BIGINT       NULL COMMENT '创建部门',
    create_by       BIGINT       NULL COMMENT '创建者',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_by       BIGINT       NULL COMMENT '更新者',
    update_time     DATETIME     NULL COMMENT '更新时间',
    remark          VARCHAR(500) NULL COMMENT '备注',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_no (tenant_id, purchase_no, del_unique),
    KEY idx_source_type (tenant_id, source_type),
    KEY idx_arrive_time (tenant_id, arrive_time),
    KEY idx_purchase_status (tenant_id, purchase_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外购猪只到货登记单（FIX-WMS-MP-PIGBUY-001）';

-- ------------------------------------------------------------
-- 2. 字典 seed djs_pig_source 外购猪只来源类型
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102700, '1001', '外购猪只来源', 'djs_pig_source', 1, NOW(), 'FIX-WMS-MP-PIGBUY-001 外购猪只来源类型');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1027000, '1001', 0, '活猪',  'live',      'djs_pig_source', '', 'primary', 'Y', 1, NOW()),
    (1027001, '1001', 1, '白条',  'white_bar', 'djs_pig_source', '', 'success', 'N', 1, NOW());

-- ------------------------------------------------------------
-- 3. 字典 seed djs_pig_purchase_status 外购猪只处理状态
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102701, '1001', '外购猪只处理状态', 'djs_pig_purchase_status', 1, NOW(), 'FIX-WMS-MP-PIGBUY-001 外购猪只处理状态');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1027010, '1001', 0, '待处理', 'pending', 'djs_pig_purchase_status', '', 'info',    'Y', 1, NOW()),
    (1027011, '1001', 1, '已处理', 'done',    'djs_pig_purchase_status', '', 'success', 'N', 1, NOW());
