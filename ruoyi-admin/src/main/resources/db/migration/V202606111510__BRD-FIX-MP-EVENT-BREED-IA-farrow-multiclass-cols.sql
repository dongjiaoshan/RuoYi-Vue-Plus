-- BRD-FIX-MP-EVENT-BREED-IA-001：分娩多分类仔猪字段（Kevin 2026-05-29 拍板，采用原型 93 多分类模型）
-- 原型 93「分娩仔猪情况」公/母双值拆分：健仔（公/母）+ 弱仔留养（公/母）+ 弱仔处死 + 畸形。
-- 现有 weak_born / male_count / female_count 保留（下游 BRD-EVENT-003 耳标依赖 live_born，不破坏）。
-- 公共字段 + tenant_id 不变；纯 ADD COLUMN，向后兼容（旧记录新列 NULL DEFAULT 0）。

ALTER TABLE t_farm_pig_farrow
  ADD COLUMN healthy_male       INT NULL DEFAULT 0 COMMENT '健仔公猪数（原型 93 健仔数·公猪数）'         AFTER weak_born,
  ADD COLUMN healthy_female     INT NULL DEFAULT 0 COMMENT '健仔母猪数（原型 93 健仔数·母猪数）'         AFTER healthy_male,
  ADD COLUMN weak_raised_male   INT NULL DEFAULT 0 COMMENT '弱仔留养公猪数（原型 93 弱仔数(饲养)·公猪数）' AFTER healthy_female,
  ADD COLUMN weak_raised_female INT NULL DEFAULT 0 COMMENT '弱仔留养母猪数（原型 93 弱仔数(饲养)·母猪数）' AFTER weak_raised_male,
  ADD COLUMN weak_culled        INT NULL DEFAULT 0 COMMENT '弱仔处死数（原型 93 弱仔数(处死)）'           AFTER weak_raised_female,
  ADD COLUMN deformed_born      INT NULL DEFAULT 0 COMMENT '畸形数（原型 93 畸形数）'                     AFTER weak_culled;
