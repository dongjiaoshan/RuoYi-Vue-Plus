-- ============================================================================
-- V6-R197 / R198  门店需求状态新增「部分到店」PARTIAL_ARRIVED
--
-- 甲方原话（2026-09-07）：
--   「目前开放了部份出车发货的需求，如图所示的产品并未生产，但是出车完成之后，状态也变成了确认到店。
--     新增一种状态：部份到店。到店量等于 0 时，状态还是【已确认】状态。」
-- 缺量出车时需求被 forceCloseUnmetDemands 推到 COMPLETED，门店却一件都没收到，
-- 旧派生口径把它显示成「已发货 / 确认到店」，与事实矛盾。
--
-- 门店视角派生态（字典 djs_store_demand_status）因此从 5 态扩到 6 态：
--   待确认 SUBMITTED / 已确认 CONFIRMED / 部分到店 PARTIAL_ARRIVED /
--   已发货 SHIPPED / 确认到店 ARRIVED / 已删除 DELETED
-- 派生规则的唯一实现在 StoreDemandStatusMapping.derive()（本 SQL 只加字典项，
-- 不动仓库状态机 DemandStatus，也不动 t_warehouse_demand_manage 任何列）。
--
-- 配色：el-tag 只有 primary/success/info/warning/danger 五种 type，6 态必有一处重复。
--   部分到店取 warning 橙（与「待确认」同色）—— 两者在流程上隔了三档，不会相邻出现造成误读；
--   与「已发货 primary 蓝 / 确认到店 success 绿」分得开才是这一列的关键。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

-- 新增字典项（幂等：已存在则不插）
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type,
                           css_class, list_class, is_default, create_by, create_time, remark)
SELECT 9204035, '1001', 2, '部分到店', 'PARTIAL_ARRIVED', 'djs_store_demand_status',
       '', 'warning', 'N', 1, NOW(), '已发货态且未收货，0 < 到店量 < 需求量（V6-R197）'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data
                    WHERE dict_type = 'djs_store_demand_status'
                      AND tenant_id = '1001'
                      AND dict_value = 'PARTIAL_ARRIVED');

-- 排序落到流程顺序：待确认 0 / 已确认 1 / 部分到店 2 / 已发货 3 / 确认到店 4 / 已删除 5
-- （UPDATE 到固定值，重复跑结果一致）
UPDATE sys_dict_data SET dict_sort = 0, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'SUBMITTED';

UPDATE sys_dict_data SET dict_sort = 1, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'CONFIRMED';

UPDATE sys_dict_data SET dict_sort = 2, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'PARTIAL_ARRIVED';

UPDATE sys_dict_data SET dict_sort = 3, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'SHIPPED';

UPDATE sys_dict_data SET dict_sort = 4, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'ARRIVED';

UPDATE sys_dict_data SET dict_sort = 5, update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status' AND tenant_id = '1001' AND dict_value = 'DELETED';

-- 字典说明同步到 6 态（人读，运营在字典管理页看得到）
UPDATE sys_dict_type
   SET remark = '门店视角看自己需求单的进度（待确认/已确认/部分到店/已发货/确认到店/已删除），门店端需求列表显示到哪一步了',
       update_time = NOW()
 WHERE dict_type = 'djs_store_demand_status';
