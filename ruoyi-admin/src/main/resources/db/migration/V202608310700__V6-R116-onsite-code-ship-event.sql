-- V6 row116：门店现场生码（门店猪肉打包）的追溯页时间线缺「冷链发货」。
-- 原因：那趟把白条冷链送到店的发货事件挂在上游白条产出行自己的码上，门店现场码出生时只回填了
-- marketing/singe/white_bar_in/white_bar_pick/slaughter/acid 六个耳号事件 + 自己的 arrival/in_stock，
-- 没有 ship → C 端时间线从「屠宰完成」直接跳到「到店」。生码逻辑已补回填（TraceServiceImpl
-- #backfillOnsiteShipEvent）；本脚本补齐存量的现场码。
-- 取值：同一头猪、且发货时刻不晚于本码出生时刻的最近一条 ship 事件（真实发货时刻，不造时间）。
-- 只补 remark 带「现场生码」前缀的门店码 —— 仓库打包码不能补，同一头猪「半扇A已发门店、半扇B还在仓库」
-- 是正常情况，给仓库里的货盖发货戳就是造假。
INSERT INTO t_warehouse_trace_event (tenant_id, produce_code, trace_content, trace_time)
SELECT tc.tenant_id, tc.produce_code, 'ship', MAX(te2.trace_time)
  FROM t_warehouse_trace_code tc
  JOIN t_warehouse_trace_code tc2
    ON tc2.pig_ear_no = tc.pig_ear_no
   AND tc2.produce_code <> tc.produce_code
  JOIN t_warehouse_trace_event te2
    ON te2.produce_code = tc2.produce_code
   AND te2.trace_content = 'ship'
 WHERE tc.del_flag = '0'
   AND tc.code_type = 'pork'
   AND tc.remark LIKE '现场生码%'
   AND te2.trace_time <= tc.create_time
   AND NOT EXISTS (SELECT 1
                     FROM t_warehouse_trace_event te
                    WHERE te.produce_code = tc.produce_code
                      AND te.trace_content = 'ship')
 GROUP BY tc.tenant_id, tc.produce_code;
