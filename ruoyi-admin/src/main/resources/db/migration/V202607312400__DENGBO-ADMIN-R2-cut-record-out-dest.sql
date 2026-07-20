-- ============================================================
-- DENGBO-ADMIN-R2 白条领用表：t_warehouse_pig_cut_record 加出库去向 out_dest
-- ============================================================
-- 邓博 2026-07-06（admin 测试表 row2）：白条领用「确认出库」出库位置=仓库出库(out_type='warehouse')时，
--   须把页面所选【出库去向】码值落到本表 out_dest 列（字典 djs_stock_out_dest：矿山/厨房/大冶门店/个人…）。
--   前端 packEntry/pickup 已把 warehouseOutDest 传给 submitWarehouseOut，仅缺本表列 + 后端写入。
--   out_type='cut'/'ship' 无出库去向概念 → out_dest 保持 NULL。
-- 幂等 guard（INFORMATION_SCHEMA 守卫）：可先手动 apply staging 做 live 验证，Flyway 部署重跑安全。
-- version 202607312400 > 当前 flyway max V202607312300。out_dest 复用已有字典 djs_stock_out_dest，无需新增字典。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

-- cut_record.out_dest 列（幂等）
SET @has_od := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=@db AND TABLE_NAME='t_warehouse_pig_cut_record' AND COLUMN_NAME='out_dest');
SET @sql := IF(@has_od=0,
  "ALTER TABLE t_warehouse_pig_cut_record ADD COLUMN out_dest VARCHAR(50) NULL COMMENT '出库去向码值（字典 djs_stock_out_dest，仅 out_type=warehouse 仓库出库时有值）' AFTER out_type",
  "DO 0");
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
