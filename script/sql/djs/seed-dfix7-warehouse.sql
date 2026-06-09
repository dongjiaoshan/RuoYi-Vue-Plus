-- ============================================================================
-- D-FIX-7/8/9 仓库板块测试种子数据（可重复执行）
--
-- 用途：让 mp 仓库各页脱离 _devMock 假数据、走真实后端 + 写操作可提交。
--   （mockFallback 真实优先：后端返非空就用真实，故只要库里有数据即覆盖 mock）
--
-- ID 段约定：本脚本所有新行 id ∈ [3_000_000_000_000_000_000, 3_099_999_999_999_999_999]
--   重跑前先 DELETE 该段（见模块 0），幂等。交付前清理 = 跑模块 0 即可。
--
-- 复用现有主数据（不新建）：
--   作物 大白菜=2059550442693541890  地块 P001=2059550021501534210  片区 zone=2059547745324056577
--   库位 蔬菜鲜品库=100000000000930002 冷冻库=100000000000930001 包材库=100000000000930003
--        冷冻一号库=2059244331633524737
--   门店 演示徐汇=100000000000910001 演示浦东=100000000000910002
--   员工 赵仓库=9104 吴仓库=9108
--   产品 白条整只=100000000000000001 半只=100000000000000004 部位肉=100000000000000102
--        猪皮=100000000000000104 骨类=100000000000000103 蔬菜P0003=2059526196453937153
--        塑料袋=2059526196453937156 纸箱=2059526196453937157 标签=2059526196453937158 捆扎绳=2059526196453937159
-- ============================================================================

-- ────────────────────────── 模块 0：清理（可重复） ──────────────────────────
SET @lo = 3000000000000000000;
SET @hi = 3099999999999999999;
DELETE FROM t_warehouse_stock_flow        WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_check_record       WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_veg_receive        WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_product_production  WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_demand_manage       WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_location_stock      WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_product_info        WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_handle_record       WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_vegetable_handle    WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_warehouse_planting_record     WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_plant_plot_info               WHERE id BETWEEN @lo AND @hi;
DELETE FROM t_plant_crop_info               WHERE id BETWEEN @lo AND @hi;

-- ────────────────────── 模块 1：种植主数据（贴原型作物 + 地块） ──────────────────────
-- 作物：小白菜 / 番茄 / 花菜（贴 mp 原型菜品）
INSERT INTO t_plant_crop_info (id, tenant_id, crop_code, crop_name, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3001000000000000001, '1001', 'C-DFIX-01', '小白菜', 100, 1, NOW(), '0', 0),
 (3001000000000000002, '1001', 'C-DFIX-02', '番茄',   100, 1, NOW(), '0', 0),
 (3001000000000000003, '1001', 'C-DFIX-03', '花菜',   100, 1, NOW(), '0', 0);

-- 地块：A-D-001 / A-D-002 / A-D-003（zone 复用现有；plot_name/area 给合理值）
INSERT INTO t_plant_plot_info (id, tenant_id, plot_code, zone_id, plot_name, plot_area, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3002000000000000001, '1001', 'A-D-001', 2059547745324056577, 'A区1号地', 1.21, 100, 1, NOW(), '0', 0),
 (3002000000000000002, '1001', 'A-D-002', 2059547745324056577, 'A区2号地', 0.86, 100, 1, NOW(), '0', 0),
 (3002000000000000003, '1001', 'A-D-003', 2059547745324056577, 'A区3号地', 1.05, 100, 1, NOW(), '0', 0);

-- ──────────────── 模块 2：蔬菜链（毛菜处理 + 果蔬月台，精确对照原型） ────────────────
-- planting_record（地块批次）：小白菜×3地块 + 番茄×A-D-001 + 花菜×A-D-002
INSERT INTO t_warehouse_planting_record
 (id, tenant_id, plot_id, crop_id, plot_name, crop_name, harvest_date, harvest_weight, data_date, handle_status, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3003000000000000001, '1001', 3002000000000000001, 3001000000000000001, 'A-D-001', '小白菜', CURDATE(), 32.50, NOW(), 'processing', 100, 1, NOW(), '0', 0),
 (3003000000000000002, '1001', 3002000000000000002, 3001000000000000001, 'A-D-002', '小白菜', CURDATE(), 24.20, NOW(), 'processing', 100, 1, NOW(), '0', 0),
 (3003000000000000003, '1001', 3002000000000000003, 3001000000000000001, 'A-D-003', '小白菜', CURDATE(), 29.80, NOW(), 'done',       100, 1, NOW(), '0', 0),
 (3003000000000000004, '1001', 3002000000000000001, 3001000000000000002, 'A-D-001', '番茄',   CURDATE(), 41.60, NOW(), 'pending',    100, 1, NOW(), '0', 0),
 (3003000000000000005, '1001', 3002000000000000002, 3001000000000000003, 'A-D-002', '花菜',   CURDATE(), 18.70, NOW(), 'processing', 100, 1, NOW(), '0', 0);

