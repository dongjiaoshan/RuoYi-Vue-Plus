-- ============================================================
-- BRD-MED-002 药品领用 / 退回 / 损耗台账
--
-- 现状：
--   表 t_breed_medicine_info（药品库）+ t_breed_medicine_batch（批次）由
--   BRD-MED-001 V202605221101 建好；库存字段为 t_breed_medicine_batch.quantity
--   DECIMAL(18,3)（非 prompt 中提及的 remaining_qty，prompt 与 DB 不一致，
--   依"DB 为权威源"原则按 quantity 扣减）。
--
-- 本文件做 1 件事：建 t_breed_medicine_usage 表（领用 / 退回 / 损耗共表，
-- 由 usage_type 区分；BRD-MED-003 用药治疗后台依赖 use_date 字段过滤
-- "近 3 天已领"）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_breed_medicine_usage;
CREATE TABLE t_breed_medicine_usage (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    batch_id        BIGINT        NOT NULL COMMENT '药品批次 ID（引用 t_breed_medicine_batch.id）',
    medicine_id     BIGINT        NOT NULL COMMENT '药品 ID（引用 t_breed_medicine_info.id，冗余便于报表）',
    usage_type      VARCHAR(20)   NOT NULL DEFAULT 'use' COMMENT '类型：use=领用 / return=退回 / loss=损耗',
    usage_qty       DECIMAL(18,3) NOT NULL COMMENT '数量（与批次 quantity 对齐 DECIMAL(18,3)）',
    use_date        DATE          NOT NULL COMMENT '业务日期（领用/退回/损耗发生日，非 create_time；BRD-MED-003 按此过滤）',
    related_pen_id  BIGINT        NULL COMMENT '关联栏位（可选，批次级用药关联到栏位）',
    pig_id          BIGINT        NULL COMMENT '关联猪只（可选，单只用药场景）',
    schedule_id     BIGINT        NULL COMMENT '关联用药计划 ID（可选，BRD-MD-003 t_farm_production_cycle_config 中的 schedule 项）',
    create_dept     BIGINT        NULL COMMENT '创建部门',
    create_by       BIGINT        NULL COMMENT '创建人',
    create_time     DATETIME      NULL COMMENT '创建时间',
    update_by       BIGINT        NULL COMMENT '更新人',
    update_time     DATETIME      NULL COMMENT '更新时间',
    del_flag        CHAR(1)       DEFAULT '0' COMMENT '删除标志',
    remark          VARCHAR(500)  NULL COMMENT '备注',
    del_unique      BIGINT        NOT NULL DEFAULT 0 COMMENT "软删 token（应用层 update del_flag='1' 时同步 SET del_unique=id）",
    PRIMARY KEY (id),
    KEY idx_batch_id (batch_id),
    KEY idx_medicine_id (medicine_id),
    KEY idx_use_date (use_date),
    KEY idx_tenant_use_date (tenant_id, use_date),
    KEY idx_pig_id (pig_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品领用台账（领用/退回/损耗共表，BRD-MED-002）';
