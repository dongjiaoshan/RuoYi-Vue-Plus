-- FIX-TRC-PORK-CHAIN-001：猪肉追溯链耳号事件历史回填。
--
-- 背景：pork trace_code 在打包出库（IN_STOCK 时刻）才生成，而出栏/燎毛/屠宰/排酸 4 个上游耳号事件
-- 早于此发生，其实时 recordEventByEarNo 调用因 code 未出生反查落空被跳过 → 既有 pork 码只串到
-- in_stock/ship，链路残缺。代码侧已在 TraceServiceImpl.genCode 出生时回填（backfillEarNoEvents），
-- 本迁移把同样逻辑应用到**迁移前已存在**的 pork 码：按耳号从 t_warehouse_bar_info（一头猪一行，
-- 沉淀全生命周期时间戳）重建 4 事件，用各阶段真实时刻写入 t_warehouse_trace_event。
--
-- 时间戳映射（与代码 backfillEarNoEvents 完全一致）：
--   marketing → bar_info.marketing_time（出栏）
--   singe     → bar_info.in_time（燎毛入库）
--   slaughter → bar_info.out_time（分割出库 = 排酸完成）
--   acid      → bar_info.out_time（同 cutDone 触发，排在 slaughter 之后）
--
-- 幂等 & 容错：
--   - WHERE NOT EXISTS 防重复（重跑安全；与代码 findExistingContents 幂等同义）。
--   - 仅当对应 bar_info 时间戳非 NULL（工序已走到）才插入，NULL 阶段跳过、不造假数据。
--   - trace_event immutable append-only：本迁移只 INSERT，不 UPDATE/DELETE。
--   - operator_id 留 NULL：4 阶段操作人分散在 3 张源表，bar_info 不逐阶段存，回填不臆造操作人。
--   - tenant_id 显式 '1001'（V1 单租户；迁移是显式 INSERT 非 entity insertFill 路径）。
-- 回滚：删除本迁移插入的回填事件（event_data 标记 'backfill:FIX-TRC-PORK-CHAIN-001' 便于识别）：
--   DELETE FROM t_warehouse_trace_event
--    WHERE JSON_EXTRACT(event_data,'$.src') = 'FIX-TRC-PORK-CHAIN-001';

-- marketing（出栏）
INSERT INTO t_warehouse_trace_event
    (tenant_id, produce_code, trace_content, trace_time, event_data, operator_id, create_time)
SELECT c.tenant_id, c.produce_code, 'marketing', b.marketing_time,
       JSON_OBJECT('src','FIX-TRC-PORK-CHAIN-001'), NULL, NOW(3)
  FROM t_warehouse_trace_code c
  JOIN t_warehouse_bar_info b
    ON b.ear_no = c.pig_ear_no AND b.del_flag = '0'
 WHERE c.code_type = 'pork'
   AND c.del_flag = '0'
   AND c.pig_ear_no IS NOT NULL AND c.pig_ear_no <> ''
   AND b.marketing_time IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM t_warehouse_trace_event e
        WHERE e.produce_code = c.produce_code AND e.trace_content = 'marketing');

-- singe（燎毛入库）
INSERT INTO t_warehouse_trace_event
    (tenant_id, produce_code, trace_content, trace_time, event_data, operator_id, create_time)
SELECT c.tenant_id, c.produce_code, 'singe', b.in_time,
       JSON_OBJECT('src','FIX-TRC-PORK-CHAIN-001'), NULL, NOW(3)
  FROM t_warehouse_trace_code c
  JOIN t_warehouse_bar_info b
    ON b.ear_no = c.pig_ear_no AND b.del_flag = '0'
 WHERE c.code_type = 'pork'
   AND c.del_flag = '0'
   AND c.pig_ear_no IS NOT NULL AND c.pig_ear_no <> ''
   AND b.in_time IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM t_warehouse_trace_event e
        WHERE e.produce_code = c.produce_code AND e.trace_content = 'singe');

-- slaughter（屠宰分割 = 分割出库时刻）
INSERT INTO t_warehouse_trace_event
    (tenant_id, produce_code, trace_content, trace_time, event_data, operator_id, create_time)
SELECT c.tenant_id, c.produce_code, 'slaughter', b.out_time,
       JSON_OBJECT('src','FIX-TRC-PORK-CHAIN-001'), NULL, NOW(3)
  FROM t_warehouse_trace_code c
  JOIN t_warehouse_bar_info b
    ON b.ear_no = c.pig_ear_no AND b.del_flag = '0'
 WHERE c.code_type = 'pork'
   AND c.del_flag = '0'
   AND c.pig_ear_no IS NOT NULL AND c.pig_ear_no <> ''
   AND b.out_time IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM t_warehouse_trace_event e
        WHERE e.produce_code = c.produce_code AND e.trace_content = 'slaughter');

-- acid（排酸 = 排酸完成 = 分割出库时刻；排在 slaughter 之后）
INSERT INTO t_warehouse_trace_event
    (tenant_id, produce_code, trace_content, trace_time, event_data, operator_id, create_time)
SELECT c.tenant_id, c.produce_code, 'acid', b.out_time,
       JSON_OBJECT('src','FIX-TRC-PORK-CHAIN-001'), NULL, NOW(3)
  FROM t_warehouse_trace_code c
  JOIN t_warehouse_bar_info b
    ON b.ear_no = c.pig_ear_no AND b.del_flag = '0'
 WHERE c.code_type = 'pork'
   AND c.del_flag = '0'
   AND c.pig_ear_no IS NOT NULL AND c.pig_ear_no <> ''
   AND b.out_time IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM t_warehouse_trace_event e
        WHERE e.produce_code = c.produce_code AND e.trace_content = 'acid');
