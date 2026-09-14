-- ============================================================================
-- V6-R214  退回产品清单 djs_return_product_list
--
-- 用途：门店退回操作三个 tab（猪肉 / 果蔬 / 其他产品）的候选产品唯一来源。
--   label = 产品中文名（admin 字典管理里给人看），value = 产品主数据 product_id（业务码），
--   后端 StoreReturnServiceImpl 按 value resolve 成雪花主键，再按产品自身 belong_type 分到三个 tab：
--   pork/white_bar → 猪肉产品；vegetable → 果蔬产品；其余（含 belong_type 为空的外购品）→ 其他产品。
--
-- 候选不再来自门店当日盘点台账，因此清单内产品的退回量不封顶；清单外产品仍然一律拒绝退回
--   （这是唯一保留的闸：没配进清单的东西退不了，防止凭空给仓库造库存）。
--
-- 初始 17 项 = 白条到店分割部位（与 djs_white_bar_return_product 同一份 Y 码），
--   让猪肉 tab 开箱即有内容；果蔬 / 其他产品由客户在 admin「字典管理 → 退回产品清单」自行增配。
--
-- 取号：dict_id 102609130、dict_code 1090000-1090016（两库实查为空）。
--
-- 🔴 幂等写法：**只认自己的 17 个 dict_code 做 upsert，绝不整表 DELETE**。
--   这本字典从建起就是「客户自助增配」的（果蔬 / 其他产品由甲方在 admin 字典管理自己加），
--   他加的行拿的是雪花 dict_code、与这 17 个固定号不冲突。写成 `DELETE FROM ... WHERE dict_type=...`
--   再 INSERT 的话，部署那一刻会把甲方在部署前配的所有行全部抹掉 —— 真数据丢失，不是理论风险。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

INSERT INTO sys_dict_type
  (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
  (102609130, '1001', '退回产品清单', 'djs_return_product_list', NULL, NOW(),
   '门店退回操作三个 tab 的候选产品清单；value 填产品编码，按产品归属类型自动分 tab')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1090000, '1001',  0, '通排',     'Y00107', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090001, '1001',  1, '后腿肉',   'Y00104', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090002, '1001',  2, '前腿肉',   'Y00103', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090003, '1001',  3, '五花肉',   'Y00101', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090004, '1001',  4, '腰柳',     'Y00100', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090005, '1001',  5, '蹄髈',     'Y00112', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090006, '1001',  6, '精梅花',   'Y00105', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090007, '1001',  7, '纯瘦肉',   'Y00102', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090008, '1001',  8, '板油',     'Y00114', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090009, '1001',  9, '扇子骨',   'Y00109', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090010, '1001', 10, '筒子骨',   'Y00110', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090011, '1001', 11, '肥肉',     'Y00113', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090012, '1001', 12, '猪脚',     'Y00111', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090013, '1001', 13, '里脊',     'Y00099', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090014, '1001', 14, '龙骨',     'Y00108', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090015, '1001', 15, '肉末',     'Y00143', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL),
  (1090016, '1001', 16, '废肉废料', 'Y00144', 'djs_return_product_list', '', 'primary', 'N', NULL, NOW(), NULL)
ON DUPLICATE KEY UPDATE
  dict_sort = VALUES(dict_sort), dict_label = VALUES(dict_label), dict_value = VALUES(dict_value);
