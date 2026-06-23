-- ============================================================
-- WMS-CUT-REMAIN-001  白条分割「剩余重量」+ 超量校验 stable key
-- 问题：外购白条无耳号（ear_no=NULL），分割剩余重量按 ear_no 聚合 cut_out_in 流水失效，
--   外购记录剩余重量恒 null（不显示），且 submitCutOut 无超量拦截。
-- 解法：给 t_warehouse_stock_flow 加 white_bar_id 列，分割产出流水（cut_out_in）按 white_bar_id 关联
--   白条（= bar_info.id = cut_record.white_bar_id），自养/外购同口径（不依赖 ear_no）。
-- 存量 cut_out_in 流水按 remark 里的 cut_id 反查 cut_record 回填 white_bar_id（remark 格式固定
--   「分割产出入冻品库 cut_id=<CUT...> part=<...>」，已核实 35 行 100% 可匹配）。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 加列（NULL 允许；仅 cut_out_in 流水写值，其它流水类型保持 NULL）
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN white_bar_id BIGINT NULL COMMENT '白条 ID（= t_warehouse_bar_info.id；仅分割产出 cut_out_in 流水写值，分割剩余重量/超量校验按此聚合，外购无耳号也可用）' AFTER ear_no;

-- 2) 索引：分割剩余重量按 white_bar_id 聚合 cut_out_in 流水
ALTER TABLE t_warehouse_stock_flow
    ADD KEY idx_tenant_whitebar (tenant_id, white_bar_id);

-- 3) 存量回填：cut_out_in 流水按 remark 中 cut_id 反查 cut_record.white_bar_id
UPDATE t_warehouse_stock_flow f
JOIN t_warehouse_pig_cut_record c
  ON c.cut_id = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(f.remark, 'cut_id=', -1), ' ', 1))
 AND c.del_flag = '0'
SET f.white_bar_id = c.white_bar_id
WHERE f.flow_type = 'cut_out_in'
  AND f.del_flag = '0'
  AND f.white_bar_id IS NULL
  AND f.remark LIKE '%cut_id=%';
