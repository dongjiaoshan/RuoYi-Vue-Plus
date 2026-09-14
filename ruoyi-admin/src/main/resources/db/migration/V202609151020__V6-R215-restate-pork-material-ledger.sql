-- ============================================================================
-- V6-R215  猪肉原材料盘点历史行重述：把错记成损耗的现场打包量挪到销售量
--
-- 背景（甲方 row215 第 1~4 条）：猪肉原材料行改成「销售量取现场打包追溯码的原材料消耗量、
--   期末与损耗手填默认零、退回量 = 期初+入库-销售-赠送-期末-损耗 倒算」。
--   改造**之前**落库的行走的是旧口径 —— 销售量取销售流水（猪肉原材料没有流水，恒 0）、
--   损耗是倒算残差，于是当天现场打包掉的那部分全被记进了 loss_qty。
--   staging 实查：猪肉原材料行 sale_qty 恒 0、loss_qty 恒等于 inbound_qty，无一例外。
--   这正是甲方要修的现象本身。
--
-- 不重述会怎样：新口径下 loss_qty 被当成「工人手填的损耗」再减一次，历史行一打开
--   门店盘点的「修改」抽屉就倒算出 **负的退回量**（实测 店…957057/8-22 通排 -10.000、
--   店…067713/8-20 里脊肉 -1.001），前端负值校验直接拦住提交。
--
-- 口径（Kevin 2026-09-14 拍板 D-0058）：**只把错记的打包量从损耗挪到销售**，三条边界：
--   1) sale_qty  += 当日该门店该原材料的现场打包消耗量
--   2) loss_qty  -= 同一个数（下限 0，不允许出现负损耗）
--   3) wh_return_qty **不动** —— 旧口径下它存的是「退回操作」的真实退回量，是好数据，
--      换成倒算残差反而更不准。实测重述后 期初+入库-销售-赠送-期末-损耗 恰好等于它（4/4 行自洽）。
--
-- 影响面（执行前 staging 实查）：4 行，无一行损耗会被减成负数，无一行重述后不自洽。
--
-- 幂等：靠 `l.sale_qty = 0` 这道闸 —— 重述过的行 sale_qty 已非 0，重跑直接跳过。
--
-- ⚠️ 这道闸**会连带跳过「工人当年手填过销售量」的旧行**（staging 实查 10 行，8-02~8-05，
--   销售量 0.3~2.0 不等）。其中 1 行（店 9315000000000001 / 8-02 / 五花肉）当天确实也有
--   0.251kg 现场打包躺在损耗里。**故意不动它**：新口径下销售量 = 追溯码消耗量（不是叠加），
--   重述会把工人手填的 2.000 直接换成 0.251、抹掉人工录入；而它按新口径倒算是
--   退回 = 0+5-0.251-1-0-1 = 2.749 > 0，不会触发负值拦截，没有非改不可的理由。
--   剩下 9 行当天没有现场打包，损耗里本就没有要挪的量。
--
-- 解析不出原材料的现场码（部位名在产品表里查无此人 / 成品没配 product_material）不计入，
--   与 StoreTraceServiceImpl.sumOnsiteConsumedWeightByMaterial 的 log.warn 分支同口径。
--
-- 取号：staging 已应用到 202609150900（BRD-STAT-002），同批未部署的还有 202609151000 (R214)
--   与 202609151010 (STR-RETURN-OPS-001)，本文件取 202609151020（ADR-0008 §2.5：必须 > 目标库
--   当前 max + 1h buffer，否则 out-of-order=false 会拒绝启动）。
-- ============================================================================
SET NAMES utf8mb4;

UPDATE t_store_daily_ledger l
JOIN t_warehouse_product_info p
  ON p.id = l.product_id
 -- 「猪肉原材料产品」判据与 StoreDailyLedgerServiceImpl.isPorkRawMaterial 逐字一致
 AND p.product_attr = 2
 AND p.belong_type IN ('pork', 'white_bar')
JOIN (
    -- 当日该门店现场打包消耗掉的原材料重量，按原材料 id 合计。
    -- remark 形如「现场生码 部位=黑毛猪通排1000g/份 重量=8.000kg」，表里没有专用列，只能按文本解析；
    -- 「部位」写的是打包**成品名**，要折算到它的 product_material 才是原材料（成品 500g/1000g
    -- 两个规格折到同一个原材料上累加）。部位名本身就是原材料时（product_attr=2）取它自己。
    SELECT tc.store_id,
           DATE(tc.create_time) AS ledger_date,
           IF(pm.product_attr = 2, pm.id, pm.product_material) AS material_id,
           SUM(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(tc.remark, '重量=', -1), 'kg', 1) AS DECIMAL(12, 3))) AS used
    FROM t_warehouse_trace_code tc
    JOIN t_warehouse_product_info pm
      ON pm.del_flag = '0'
     AND pm.product_name = SUBSTRING_INDEX(SUBSTRING_INDEX(tc.remark, '部位=', -1), ' 重量=', 1)
    WHERE tc.code_type = 'pork'
      AND tc.del_flag = '0'
      AND tc.remark LIKE '现场生码%'
      AND tc.store_id IS NOT NULL
    GROUP BY tc.store_id, DATE(tc.create_time), IF(pm.product_attr = 2, pm.id, pm.product_material)
) s
  ON s.store_id = l.store_id
 AND s.ledger_date = l.ledger_date
 AND s.material_id = l.product_id
SET l.sale_qty = s.used,
    l.loss_qty = GREATEST(l.loss_qty - s.used, 0)
WHERE l.del_flag = '0'
  AND s.material_id IS NOT NULL
  -- 幂等闸：只重述还没被重述过的旧口径行
  AND l.sale_qty = 0;

-- 验收 query —— 真正要守的是「没有任何猪肉原材料行按新口径倒算出负的退回量」
--   （负值会被前端校验拦住提交，是这次重述要消灭的现象本身）。应返 0 行：
--
--   SELECT l.store_id, l.ledger_date, l.product_id,
--          l.opening_qty + l.inbound_qty - COALESCE(s.used, 0) - l.gift_qty
--            - l.closing_qty - l.loss_qty AS derived_return
--     FROM t_store_daily_ledger l
--     JOIN t_warehouse_product_info p ON p.id = l.product_id
--      AND p.product_attr = 2 AND p.belong_type IN ('pork','white_bar')
--     LEFT JOIN (
--       SELECT tc.store_id, DATE(tc.create_time) d,
--              IF(pm.product_attr = 2, pm.id, pm.product_material) mid,
--              SUM(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(tc.remark,'重量=',-1),'kg',1) AS DECIMAL(12,3))) used
--         FROM t_warehouse_trace_code tc
--         JOIN t_warehouse_product_info pm ON pm.del_flag = '0'
--          AND pm.product_name = SUBSTRING_INDEX(SUBSTRING_INDEX(tc.remark,'部位=',-1),' 重量=',1)
--        WHERE tc.code_type = 'pork' AND tc.del_flag = '0'
--          AND tc.remark LIKE '现场生码%' AND tc.store_id IS NOT NULL
--        GROUP BY 1, 2, 3
--     ) s ON s.store_id = l.store_id AND s.d = l.ledger_date AND s.mid = l.product_id
--    WHERE l.del_flag = '0'
--      AND l.opening_qty + l.inbound_qty - COALESCE(s.used, 0) - l.gift_qty
--          - l.closing_qty - l.loss_qty < 0;