-- vegetable_handle（毛菜处理汇总，按 planting_record 维度）
-- 小白菜三地块 send_platform_weight = 12.5 / 12.91 / 6.3（对照原型：A-D-001待入12.5 / A-D-002待入8.71 / A-D-003待入0）
--   头卡合计待入 = 12.5 + (12.91-4.2) + (6.3-6.3) = 21.21（= 原型小白菜头卡 21.21kg）
INSERT INTO t_warehouse_vegetable_handle
 (id, tenant_id, planting_record_id, plot_id, crop_id, pick_start_time, pick_end_time, picked_weight, handled_weight, feed_weight, send_platform_weight, stock_in_weight, loss_weight, is_weighed, is_finish, handle_status, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3004000000000000001, '1001', 3003000000000000001, 3002000000000000001, 3001000000000000001, NOW(), NULL,  32.50, 12.50, 2.00, 12.50, 0.00,  1.50, 1, 2, 'processing', 100, 1, NOW(), '0', 0),
 (3004000000000000002, '1001', 3003000000000000002, 3002000000000000002, 3001000000000000001, NOW(), NULL,  24.20, 12.91, 1.50, 12.91, 4.20,  0.79, 1, 2, 'processing', 100, 1, NOW(), '0', 0),
 (3004000000000000003, '1001', 3003000000000000003, 3002000000000000003, 3001000000000000001, NOW(), NOW(), 29.80,  6.30, 1.20,  6.30, 6.30,  1.30, 1, 1, 'done',       100, 1, NOW(), '0', 0),
 (3004000000000000004, '1001', 3003000000000000005, 3002000000000000002, 3001000000000000003, NOW(), NULL,  18.70,  5.00, 0.50,  5.00, 0.00,  0.10, 1, 2, 'processing', 100, 1, NOW(), '0', 0);
-- 番茄 A-D-001（pending：仅采摘未处理，无月台量，毛菜处理列表显示「待称重」）
INSERT INTO t_warehouse_vegetable_handle
 (id, tenant_id, planting_record_id, plot_id, crop_id, pick_start_time, picked_weight, handled_weight, feed_weight, send_platform_weight, stock_in_weight, loss_weight, is_weighed, is_finish, handle_status, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3004000000000000005, '1001', 3003000000000000004, 3002000000000000001, 3001000000000000002, NOW(), 41.60, 0.00, 0.00, 0.00, 0.00, 0.00, 2, 2, 'pending', 100, 1, NOW(), '0', 0);

-- veg_receive（已入库自产记录 receive_type=1）：让小白菜 A-D-002 入库中(已入4.2)、A-D-003 已入库(已入6.3)
INSERT INTO t_warehouse_veg_receive
 (id, tenant_id, receive_no, receive_type, crop_id, crop_name, plot_id, weight, location_id, is_finish, receive_status, operator_id, receive_time, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3009000000000000001, '1001', 'VR-DFIX-0001', 1, 3001000000000000001, '小白菜', 3002000000000000002, 4.20, 100000000000930002, 2, 'processing', 9108, NOW(), 100, 9108, NOW(), '0', 0),
 (3009000000000000002, '1001', 'VR-DFIX-0002', 1, 3001000000000000001, '小白菜', 3002000000000000003, 6.30, 100000000000930002, 1, 'done',       9108, NOW(), 100, 9108, NOW(), '0', 0);

-- ─────────────────── 模块 3：egg 产品 + 库存（物资领用鸡蛋 tab 原本空） ───────────────────
INSERT INTO t_warehouse_product_info
 (id, tenant_id, product_id, product_name, product_type, belong_type, product_status, product_unit, product_spec, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3005000000000000001, '1001', 'PROD-EGG-TU-01', '土鸡蛋', 1, 'egg', 0, '枚', '约50g/枚', 100, 1, NOW(), '0', 0),
 (3005000000000000002, '1001', 'PROD-EGG-WU-01', '乌鸡蛋', 1, 'egg', 0, '枚', '约45g/枚', 100, 1, NOW(), '0', 0);

