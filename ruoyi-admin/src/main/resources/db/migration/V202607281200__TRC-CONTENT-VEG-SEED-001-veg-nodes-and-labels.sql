-- TRC-CONTENT-VEG-SEED-001 — 追溯事件字典补全果蔬工序节点 + 猪肉链节点 label 校准。
--
-- 背景：djs_trace_content 原只灌猪肉链 + 通用节点（marketing/singe/slaughter/acid/in_stock/ship/
-- arrival = 010..016 + white_bar_in/white_bar_pick = 017/018）。果蔬链 4 个合成工序节点
-- （sowing/harvest/veg_handle/pack，由 TracePublicServiceImpl#appendVegProcessNodes 按业务表时间补造）
-- 从未入字典 → admin 追溯页（TraceEventTimeline.vue 走 dict-tag :options=djs_trace_content）显示英文 raw key。
--
-- 本迁移：
--   ① 补 4 个果蔬节点：sowing「播种」/ harvest「采摘」/ veg_handle「毛菜处理」/ pack「打包」
--      （in_stock 已在 010 段，果蔬入库复用，不重复 seed）。dict_code 续 102454019+。
--   ② B7（Kevin 确认 · 邓博 row19）：singe label「燎毛」→「屠宰完成」。这是 trace 专用字典，
--      不影响别处「燎毛」工序文案。slaughter/acid 是 spec 未列细节点，本轮不删/不隐藏（待 row20 视觉轮确认）。
--   ③ 采摘 label 与邓博 row19 对齐为「采摘」（直接 seed，非沿用「采收」）。
--
-- ⚠️ 跑后须 bash script/sql/djs/_post-init.sh flush redis 字典缓存（否则追溯时间轴显示 raw key / 旧 label）。

-- ① 果蔬链 4 个工序节点（dict_code 续 102454019）
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102454019, '1001',  9, '播种',     'sowing',     'djs_trace_content', '', 'success', 'N', 1, NOW(), 'TRC-CONTENT-VEG-SEED-001 果蔬节点'),
    (102454020, '1001', 10, '采摘',     'harvest',    'djs_trace_content', '', 'success', 'N', 1, NOW(), 'TRC-CONTENT-VEG-SEED-001 果蔬节点 邓博 row19'),
    (102454021, '1001', 11, '毛菜处理', 'veg_handle', 'djs_trace_content', '', 'info',    'N', 1, NOW(), 'TRC-CONTENT-VEG-SEED-001 果蔬节点'),
    (102454022, '1001', 12, '打包',     'pack',       'djs_trace_content', '', 'primary', 'N', 1, NOW(), 'TRC-CONTENT-VEG-SEED-001 果蔬节点');

-- ② B7：singe 节点 label「燎毛」→「屠宰完成」（trace 专用字典，邓博 row19 猪只 6 事件时间轴）
UPDATE sys_dict_data
SET dict_label = '屠宰完成'
WHERE dict_type = 'djs_trace_content' AND dict_value = 'singe';
