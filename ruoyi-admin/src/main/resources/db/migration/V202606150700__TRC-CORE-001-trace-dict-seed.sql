-- TRC-CORE-001：追溯字典 seed（表已建于 SYS-INIT-001，本文件仅灌字典）。
--
-- 两套字典，dict_value 对齐 DDL VARCHAR COMMENT（ADR-0004 §2.2）：
--   djs_trace_code_type → t_warehouse_trace_code.code_type VARCHAR(16) 值 pork/veg/gift
--   djs_trace_content   → t_warehouse_trace_event.trace_content VARCHAR(32) 值
--                         marketing/singe/slaughter/acid/in_stock/ship/arrival（V1 7 种，对齐 xlsx + DDL）
--
-- 不复用 djs_trace_event_type（那是 t_store_trace.event_type 的另一套 intro/birth/breed/...，语义不同）。
-- 字典 seed 是显式 INSERT（非 entity insertFill 路径）→ 显式 tenant_id='1001'。

-- ============================================================
-- djs_trace_code_type（追溯码类型）
-- ============================================================
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (920710, '1001', '追溯码类型', 'djs_trace_code_type', 1, NOW(), 'TRC-CORE-001 追溯码业态 pork/veg/gift');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102454000, '1001', 0, '猪肉', 'pork', 'djs_trace_code_type', '', 'danger',  'N', 1, NOW(), 'TRC-CORE-001'),
    (102454001, '1001', 1, '果蔬', 'veg',  'djs_trace_code_type', '', 'success', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454002, '1001', 2, '礼盒', 'gift', 'djs_trace_code_type', '', 'warning', 'N', 1, NOW(), 'TRC-CORE-001');

-- ============================================================
-- djs_trace_content（追溯事件类型，7 种）
-- ============================================================
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (920711, '1001', '追溯事件类型', 'djs_trace_content', 1, NOW(), 'TRC-CORE-001 追溯链事件节点');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102454010, '1001', 0, '出栏上市', 'marketing', 'djs_trace_content', '', 'primary', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454011, '1001', 1, '燎毛',     'singe',     'djs_trace_content', '', 'info',    'N', 1, NOW(), 'TRC-CORE-001'),
    (102454012, '1001', 2, '屠宰分割', 'slaughter', 'djs_trace_content', '', 'warning', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454013, '1001', 3, '排酸',     'acid',      'djs_trace_content', '', 'warning', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454014, '1001', 4, '入库',     'in_stock',  'djs_trace_content', '', 'success', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454015, '1001', 5, '发货',     'ship',      'djs_trace_content', '', 'primary', 'N', 1, NOW(), 'TRC-CORE-001'),
    (102454016, '1001', 6, '到店',     'arrival',   'djs_trace_content', '', 'success', 'N', 1, NOW(), 'TRC-CORE-001');
