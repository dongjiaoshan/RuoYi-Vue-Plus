-- ============================================================================
-- DICT-ALIGN-007  供应商类型 djs_supplier_type 按甲方供应商表重建
--
-- 词表来源改为甲方「供应商信息」sheet 的「供应类型」列实际取值（54 家实查）：
--   包装物类 14 / 种子类 12 / 农产品类 8 / 肥料类 6 / 猪药类 5 / 猪饲料类 5 / 农药类 3 / 空 1
-- 原 5 项（饲料原材料 / 种猪 / 药品 / 种子 / 其他）来自甲方「字典项确认清单」，
-- 与供应商表对不上：54 家里 29 家（54%）落不进任何粗类、只能堆「其他」。
--
-- 终表 9 项 = 甲方 7 类 + 种猪 breed + 兜底「其他」：
--   其他 other 保留，三条理由——
--     1. t_md_supplier.supplier_type 是 NOT NULL，甲方 S0046「网购」供应类型为空，
--        生成器兜底取「其他」；没有该项则落 NULL，INSERT 直接失败。
--     2. admin SupplierForm 的 supplierType 是 required，新建供应商遇到 7 类之外的必须有兜底项。
--     3. other 是既有 value，保留不产生孤儿。
--   种猪 breed 保留：mp 养殖→引种登记→外部引种 tab 的供应商字段写死按 supplier-type='breed'
--     过滤（miniapp/src/pages/breed/event/intro/index.vue）。甲方 54 家里没有种猪供应商，
--     该 picker 目前必然为空 —— 这是甲方数据缺口，不是字典问题。保留该项让客户能在 admin
--     把某家供应商标成种猪自助补上；删掉则只能改代码。
--
-- value 取舍（业务表存 value，label 可随时改）：
--   feed / med / seed / other 四个既有 value 复用（各自承接同语义类目，只换 label），
--   packaging / agri_product / fertilizer / pesticide 四个新增；breed 沿用既有 value。
--   ⚠️ breed 必须保留：除 mp picker 外，PigIntroServiceImpl 还有
--      "breed".equals(sup.getSupplierType()) 的硬校验，删掉该 value 会让外部引种彻底不可用。
--
-- 取号：1029200-1029208（本地 + staging 全表 SELECT 实查 1029134-1029999 为空）。
--      dict_code 在 sys_dict_data 只是主键，无任何业务表拿它当外键（代码一律按
--      (dict_type, dict_value) 取值），故可整表删后按 canonical 低位号重建，两库收敛一致。
--
-- 幂等：整表 DELETE + 定值 INSERT，重跑结果相同。
--
-- 跑完刷 Redis 字典缓存：本地 bash script/sql/djs/_post-init.sh
--                       staging bash ops/redis-flush-dict.sh staging
-- ============================================================================
SET NAMES utf8mb4;

DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_supplier_type'
   AND tenant_id = '1001';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1029200, '1001', 0, '农产品类', 'agri_product', 'djs_supplier_type', '', 'success', 'N', NULL, NOW()),
  (1029201, '1001', 1, '包装物类', 'packaging',    'djs_supplier_type', '', 'info',    'N', NULL, NOW()),
  (1029202, '1001', 2, '肥料类',   'fertilizer',   'djs_supplier_type', '', 'success', 'N', NULL, NOW()),
  (1029203, '1001', 3, '农药类',   'pesticide',    'djs_supplier_type', '', 'warning', 'N', NULL, NOW()),
  (1029204, '1001', 4, '猪药类',   'med',          'djs_supplier_type', '', 'warning', 'N', NULL, NOW()),
  (1029205, '1001', 5, '猪饲料类', 'feed',         'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1029206, '1001', 6, '种子类',   'seed',         'djs_supplier_type', '', 'primary', 'N', NULL, NOW()),
  (1029207, '1001', 7, '种猪',     'breed',        'djs_supplier_type', '', 'danger',  'N', NULL, NOW()),
  (1029208, '1001', 8, '其他',     'other',        'djs_supplier_type', '', '',        'N', NULL, NOW());
