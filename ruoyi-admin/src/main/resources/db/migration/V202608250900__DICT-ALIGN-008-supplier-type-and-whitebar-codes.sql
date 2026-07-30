-- ============================================================================
-- DICT-ALIGN-008  供应商类型按甲方新体系重建 + 白条产品退回补真实产品码
--
-- 甲方 2026-07-30 在《东角山数据补充清单》里回复并直接改了源表，本迁移跟上：
--
-- 1) djs_supplier_type —— 供应类型**整个体系换掉**。
--    甲方「供应商信息」表实测新分布（55 家）：
--      种植采购类 21 / 包装物类 14 / 养殖采购类 10 / 仓库采购类 7 / 通用类 2 / 种猪类 1
--    旧词表（农产品类/包装物类/肥料类/农药类/猪药类/猪饲料类/种子类 + 种猪 + 其他）作废：
--    甲方从「按物料品类分」改成了「按采购归口分」，两套无法一一映射，整表重建。
--    · 「种猪类」保留 —— PigIntroServiceImpl 有 "breed".equals(supplierType) 硬校验，
--      且甲方这轮补了 1 家种猪供应商，外部引种终于可用。
--    · 「通用类」承接原先供应类型为空的 S0046「网购」，不再需要单独的「其他」兜底项。
--    取号 1029210-1029215（两库全表 SELECT 实查 1029210-1029260 为空）。
--
-- 2) djs_white_bar_return_product —— 「肉末」「废肉废料」换成真实产品码。
--    这两项原先甲方没给产品编码，只能用占位值 rou_mo / fei_liao（匹配不到产品、
--    门店盘点「白条分割产品总重」算不进这两项）。甲方本轮补进原材料清单：
--      Y00143 肉末 / Y00144 废肉废料（均为 猪肉产品）
--    → 按该字典既有约定（label=部位名 / value=产品业务码）改成真码。
--    另：甲方把该组字典从「白条到店分割类型」改名成「白条产品退回类型」，与本字典语义一致，无需改 dict_type。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_supplier_type'
   AND tenant_id = '1001';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1029210, '1001', 0, '种植采购类', 'plant_purchase', 'djs_supplier_type', '', 'success', 'N', NULL, NOW(), 'DICT-ALIGN-008 甲方供应商表实际口径'),
  (1029211, '1001', 1, '养殖采购类', 'breed_purchase', 'djs_supplier_type', '', 'primary', 'N', NULL, NOW(), 'DICT-ALIGN-008'),
  (1029212, '1001', 2, '仓库采购类', 'wh_purchase',    'djs_supplier_type', '', 'warning', 'N', NULL, NOW(), 'DICT-ALIGN-008'),
  (1029213, '1001', 3, '包装物类',   'packaging',      'djs_supplier_type', '', 'info',    'N', NULL, NOW(), 'DICT-ALIGN-008 沿用既有 value'),
  (1029214, '1001', 4, '种猪类',     'breed',          'djs_supplier_type', '', 'danger',  'N', NULL, NOW(), 'DICT-ALIGN-008 沿用既有 value；PigIntroServiceImpl 有 breed 硬校验，不能改'),
  (1029215, '1001', 5, '通用类',     'general',        'djs_supplier_type', '', '',        'N', NULL, NOW(), 'DICT-ALIGN-008 承接原供应类型为空的供应商，兼作兜底');

UPDATE sys_dict_data
   SET dict_value = 'Y00143', update_time = NOW()
 WHERE dict_type = 'djs_white_bar_return_product' AND tenant_id = '1001' AND dict_label = '肉末';

UPDATE sys_dict_data
   SET dict_value = 'Y00144', update_time = NOW()
 WHERE dict_type = 'djs_white_bar_return_product' AND tenant_id = '1001' AND dict_label = '废肉废料';
