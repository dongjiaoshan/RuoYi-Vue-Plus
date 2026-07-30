-- ============================================================================
-- DICT-ALIGN-002  白条产品退回 djs_white_bar_return_product：灌甲方「白条到店分割类型」17 项
--
-- 用途：门店盘点「白条分割产品总重」口径 + 门店退回操作「猪肉产品」tab 的白条产品候选
--   （StoreDailyLedgerServiceImpl / StoreLossServiceImpl 按本字典 value 取产品）。
--   label = 部位中文名（admin 展示），value = 原材料主数据 product_id（Y 码），后端按 value resolve 成雪花主键。
--
-- 取号：1029000-1029016（两库实查为空），sort 0-16 照甲方清单原顺序。
--
-- 幂等写法：整表 DELETE 再 INSERT 17 行。本字典建时就是「空字典客户自配」，
--   staging 上客户已自配 5 项（蹄髈 / 龙骨 / 筒子骨 / 扇子骨 / 后腿肉，雪花 dict_code），
--   value 与本批完全一致，重建即收编、不丢配置；本地是空表，DELETE 空操作。两库同一终点。
--
-- 「肉末」「废肉废料」在甲方原材料清单里没有 Y 码（分割副产物，本该有产品码）：
--   先用占位 value 灌进来保证 17 项齐全，待甲方补码后改 dict_value 即可（label 不动）。
--
-- sys_dict_type 行两库都已存在（dict_id=102608121，DENGBO-R11 建），本文件只补数据项。
--
-- 跑完刷 Redis 字典缓存：bash script/sql/djs/_post-init.sh
-- ============================================================================
SET NAMES utf8mb4;

DELETE FROM sys_dict_data WHERE dict_type = 'djs_white_bar_return_product';

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
  (1029000, '1001',  0, '通排',     'Y00107', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029001, '1001',  1, '后腿肉',   'Y00104', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029002, '1001',  2, '前腿肉',   'Y00103', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029003, '1001',  3, '五花肉',   'Y00101', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029004, '1001',  4, '腰柳',     'Y00100', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), '对应原材料 Y00100 腰柳肉'),
  (1029005, '1001',  5, '蹄髈',     'Y00112', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029006, '1001',  6, '精梅花',   'Y00105', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), '对应原材料 Y00105 梅花肉'),
  (1029007, '1001',  7, '纯瘦肉',   'Y00102', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029008, '1001',  8, '板油',     'Y00114', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029009, '1001',  9, '扇子骨',   'Y00109', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029010, '1001', 10, '筒子骨',   'Y00110', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029011, '1001', 11, '肥肉',     'Y00113', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029012, '1001', 12, '猪脚',     'Y00111', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029013, '1001', 13, '里脊',     'Y00099', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), '对应原材料 Y00099 里脊肉'),
  (1029014, '1001', 14, '龙骨',     'Y00108', 'djs_white_bar_return_product', '', 'primary', 'N', NULL, NOW(), NULL),
  (1029015, '1001', 15, '肉末',     'rou_mo', 'djs_white_bar_return_product', '', 'info',    'N', NULL, NOW(), '占位 value：甲方原材料清单无对应 Y 码，待甲方补产品码后改 dict_value'),
  (1029016, '1001', 16, '废肉废料', 'fei_liao', 'djs_white_bar_return_product', '', 'info',  'N', NULL, NOW(), '占位 value：甲方原材料清单无对应 Y 码，待甲方补产品码后改 dict_value');
