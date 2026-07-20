-- ============================================================
-- FIX-PLT-AD-INFO-LIST-001 果蔬品种有机认证「一证多作物」多对多
--
-- 1. t_plant_crop_organic_rel 证书-作物关联表（多对多，与 t_plant_organic_plotno 同范式）
-- 2. t_plant_crop_organic.crop_id 放开 NOT NULL（一证多作物后单值 crop_id 仅过渡兼容，可空）
-- 3. seed：把现存单值 crop_id 迁入关联表（历史数据不丢关联）
--
-- 本卡不改菜单 / 不改字典（纯列/筛选/行操作对齐 + 多对多关联表）。
-- 关联读写复用现有 cropOrganic:add / edit 权限，无新增 sys_menu。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. t_plant_crop_organic_rel 证书-作物关联表
--    多对多：一张果蔬证书可关联多个作物；一个作物也可被多张证书覆盖
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_crop_organic_rel;
CREATE TABLE t_plant_crop_organic_rel (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    organic_id      BIGINT       NOT NULL                COMMENT 'FK → t_plant_crop_organic.id',
    crop_id         BIGINT       NOT NULL                COMMENT 'FK → t_plant_crop_info.id',
    create_dept     BIGINT       NULL                    COMMENT '创建部门',
    create_by       BIGINT       NULL                    COMMENT '创建人',
    create_time     DATETIME     NULL                    COMMENT '创建时间',
    update_by       BIGINT       NULL                    COMMENT '更新人',
    update_time     DATETIME     NULL                    COMMENT '更新时间',
    del_flag        CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志',
    del_unique      BIGINT       NOT NULL DEFAULT 0      COMMENT "软删 token（编辑 + 软删旧关联时 SET del_unique=id）",
    PRIMARY KEY (id),
    UNIQUE KEY uk_organic_crop (tenant_id, organic_id, crop_id, del_unique),
    KEY idx_organic (tenant_id, organic_id),
    KEY idx_crop (tenant_id, crop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 果蔬证书-作物关联表（FIX-PLT-AD-INFO-LIST-001 一证多作物）';


-- ------------------------------------------------------------
-- 2. t_plant_crop_organic.crop_id 放开 NOT NULL
--    一证多作物后，关联走 rel 表，主表单值 crop_id 仅过渡兼容（可空）
-- ------------------------------------------------------------
ALTER TABLE t_plant_crop_organic
    MODIFY COLUMN crop_id BIGINT NULL COMMENT 'FK → t_plant_crop_info.id（旧单值，过渡兼容，关联以 rel 表为准）';


-- ------------------------------------------------------------
-- 3. seed：现存单值 crop_id 迁入关联表（历史数据保留关联）
--    只迁有效证书 + 有效作物 + 非空 crop_id；del_unique=0 占活槽
-- ------------------------------------------------------------
INSERT INTO t_plant_crop_organic_rel
    (tenant_id, organic_id, crop_id, create_by, create_time, del_flag, del_unique)
SELECT
    o.tenant_id, o.id, o.crop_id, o.create_by, NOW(), '0', 0
  FROM t_plant_crop_organic o
  JOIN t_plant_crop_info c
    ON c.id = o.crop_id
   AND c.del_flag = '0'
 WHERE o.del_flag = '0'
   AND o.crop_id IS NOT NULL;
