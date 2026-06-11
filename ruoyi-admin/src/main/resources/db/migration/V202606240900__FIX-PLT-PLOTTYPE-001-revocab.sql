-- ============================================================================
-- FIX-PLT-PLOTTYPE-001 地块类型词表替换（保育/露天/单体棚/连体棚）+ 存量数据迁移
--
-- 产品（邓博）将种植地块类型词表定为 保育 / 露天 / 单体棚 / 连体棚，
-- 替换 djs_plot_type 现值 大棚(greenhouse) / 露天(open) / 水田(paddy)。
-- 「保育」(nursery) 供 mp 移栽过滤 plot_type='nursery'。
-- ============================================================================

-- 1) 删旧词表全部数据行（大棚/露天/水田）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_plot_type' AND tenant_id = '1001';

-- 2) 灌新词表 4 行（value 用英文 code，label 中文；open 沿用旧值减少迁移）
INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
  (1002010, '1001', 0, '保育',   'nursery',     'djs_plot_type', '', 'primary', 'N', NULL, NOW()),
  (1002011, '1001', 1, '露天',   'open',        'djs_plot_type', '', 'success', 'Y', NULL, NOW()),
  (1002012, '1001', 2, '单体棚', 'single_shed', 'djs_plot_type', '', 'warning', 'N', NULL, NOW()),
  (1002013, '1001', 3, '连体棚', 'multi_shed',  'djs_plot_type', '', 'info',    'N', NULL, NOW());

-- 3) 存量地块数据平迁：露天(open)保留；大棚/水田/旧重复值无 1:1 后继 → NULL（待客户在 admin 重标，不擅自映射成单体或连体）
UPDATE t_plant_plot_info
   SET plot_type = NULL
 WHERE tenant_id = '1001'
   AND plot_type IN ('greenhouse', 'paddy', 'shed');
