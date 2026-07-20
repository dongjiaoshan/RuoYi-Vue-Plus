-- D06 HOTFIX: 补 5 张养殖事件表 del_unique 列
--
-- 根因：BRD-EVENT-002-be subagent 写 DDL 时漏 del_unique；
--      其余字段齐，唯独 del_unique 缺，软删 + del_unique=0 默认值的 UNIQUE 约束
--      若未来加 unique index 会撞车。补齐与 djs 业务表全局约定一致。
--
-- 参考：D04 BRD-MED-001 V202605221100__BRD-MED-001-medicine-batch.sql 中的写法
--      del_unique BIGINT NOT NULL DEFAULT 0（应用层 DjsMetaObjectHandler.updateFill
--      在 del_flag='1' 时 SET del_unique=id；MySQL 8 不支持 GENERATED 引用 PK）

ALTER TABLE t_farm_pig_breeding ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token';
ALTER TABLE t_farm_pig_farrow   ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token';
ALTER TABLE t_farm_pig_weaning  ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token';
ALTER TABLE t_farm_pig_heat     ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token';
ALTER TABLE t_farm_pig_abnormal ADD COLUMN del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删 token';
