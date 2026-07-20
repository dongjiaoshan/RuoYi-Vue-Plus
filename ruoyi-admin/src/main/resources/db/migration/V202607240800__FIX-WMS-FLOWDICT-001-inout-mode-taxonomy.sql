-- ============================================================
-- FIX-WMS-FLOWDICT-001 出入库方式 + 出库去向字典规范化（测试 row35/36/37）
--
-- 1. djs_flow_type relabel：把现有键的 label 对齐业务 taxonomy（键不变，仅 UPDATE label）
-- 2. djs_flow_type 新增 7 键：退回/领用按来源拆分 + 盘点异常出库
-- 3. djs_stock_out_dest 新增 5 值：出库去向业务 case（各出库路径自动回填，前端只读）
--
-- 说明：
--   - flow_type 现有 dict_code 已用到 102450019（backstage_out），新增续 102450020+
--   - stock_out_dest 现有 dict_code 已用到 1006305（dept），新增续 1006306+
--   - relabel 用 dict_type+dict_value 精确定位（不用 dict_code，避免 INSERT IGNORE 共用号段误伤）
--   - 改后须 flush *:sys_dict redis 缓存（script/sql/djs/_post-init.sh）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. djs_flow_type relabel（键不变，label 对齐 9 入 + 7 出 taxonomy）
-- ------------------------------------------------------------
-- 入库方式：毛菜间入库
UPDATE sys_dict_data SET dict_label = '毛菜间入库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'veg_stock_in';
-- 入库方式：燎毛间入库（slaughter_burn 的 IN 侧；同键的 OUT 文案历史为「燎毛出库」，此处统一为入库视角）
UPDATE sys_dict_data SET dict_label = '燎毛间入库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'slaughter_burn';
-- 入库方式：分割间入库
UPDATE sys_dict_data SET dict_label = '分割间入库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'cut_out_in';
-- 出库方式：白条出库（原「分割出库」）
UPDATE sys_dict_data SET dict_label = '白条出库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'cut_out';
-- 出库方式：盘点计损出库（原「盘亏出库」）
UPDATE sys_dict_data SET dict_label = '盘点计损出库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'check_out';
-- 出库方式：领用出库（pick_out 作为无法区分来源时的中性兜底 label）
UPDATE sys_dict_data SET dict_label = '领用出库'
 WHERE dict_type = 'djs_flow_type' AND dict_value = 'pick_out';
-- 入库方式：veg_receive_in（果蔬月台入库）/ veg_purchase_in（外购产品月台入库）label 已对齐，不改。

-- ------------------------------------------------------------
-- 2. djs_flow_type 新增 7 键（退回 / 领用按来源拆 + 盘点异常出库）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    -- 入库方式拆 3 类退回
    (102450020, '1001', 20, '门店退回',     'store_return_in',   'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 门店退货入库（ReturnProductServiceImpl）'),
    (102450021, '1001', 21, '生产退回',     'prod_return_in',    'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 仓库生产退回（暂无写入点，待 BO 补来源字段）'),
    (102450022, '1001', 22, '领用退回',     'pick_return_in',    'djs_flow_type', '', 'success', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 物资领用退回（MatFlowServiceImpl.returnBack）'),
    -- 出库方式拆部门 / 生产 / 饲料 + 盘点异常
    (102450023, '1001', 23, '盘点异常出库', 'check_abnormal_out','djs_flow_type', '', 'danger',  'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 盘亏且 check_result=异常(2)'),
    (102450024, '1001', 24, '部门领用',     'dept_pick_out',     'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 养殖/种植物资领用（暂无来源区分，待 BO 补字段）'),
    (102450025, '1001', 25, '生产领用',     'prod_pick_out',     'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 仓库生产消耗（礼盒打包组件 pack_consume 归此）'),
    (102450026, '1001', 26, '饲料出库',     'feed_out',          'djs_flow_type', '', 'warning', 'N', 1, NOW(), 'FIX-WMS-FLOWDICT-001 belong_type=feed 的领用出库');

-- ------------------------------------------------------------
-- 3. djs_stock_out_dest 新增 5 值（出库去向业务 case）
--    现有 6 值：kitchen / mine / daye_store / personal / feed / dept
--    新增 5 值按业务出库场景，各出库路径自动回填、前端只读不可改。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1006306, '1001', 6,  '发货月台', 'ship_dock',  'djs_stock_out_dest', '', 'warning', 'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-001 发货 / 白条领去发货月台'),
    (1006307, '1001', 7,  '部门领用', 'dept_pick',  'djs_stock_out_dest', '', 'info',    'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-001 养殖/种植物资领用去向'),
    (1006308, '1001', 8,  '白条分割', 'bar_cut',    'djs_stock_out_dest', '', 'primary', 'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-001 白条出库去分割间'),
    (1006309, '1001', 9,  '生产领用', 'prod_pick',  'djs_stock_out_dest', '', 'info',    'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-001 仓库生产消耗去向'),
    (1006310, '1001', 10, '盘点计损', 'check_loss', 'djs_stock_out_dest', '', 'danger',  'N', NULL, NOW(), 'FIX-WMS-FLOWDICT-001 盘点计损 / 异常出库去向');
