-- D-0147 ③ 称重页另存称重和皮重
--
-- 甲方口径：除实重外，另外单独保存称重和皮重两个数，方便以后追查。
-- 六个称重页（猪只入库 / 白条分割出库 / 果蔬入库 / 果蔬出库 / 白条分割管理 / 白条出库领用）提交时另带
-- gross_weight（称重 = 秤读数）与 tare_weight（去皮重量，不去皮为 0），满足 gross_weight − tare_weight = 实重。
--
-- 落点：
--   t_warehouse_stock_flow     承载该次称重实重的那条出入库流水（不可变审计账，六个页面都写）；
--                              果蔬出库先进先出拆到多篮时，同一次称重的各条切片流水都记这次读数（切片之间没有共同的批号，只能靠创建时间和相同读数识别；对 gross_weight 直接求和会重复计算）。
--   t_warehouse_pig_cut_record 白条出库领用到分割车间：分割单 pickup_weight 是领用称重的唯一记录
--                              （整只兜底领用不写与称重同量的流水），读数同落分割单。
-- 旧客户端 / 非称重写入为 NULL。
-- 生效：纯加列，无字典 / 菜单，部署后无需 flush 缓存。

SET NAMES utf8mb4;

ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN gross_weight DECIMAL(12,3) NULL COMMENT '称重（秤读数 kg）；gross_weight − tare_weight = 该次称重实重；非称重流水为 NULL',
    ADD COLUMN tare_weight  DECIMAL(12,3) NULL COMMENT '去皮重量 kg（不去皮为 0）；与 gross_weight 成对写入';

ALTER TABLE t_warehouse_pig_cut_record
    ADD COLUMN gross_weight DECIMAL(12,3) NULL COMMENT '领用称重的秤读数 kg；gross_weight − tare_weight = pickup_weight；未现场称重为 NULL',
    ADD COLUMN tare_weight  DECIMAL(12,3) NULL COMMENT '领用称重的去皮重量 kg（不去皮为 0）';