-- ──────────────── 模块 4：库存均衡（mat 各业态 chip + 卡饱满；product_id 维度，is_end=0） ────────────────
INSERT INTO t_warehouse_location_stock
 (id, tenant_id, location_id, product_id, product_name, product_stock, product_unit, is_end, operator_id, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 -- egg（鸡蛋鲜品库 → 用蔬菜鲜品库承载）
 (3006000000000000001, '1001', 100000000000930002, 3005000000000000001, '土鸡蛋', 480.000, '枚', 0, 9108, 100, 1, NOW(), '0', 0),
 (3006000000000000002, '1001', 100000000000930002, 3005000000000000002, '乌鸡蛋', 260.000, '枚', 0, 9108, 100, 1, NOW(), '0', 0),
 -- package（演示包材库补 4 种，让包材 tab + chip 饱满）
 (3006000000000000003, '1001', 100000000000930003, 2059526196453937156, '塑料袋', 1500.000, '个', 0, 9108, 100, 1, NOW(), '0', 0),
 (3006000000000000004, '1001', 100000000000930003, 2059526196453937157, '纸箱',    620.000, '个', 0, 9108, 100, 1, NOW(), '0', 0),
 (3006000000000000005, '1001', 100000000000930003, 2059526196453937158, '标签贴纸', 3000.000, '张', 0, 9108, 100, 1, NOW(), '0', 0),
 (3006000000000000006, '1001', 100000000000930003, 2059526196453937159, '捆扎绳',   880.000, '米', 0, 9108, 100, 1, NOW(), '0', 0),
 -- pork（冷冻库补部位肉/猪皮，让猪肉 tab 多产品有量）
 (3006000000000000007, '1001', 100000000000930001, 100000000000000102, '猪肉·部位肉', 64.500, 'kg', 0, 9108, 100, 1, NOW(), '0', 0),
 (3006000000000000008, '1001', 100000000000930001, 100000000000000104, '猪肉·猪皮',   28.000, 'kg', 0, 9108, 100, 1, NOW(), '0', 0),
 -- vegetable（蔬菜鲜品库补蔬菜 P0003）
 (3006000000000000009, '1001', 100000000000930002, 2059526196453937153, '蔬菜',       96.300, 'kg', 0, 9108, 100, 1, NOW(), '0', 0);

-- ──────────────── 模块 5：今日 demand + production（home/stats/dashboard 今日口径 + 发货月台） ────────────────
-- demand（今日，覆盖 SUBMITTED 待确认 / CONFIRMED 可发货）
INSERT INTO t_warehouse_demand_manage
 (id, tenant_id, demand_no, demand_date, store_id, product_id, product_name, product_type, product_unit, demand_quantity, demand_status, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3007000000000000001, '1001', 'DMD-DFIX-WB01', CURDATE(), 100000000000910001, 100000000000000001, '白条·整只', 'white_bar', '头', 3.000,  'SUBMITTED',  100, 1, NOW(), '0', 0),
 (3007000000000000002, '1001', 'DMD-DFIX-VG01', CURDATE(), 100000000000910002, 2059526196453937153, '蔬菜',     'vegetable', 'kg', 30.000, 'SUBMITTED',  100, 1, NOW(), '0', 0),
 (3007000000000000003, '1001', 'DMD-DFIX-WB02', CURDATE(), 100000000000910001, 100000000000000001, '白条·整只', 'white_bar', '头', 2.000,  'CONFIRMED',  100, 1, NOW(), '0', 0),
 (3007000000000000004, '1001', 'DMD-DFIX-VG02', CURDATE(), 100000000000910002, 2059526196453937153, '蔬菜',     'vegetable', 'kg', 20.000, 'CONFIRMED',  100, 1, NOW(), '0', 0);

-- production（今日可发：demand_id NULL + is_delivery_check=0；belong_type 与 demand 业态匹配）
INSERT INTO t_warehouse_product_production
 (id, tenant_id, produce_no, produce_date, produce_time, product_id, product_name, product_type, product_unit, product_weight, produce_quantity, store_id, demand_id, is_delivery_check, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3008000000000000001, '1001', 'B-DFIX-0001', CURDATE(), NOW(), 100000000000000001, '白条·整只', 1, '头', 80.500, 1.000, 100000000000910001, NULL, 0, 100, 1, NOW(), '0', 0),
 (3008000000000000002, '1001', 'B-DFIX-0002', CURDATE(), NOW(), 100000000000000004, '白条·半只', 1, '头', 41.200, 1.000, 100000000000910001, NULL, 0, 100, 1, NOW(), '0', 0),
 (3008000000000000003, '1001', 'V-DFIX-0001', CURDATE(), NOW(), 2059526196453937153, '蔬菜',     1, 'kg', 30.000, 30.000, 100000000000910002, NULL, 0, 100, 1, NOW(), '0', 0),
 (3008000000000000004, '1001', 'V-DFIX-0002', CURDATE(), NOW(), 2059526196453937153, '蔬菜',     1, 'kg', 20.000, 20.000, NULL,                NULL, 0, 100, 1, NOW(), '0', 0);

-- ──────────────── 模块 6：盘点明细 + 今日流水（dashboard 盘点 KPI + 自助盘点记录 + mat 今日额度） ────────────────
-- check_record 明细行（is_header=0, completed）：dashboard 盘点 KPI（最近盘点日 正常/异常/计损）
INSERT INTO t_warehouse_check_record
 (id, tenant_id, check_id, is_header, location_id, product_id, check_date, check_status, check_result_type, sys_stock, check_stock, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3010000000000000001, '1001', 'CK-DFIX-0001', 0, 100000000000930003, 2059526196453937156, NOW(), 'completed', 1, 1500.000, 1500.000, 100, 1, NOW(), '0', 0),
 (3010000000000000002, '1001', 'CK-DFIX-0001', 0, 100000000000930002, 100000000000931004, NOW(), 'completed', 3, 120.000, 116.500, 100, 1, NOW(), '0', 0),
 (3010000000000000003, '1001', 'CK-DFIX-0001', 0, 100000000000930001, 100000000000000102, NOW(), 'completed', 2, 64.500, 60.000, 100, 1, NOW(), '0', 0);

-- stock_flow：今日 pick_out（operator 9108）→ mat myToday 有内容 + return/loss 今日额度；
--   + 几条 check_in/check_out（自助盘点 checkRecords 历史）+ 各业态进出库流水（inoutFlows 有数）
INSERT INTO t_warehouse_stock_flow
 (id, tenant_id, flow_no, flow_date, product_id, warehouse_id, inout_type, flow_type, change_num, change_quantity, stock_out_dest, operator_id, create_dept, create_by, create_time, del_flag, del_unique) VALUES
 (3011000000000000001, '1001', 'OT-DFIX-0001', NOW(), 2059526196453937156, 100000000000930003, 'OT', 'pick_out',   -20.00, 20.00, 'dept',    9108, 100, 9108, NOW(), '0', 0),
 (3011000000000000002, '1001', 'OT-DFIX-0002', NOW(), 100000000000000102,  100000000000930001, 'OT', 'pick_out',   -5.00,  5.00,  'kitchen', 9108, 100, 9108, NOW(), '0', 0),
 (3011000000000000003, '1001', 'IN-DFIX-0001', NOW(), 2059526196453937157, 100000000000930003, 'IN', 'purchase_in', 620.00, 620.00, NULL,     9108, 100, 9108, NOW(), '0', 0),
 (3011000000000000004, '1001', 'IN-DFIX-0002', NOW(), 100000000000931004,  100000000000930002, 'IN', 'check_in',    0.00,   116.50, NULL,     9104, 100, 9104, NOW(), '0', 0),
 (3011000000000000005, '1001', 'OT-DFIX-0003', NOW(), 100000000000000102,  100000000000930001, 'OT', 'check_out',  -4.50,   60.00,  NULL,     9104, 100, 9104, NOW(), '0', 0);

-- ============================================================================
-- 完。验证：
--   果蔬月台自产 → 应见 小白菜(待入21.21)/番茄/花菜；点小白菜 → A-D-001待入12.5 / A-D-002入库中 / A-D-003已入库
--   物资领用 → 鸡蛋 tab 有土鸡蛋/乌鸡蛋；包材/猪肉/蔬菜 tab 均有库存卡
--   发货月台 → 演示徐汇/浦东有今日待发；仓库 dashboard → 今日需求/生产/盘点有数
-- ============================================================================
