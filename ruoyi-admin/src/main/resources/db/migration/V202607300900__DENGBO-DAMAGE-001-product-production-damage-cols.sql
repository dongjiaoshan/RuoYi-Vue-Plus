-- DENGBO-DAMAGE-001：到店损耗（损坏）标记 —— 扩 t_warehouse_product_production
-- 门店需求（已发货）「产品明细」逐条标损：损坏凭证多图 + 备注；仓库产品生产看「是否损坏」列 / 损坏量统计。
-- 链路：门店标损（is_damaged=1 + 凭证 + 备注 + 时间）→ 仓库产品生产/需求列表读 is_damaged 聚合。
ALTER TABLE t_warehouse_product_production
    ADD COLUMN is_damaged              TINYINT      NOT NULL DEFAULT 0 COMMENT '是否损坏 djs_yes_no：1=是/0=否（门店标损）',
    ADD COLUMN damage_evidence_oss_ids VARCHAR(500) NULL COMMENT '损坏凭证图 OSS IDs CSV（biz_type=warehouse_damage_evidence）',
    ADD COLUMN damage_remark           VARCHAR(500) NULL COMMENT '损坏备注',
    ADD COLUMN damage_time             DATETIME     NULL COMMENT '标损时间';

ALTER TABLE t_warehouse_product_production
    ADD INDEX idx_pp_damage (tenant_id, is_damaged, del_flag);
