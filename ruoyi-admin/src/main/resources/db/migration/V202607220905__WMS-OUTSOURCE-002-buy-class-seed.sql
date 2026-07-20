-- WMS-OUTSOURCE-002：djs_buy_class 外购商品分类 seed
-- dict_type 102012 已在 WMS-MD-002 建好、dict_data 空（客户 Q3 未明示具体类目）。
-- 本迁移 seed 一组起始分类（客户后续可在 admin 字典管理页增删 / 改 label）。
-- dict_code 取未占的 1027010~1027018 段（1021xxx 段已被 djs_burn_status 占）。
-- 幂等：先清本字典 dict_data 再 INSERT（重 seed 不堆叠）。
DELETE FROM sys_dict_data WHERE dict_type = 'djs_buy_class';

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time)
VALUES
    (1027010, '1001', 1, '原料', 'raw',         'djs_buy_class', '', 'primary', 'N', 103, 1, NOW()),
    (1027011, '1001', 2, '饲料', 'feed',        'djs_buy_class', '', 'success', 'N', 103, 1, NOW()),
    (1027012, '1001', 3, '药品', 'medicine',    'djs_buy_class', '', 'danger',  'N', 103, 1, NOW()),
    (1027013, '1001', 4, '肥料', 'fertilizer',  'djs_buy_class', '', 'warning', 'N', 103, 1, NOW()),
    (1027014, '1001', 5, '农药', 'pesticide',   'djs_buy_class', '', 'danger',  'N', 103, 1, NOW()),
    (1027015, '1001', 6, '种子', 'seed',        'djs_buy_class', '', 'success', 'N', 103, 1, NOW()),
    (1027016, '1001', 7, '设备', 'equipment',   'djs_buy_class', '', 'info',    'N', 103, 1, NOW()),
    (1027017, '1001', 8, '包材', 'packaging',   'djs_buy_class', '', 'info',    'N', 103, 1, NOW()),
    (1027018, '1001', 9, '其他', 'other',       'djs_buy_class', '', 'info',    'N', 103, 1, NOW());
