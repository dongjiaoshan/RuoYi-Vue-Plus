-- WMS-DEMAND-001 需求管理：完善 t_warehouse_demand_manage 字段（D7 WMS-MD-001 已建空表，本次补齐业务字段）+ 新建 t_warehouse_demand_pig
-- 字段权威：doc/11-字段权威表.md §2.9 + §2.10
-- 业务流：doc/10-业务流权威图.md §F-WMS-03

-- ============ 主表字段补齐 ============
-- 1) product_type TINYINT → VARCHAR(32)（业态维度：white_bar/vegetable/gift_box/other，字典 djs_demand_product_type）
ALTER TABLE `t_warehouse_demand_manage`
    MODIFY COLUMN `product_type` VARCHAR(32) NOT NULL
        COMMENT '业态：white_bar/vegetable/gift_box/other，字典 djs_demand_product_type';

-- 2) demand_quantity DECIMAL(12,2) → DECIMAL(12,3)（与 product_info / shipped_count 精度统一）
ALTER TABLE `t_warehouse_demand_manage`
    MODIFY COLUMN `demand_quantity` DECIMAL(12, 3) NOT NULL COMMENT '需求量',
    MODIFY COLUMN `shipped_count` DECIMAL(12, 3) NOT NULL DEFAULT 0 COMMENT '累计已发货量（CROSS-FLOW-003 listener 原子 += 更新）',
    MODIFY COLUMN `confirmed_count` DECIMAL(12, 3) NOT NULL DEFAULT 0 COMMENT '累计已确认量';

-- 3) 补 9 个业务字段（doc/11 §2.9 行 687-723）
ALTER TABLE `t_warehouse_demand_manage`
    ADD COLUMN `product_name`         VARCHAR(128) NOT NULL DEFAULT '' COMMENT '产品名（冗余）' AFTER `product_id`,
    ADD COLUMN `product_spec`         VARCHAR(64)  NULL                COMMENT '产品规格' AFTER `product_name`,
    ADD COLUMN `product_unit`         VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '单位' AFTER `demand_quantity`,
    ADD COLUMN `raw_material`         VARCHAR(255) NULL                COMMENT '原材料描述，例"需要 5 头猪"' AFTER `product_unit`,
    ADD COLUMN `material_qty`         DECIMAL(12, 3) NULL              COMMENT '原材料计算量' AFTER `raw_material`,
    ADD COLUMN `demand_remark`        VARCHAR(500) NULL                COMMENT '需求备注' AFTER `material_qty`,
    ADD COLUMN `demand_explain`       VARCHAR(500) NULL                COMMENT '需求说明，例"25 号之前每天 1 头猪送到矿业 / 背膘不要太厚"' AFTER `demand_remark`,
    ADD COLUMN `confirmer_time`       DATETIME     NULL                COMMENT '确认时间' AFTER `demand_confirmer`,
    ADD COLUMN `expected_arrive_date` DATE         NULL                COMMENT '期望到货日' AFTER `confirmer_time`;

-- 4) 索引调整：原 idx_demand_status 单列 → 复合 idx_tenant_status_date；新增 idx_product_type
ALTER TABLE `t_warehouse_demand_manage`
    DROP INDEX `idx_demand_status`,
    ADD INDEX `idx_tenant_status_date` (`tenant_id`, `demand_status`, `demand_date`),
    ADD INDEX `idx_store_status` (`tenant_id`, `store_id`, `demand_status`),
    ADD INDEX `idx_product_type` (`tenant_id`, `product_type`);

-- ============ 需求 ↔ 猪只关联表（白条业态用）============
CREATE TABLE IF NOT EXISTS `t_warehouse_demand_pig`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'snowflake',
    `tenant_id`   VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    `demand_id`   BIGINT       NOT NULL COMMENT 'FK t_warehouse_demand_manage.id',
    `ear_no`      VARCHAR(32)  NOT NULL COMMENT '耳号，关联 t_farm_pig_info.ear_no',
    `assigned_at` DATETIME     NOT NULL COMMENT '指定时间',
    `assigned_by` BIGINT       NOT NULL COMMENT 'FK sys_user.user_id',
    `create_by`   BIGINT       NULL COMMENT '创建人',
    `create_time` DATETIME     NULL COMMENT '创建时间',
    `update_by`   BIGINT       NULL COMMENT '更新人',
    `update_time` DATETIME     NULL COMMENT '更新时间',
    `remark`      VARCHAR(500) NULL COMMENT '备注',
    `del_flag`    CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志',
    `del_unique`  BIGINT       NOT NULL DEFAULT 0 COMMENT '软删唯一性辅助列',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_demand_ear` (`tenant_id`, `demand_id`, `ear_no`, `del_unique`),
    KEY `idx_demand` (`tenant_id`, `demand_id`),
    KEY `idx_ear` (`tenant_id`, `ear_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='需求↔猪只关联（白条业态指定猪只）';
