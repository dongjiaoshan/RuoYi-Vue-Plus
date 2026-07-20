-- 流程性问题 row7：猪只每日状态快照表（期末存栏类指标的历史唯一可信数据源）。
--
-- 背景：t_farm_indicator_record 的期末存栏类指标（end_production_sow_count 等 8 项）原先直接
-- COUNT(t_farm_pig_info) 实时主表。主表因配种/分娩/转群/淘汰/死亡/状态修改持续覆盖历史态，
-- 回溯重跑历史日期时主表已变 → 往期期末指标失真、无法对账溯源。
--
-- 方案：每日收盘固化前一自然日「在群有效猪只」（current_status<>'END'）全量最小信息为快照。
-- 期末存栏类指标改绑当日快照；已生成快照的日期不再重采（免受主表后续变动影响 → 历史可复核）。
--
-- 说明：本表是派生快照（append-only，非业务 CRUD 实体，无 MP 实体 / 无软删 / 无编辑），
-- 故不套 6 公共字段全集；只留 tenant_id + create_time 作审计。复合主键
-- (tenant_id, snap_date, pig_id) 天然保证「一猪一日一行」+ 采集幂等。
CREATE TABLE IF NOT EXISTS t_farm_pig_snapshot (
    tenant_id      VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户（V1 恒 1001）',
    snap_date      DATE        NOT NULL COMMENT '快照自然日（该日收盘时点）',
    pig_id         BIGINT      NOT NULL COMMENT '猪只 ID（FK t_farm_pig_info.id）',
    pig_type       VARCHAR(20) NULL COMMENT '猪类型 sow/boar/fattening/piglet（快照当时值）',
    current_status VARCHAR(20) NULL COMMENT '繁殖态/生命周期状态码（快照当时值）',
    birth_date     DATE        NULL COMMENT '出生日期（算 230 日龄后备）',
    create_time    DATETIME    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照生成时间',
    PRIMARY KEY (tenant_id, snap_date, pig_id),
    KEY idx_pig_snapshot_date (tenant_id, snap_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='猪只每日状态快照（期末存栏指标历史可信源）';
