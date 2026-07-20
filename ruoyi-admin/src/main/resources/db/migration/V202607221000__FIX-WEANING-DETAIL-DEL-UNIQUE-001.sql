-- FIX-WEANING-DETAIL-DEL-UNIQUE-001：断奶逐头明细表补 del_unique 对齐全项目软删约定（审计 A-2）
--
-- 根因：t_farm_pig_weaning_detail 建表时 UNIQUE KEY uk_weaning_seq 未含 del_unique，
--       软删（del_flag='1'）后同 (tenant_id, weaning_id, piglet_seq) 无法再次录入会撞唯一键。
--       与 djs 业务表全局约定一致：del_unique BIGINT NOT NULL DEFAULT 0，
--       应用层 DjsMetaObjectHandler.updateFill 在 del_flag='1' 时 SET del_unique=id；
--       唯一键末位带上 del_unique，活动行恒为 0 不影响唯一性，软删行用 id 错开。

ALTER TABLE t_farm_pig_weaning_detail
    ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token（del_flag=1 时 SET=id）' AFTER del_flag;

ALTER TABLE t_farm_pig_weaning_detail
    DROP INDEX uk_weaning_seq;

ALTER TABLE t_farm_pig_weaning_detail
    ADD UNIQUE KEY uk_weaning_seq (tenant_id, weaning_id, piglet_seq, del_unique);
