-- FIX-BRD-PIG-STATUS-BACKFILL-001 — 回填 current_status 空串/NULL 脏数据
-- （邓博 2026-06-17 #39 阉割阻塞 + 2026-06-18 引种猪配种里看不到）。
--
-- 根因：部分猪 current_status='' （空串，非 NULL，列 varchar(16) NOT NULL DEFAULT 'HB'）。
--   这些猪：(1) 选中后任何事件走 fireEvent → parseLifecycle('') → 抛 pig.state.invalid（阉割报错）；
--   (2) 在配种/事件 picker 里因状态不在可选白名单（HB/KH/DN/FQ/LC 等）而看不到。
--   来源两类：a) 一次性 bulk 测试数据（无 status_record）；b) 个别经事件流转的猪 current_status 与
--   status_record 失同步（status_record 正确、pig 列空）。正常 createPig / batchEarTag / fireEvent
--   均已正确置态，本回填解存量脏值。
--
-- 回填口径（按正确度优先）：
--   1) 有 status_record 的猪 → 取最新一条 new_status 恢复真实态（如已分娩母猪恢复 FM，不是粗暴 HB）；
--   2) 无 status_record 的猪（bulk 数据）→ 按类型初始态：公猪 BOAR_ACTIVE / 其余 HB（与 createPig 一致）。
-- 幂等：只命中 NULL/'' 行，重跑安全。纯数据 SQL，Flyway 自动应用，无需 mvn install。
-- V202607031102 > 当前 max V202607031101。

-- 1) 有 status_record：恢复最新事件后的真实状态
UPDATE t_farm_pig_info p
JOIN (
    SELECT r.pig_id, r.new_status
    FROM t_farm_status_record r
    JOIN (SELECT pig_id, MAX(id) mid FROM t_farm_status_record GROUP BY pig_id) m
      ON m.pig_id = r.pig_id AND m.mid = r.id
) latest ON latest.pig_id = p.id
SET p.current_status = latest.new_status
WHERE p.del_flag = '0'
  AND (p.current_status IS NULL OR p.current_status = '')
  AND latest.new_status IS NOT NULL
  AND latest.new_status <> '';

-- 2) 无 status_record（bulk 测试数据）：按类型初始态兜底
UPDATE t_farm_pig_info
SET current_status = CASE WHEN pig_type = 'boar' THEN 'BOAR_ACTIVE' ELSE 'HB' END
WHERE del_flag = '0'
  AND (current_status IS NULL OR current_status = '');
