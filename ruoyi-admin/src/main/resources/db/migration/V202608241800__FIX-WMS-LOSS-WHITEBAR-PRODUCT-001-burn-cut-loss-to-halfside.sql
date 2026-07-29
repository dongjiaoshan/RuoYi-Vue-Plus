-- FIX-WMS-LOSS-WHITEBAR-PRODUCT-001：燎毛 / 分割损耗统一归属白条产品「半扇」
--
-- 白条业态的正常态产品必须唯一：代码侧 resolveWhiteBarProductId（PigBurnRecordServiceImpl /
-- PigCutRecordServiceImpl）按 belong_type='white_bar' + product_status=0 升序取首条解析归属产品，
-- 库里同时存在多条正常态白条产品时会按业务码字典序静默选中非预期的那条。
-- 早期 seed 的 PROD-WHITE-BAR-* 占位产品业务码全部排在甲方维护的 Y 码之前，故停用它们
-- （停用不删除：存量流水、库存、分割记录仍指向这些 id，删除会造成悬空外键）。
--
-- EXISTS 守卫：仅当该租户已有甲方自建的白条产品时才停用占位产品。全新建库里白条产品只有这几条
-- seed，无条件停用会让「正常态白条产品」归零 —— 燎毛入库没产品可选、分割领用抛「白条产品主数据缺失」。
UPDATE t_warehouse_product_info p
   SET p.product_status = 1,
       p.update_time    = NOW()
 WHERE p.del_flag = '0'
   AND p.belong_type = 'white_bar'
   AND p.product_id LIKE 'PROD-WHITE-BAR-%'
   AND p.product_status = 0
   AND EXISTS (
       SELECT 1 FROM (SELECT * FROM t_warehouse_product_info) q
        WHERE q.del_flag = '0'
          AND q.belong_type = 'white_bar'
          AND q.product_status = 0
          AND q.tenant_id = p.tenant_id
          AND q.product_id NOT LIKE 'PROD-WHITE-BAR-%'
   );

-- 存量损耗流水回填：燎毛损耗历史上不挂产品（product_id NULL，损耗总览产品编码/名称列显 '—'），
-- 分割损耗可能挂在已停用的旧白条产品上、或产品快照文案已过期（产品改名不会回写流水快照）。
-- 这里把两类存量行的产品 id 与四个快照列一并回填成当前正常态白条产品。
--
-- 白条产品由甲方在 admin 产品配置维护，各环境雪花 id 不同 —— 用派生表按类别解析，不写死 id。
-- 某租户没有正常态白条产品时该租户无匹配行，UPDATE 影响 0 行，安全。
-- 预冷损耗（precool_loss）不在回填范围：它按燎毛产出行的真实产品（猪头 / 猪脚 / 蹄髈等）记录，
-- 与写入侧口径一致。
UPDATE t_warehouse_loss_flow lf
JOIN (
    SELECT tenant_id, id, product_id AS product_code, product_name, product_unit, belong_type
    FROM (
        SELECT p.tenant_id,
               p.id,
               p.product_id,
               p.product_name,
               p.product_unit,
               p.belong_type,
               ROW_NUMBER() OVER (PARTITION BY p.tenant_id ORDER BY p.product_id) AS rn
        FROM t_warehouse_product_info p
        WHERE p.del_flag = '0'
          AND p.belong_type = 'white_bar'
          AND p.product_status = 0
    ) ranked
    WHERE rn = 1
) wb
  ON wb.tenant_id = lf.tenant_id
SET lf.product_id   = wb.id,
    lf.product_code = wb.product_code,
    lf.product_name = wb.product_name,
    lf.product_unit = wb.product_unit,
    lf.belong_type  = wb.belong_type
WHERE lf.del_flag = '0'
  AND lf.loss_type IN ('burn_loss', 'cut_loss');
