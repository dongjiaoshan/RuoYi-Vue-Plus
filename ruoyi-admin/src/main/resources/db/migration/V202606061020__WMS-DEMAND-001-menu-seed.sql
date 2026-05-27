-- WMS-DEMAND-001 菜单 seed（9040-9054 共 15 行）
-- 父菜单 9000 仓库 已由 D7 WMS-MD-001 (V202605290910) seed
-- ADR-0006 菜单分治：仓库域 9000-10999；需求管理占 9040-9069 段

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
    (9040, '需求管理',   9000, 40, 'demand',                NULL,                                                1, 0, 'M', '0', '0', '',                                       'documentation', 1, NOW(), 'WMS-DEMAND-001'),
    (9041, '白条需求',   9040, 1,  'demand-white-bar',      'djs-warehouse/demand/white-bar/index',              1, 0, 'C', '0', '0', 'djs:warehouse:demand:list',              'meat',          1, NOW(), 'WMS-DEMAND-001'),
    (9042, '蔬菜需求',   9040, 2,  'demand-vegetable',      'djs-warehouse/demand/vegetable/index',              1, 0, 'C', '0', '0', 'djs:warehouse:demand:list',              'apple',         1, NOW(), 'WMS-DEMAND-001'),
    (9043, '礼盒需求',   9040, 3,  'demand-gift-box',       'djs-warehouse/demand/gift-box/index',               1, 0, 'C', '0', '0', 'djs:warehouse:demand:list',              'gift',          1, NOW(), 'WMS-DEMAND-001'),
    (9044, '其他需求',   9040, 4,  'demand-other',          'djs-warehouse/demand/other/index',                  1, 0, 'C', '0', '0', 'djs:warehouse:demand:list',              'list',          1, NOW(), 'WMS-DEMAND-001'),
    (9045, '需求查询',   9040, 5,  '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:query',             '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9046, '需求新增',   9040, 6,  '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:add',               '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9047, '需求修改',   9040, 7,  '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:edit',              '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9048, '需求删除',   9040, 8,  '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:remove',            '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9049, '需求确认',   9040, 9,  '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:confirm',           '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9050, '开始排产',   9040, 10, '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:start_production',  '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9051, '取消需求',   9040, 11, '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:cancel',            '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9052, '指定猪只',   9040, 12, '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:assign_pig',        '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9053, '需求导出',   9040, 13, '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:export',            '#',             1, NOW(), 'WMS-DEMAND-001'),
    (9054, '状态历史',   9040, 14, '',                      '',                                                  1, 0, 'F', '0', '0', 'djs:warehouse:demand:history',           '#',             1, NOW(), 'WMS-DEMAND-001');

-- 超级管理员角色 1 → 全菜单（与 WMS-MD-002 V202605311100 同范式）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 9040), (1, 9041), (1, 9042), (1, 9043), (1, 9044),
    (1, 9045), (1, 9046), (1, 9047), (1, 9048), (1, 9049),
    (1, 9050), (1, 9051), (1, 9052), (1, 9053), (1, 9054);
