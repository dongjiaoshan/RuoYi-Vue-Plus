-- ============================================================
-- D08-CLOSING 删除未使用的片区-地块关联表
--
-- PLT-MD-001 决策 A：片区 / 地块走"父子（parent_id）+ zone_id 冗余"建模，
-- 不再使用 t_plant_zone_plotno N:M 关联表。本表在 SYS-INIT-001 baseline 占位
-- 创建，从未读写过，DROP 释放为历史包袱。
--
-- doc/11 § 1.10 保留作历史参考，对应迁移版本号即为本文件。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_plant_zone_plotno;
