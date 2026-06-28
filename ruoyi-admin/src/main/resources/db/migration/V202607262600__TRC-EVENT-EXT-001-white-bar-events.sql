-- TRC-EVENT-EXT-001 — 追溯事件类型扩展：白条入库 + 白条出库(领用)（邓博 admin 测试表 row19 拆出 2 独立事件）。
--
-- 字典 djs_trace_content 增 2 值（dict_code 续 102454017/018；现有 marketing..arrival = 010..016）。
-- hook：PigBurnRecordServiceImpl.finishBurn（白条入库，燎毛入库时刻按耳号写）
--      + PigCutRecordServiceImpl.submitPickup（白条出库领用，白条领用出库时刻按耳号写）。
-- 读聚合事件驱动（TracePublicServiceImpl 按 trace_event 行建时间轴），新事件自动纳入，无需改读端。
--
-- ⚠️ 跑后须 bash script/sql/djs/_post-init.sh flush redis 字典缓存（否则追溯时间轴显示 raw key）。

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (102454017, '1001', 7, '白条入库',      'white_bar_in',   'djs_trace_content', '', 'info',    'N', 1, NOW(), 'TRC-EVENT-EXT-001 邓博 row19'),
    (102454018, '1001', 8, '白条出库(领用)', 'white_bar_pick', 'djs_trace_content', '', 'warning', 'N', 1, NOW(), 'TRC-EVENT-EXT-001 邓博 row19');
