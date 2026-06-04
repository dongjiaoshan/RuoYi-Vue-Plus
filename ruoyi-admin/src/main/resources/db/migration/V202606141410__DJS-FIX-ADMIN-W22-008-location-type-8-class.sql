-- ============================================================
-- DJS-FIX-ADMIN-W22-008 库位类型字典 6 → 8 类迁移 + 库位历史数据迁移
--
-- 客户实际库位 8 类（替换 D7 业内推断的 6 类）：
--   white_bar 白条 / frozen 冻品 / veg_fresh 蔬菜鲜品 / veg_heavy 重口蔬菜 /
--   packaging 包材 / medicine 药品 / dry_goods 干货 / raw_material 原材料
--
-- 策略：旧 6 行 UPDATE 改 value/label/sort（保留 dict_code 引用语义），
--       新增 white_bar / veg_heavy / packaging 3 行；删除无对应的 feed 行；
--       同步迁移 t_warehouse_location_info.location_type 旧值。
-- 跑完必 flush Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================

-- ------------------------------------------------------------
-- Part 1：字典 6 → 8（UPDATE 旧行 + INSERT 新行 + DELETE 废弃行）
-- ------------------------------------------------------------

-- frozen（1020000）重合：UPDATE label/sort（冻品，sort=1）
UPDATE sys_dict_data
SET dict_sort = 1, dict_label = '冻品', list_class = 'primary'
WHERE dict_type = 'djs_location_type' AND dict_value = 'frozen';

-- fresh（1020001）→ veg_fresh 蔬菜鲜品（sort=2）
UPDATE sys_dict_data
SET dict_value = 'veg_fresh', dict_sort = 2, dict_label = '蔬菜鲜品', list_class = 'success'
WHERE dict_type = 'djs_location_type' AND dict_value = 'fresh';

-- dry（1020002）→ dry_goods 干货（sort=6）
UPDATE sys_dict_data
SET dict_value = 'dry_goods', dict_sort = 6, dict_label = '干货', list_class = 'warning'
WHERE dict_type = 'djs_location_type' AND dict_value = 'dry';

-- ambient（1020003）→ raw_material 原材料（sort=7）
UPDATE sys_dict_data
SET dict_value = 'raw_material', dict_sort = 7, dict_label = '原材料', list_class = 'info'
WHERE dict_type = 'djs_location_type' AND dict_value = 'ambient';

-- medicine（1020004）重合：UPDATE label/sort（药品，sort=5）
UPDATE sys_dict_data
SET dict_sort = 5, dict_label = '药品', list_class = 'danger'
WHERE dict_type = 'djs_location_type' AND dict_value = 'medicine';

-- feed（1020005）废弃：客户 8 类无饲料库（历史库位 feed 数据已映射 raw_material，见 Part 2）
DELETE FROM sys_dict_data
WHERE dict_type = 'djs_location_type' AND dict_value = 'feed';

-- 新增 white_bar 白条（sort=0）/ veg_heavy 重口蔬菜（sort=3）/ packaging 包材（sort=4）
INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1020006, '1001', 0, '白条',     'white_bar', 'djs_location_type', '', 'danger',  'N', 103, 1, NOW()),
    (1020007, '1001', 3, '重口蔬菜', 'veg_heavy', 'djs_location_type', '', 'success', 'N', 103, 1, NOW()),
    (1020008, '1001', 4, '包材',     'packaging', 'djs_location_type', '', 'info',    'N', 103, 1, NOW());

-- ------------------------------------------------------------
-- Part 2：库位历史数据迁移（t_warehouse_location_info.location_type 旧值 → 新值）
-- frozen / medicine value 重合不变；feed 若有数据归 raw_material（AI 推荐 a，待 Kevin closing 确认）
-- ------------------------------------------------------------
UPDATE t_warehouse_location_info SET location_type = 'veg_fresh'    WHERE location_type = 'fresh'   AND del_flag = '0';
UPDATE t_warehouse_location_info SET location_type = 'dry_goods'    WHERE location_type = 'dry'     AND del_flag = '0';
UPDATE t_warehouse_location_info SET location_type = 'raw_material' WHERE location_type = 'ambient' AND del_flag = '0';
UPDATE t_warehouse_location_info SET location_type = 'raw_material' WHERE location_type = 'feed'    AND del_flag = '0';
