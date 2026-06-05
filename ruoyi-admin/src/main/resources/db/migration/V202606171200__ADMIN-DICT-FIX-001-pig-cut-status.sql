-- ============================================================
-- ADMIN-DICT-FIX-001：分割记录状态字典 seed
--   pigCut/index.vue 原硬编码 cutStatusOptions（待领用/已领用/分割中/已完成），
--   无对应 sys_dict seed。本迁移补 djs_pig_cut_status（命名族同 djs_pig_cut_part），
--   admin 页面改走字典渲染（ADR-0004 复用优先 / seed-first）。
--
--   dict_id   : 103110   （102310 已被 djs_record_type 占用，改用高段空闲 id）
--   dict_code : 1031100-1031103
--   幂等：INSERT IGNORE（显式 id，重复执行不报错）
-- ============================================================

INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark) VALUES
    (103110, '1001', '猪肉分割状态', 'djs_pig_cut_status', 1, NOW(), 'ADMIN-DICT-FIX-001 / WMS-PIG-002 分割记录状态');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1031100, '1001', 1, '待领用', 'pending_pickup', 'djs_pig_cut_status', '', 'info',    'Y', 1, NOW()),
    (1031101, '1001', 2, '已领用', 'picked',         'djs_pig_cut_status', '', 'warning', 'N', 1, NOW()),
    (1031102, '1001', 3, '分割中', 'cutting',        'djs_pig_cut_status', '', 'warning', 'N', 1, NOW()),
    (1031103, '1001', 4, '已完成', 'done',           'djs_pig_cut_status', '', 'success', 'N', 1, NOW());
