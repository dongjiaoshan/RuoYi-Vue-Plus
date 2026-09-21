-- PLT-ROTATE-ONCE-001 退茬一次性化：明细行加「已退茬」标记 + 回填历史
--
-- 背景：退茬原先只改 plot_info.plot_status=1，在「这一茬」上不留痕。上一茬退完后
-- harvest_status 仍是 completed，只要地块被下一茬重新占用并进入采摘（plot_status 回 3），
-- 退茬候选条件「completed AND plot_status=3」就再次成立 —— 上一茬重新进退茬列表，
-- 工人再点一次就把正在采摘的下一茬连带退掉（实测 5 块地中招）。
--
-- 口径：同一地块 + 同一作物 + 一次种植计划 只能退一次 = t_plant_plant_details 一行只能退一次。

ALTER TABLE t_plant_plant_details
    ADD COLUMN rotated_at datetime NULL COMMENT '退茬时间：非空=这一茬已退茬，不再进退茬候选' AFTER change_type;

-- 回填：按 (plot_id, crop_id) 把「第 n 次退茬记录」配给「第 n 条已采完明细」（均按时间升序）。
-- 这条规则同时修正了历史上 farm_records.plant_id 挂错茬的行 —— 配对只看顺序，不看 plant_id。
-- 退茬记录数 > 已采完明细数时（重复退茬），多出来的记录配不到明细，自然不参与回填。
UPDATE t_plant_plant_details d
JOIN (
    SELECT det.id AS detail_id, rot.create_time AS rotated_at
    FROM (
        SELECT id, plot_id, crop_id,
               ROW_NUMBER() OVER (PARTITION BY plot_id, crop_id
                                  ORDER BY begin_actualdate, id) AS rn
        FROM t_plant_plant_details
        WHERE del_flag = '0' AND harvest_status = 'completed'
    ) det
    JOIN (
        SELECT plot_id, crop_id, create_time,
               ROW_NUMBER() OVER (PARTITION BY plot_id, crop_id
                                  ORDER BY create_time) AS rn
        FROM t_plant_farm_records
        WHERE del_flag = '0' AND farm_type = 'rotation'
    ) rot
      ON rot.plot_id = det.plot_id AND rot.crop_id = det.crop_id AND rot.rn = det.rn
) m ON m.detail_id = d.id
SET d.rotated_at = m.rotated_at;
