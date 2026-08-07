-- V6 row54：有机饲喂记录新增「产品名称」列后，回填历史饲喂台账里被记错的产品。
--
-- 错在哪：毛菜处理「去向=饲料」写 feed_log 时，产品是用 resolveProductIdByCrop 按作物反解的
-- （只要作物配了 related_product 就返回它、无视工人选的产品）。一个作物有多个产品时
-- （红薯 / 红薯杆），红薯杆的饲喂被记成红薯 —— 新加的那一列会整列退化成作物名，等于白加。
-- 写入端已改成用工人选的产品（VegetableHandleServiceImpl.insertFeedLog）。
--
-- 怎么回填：同一笔提交里，feed_log 与 handle_record(record_type=2 处理, handle_target=3 饲料)
-- 是同一个事务、同一个时间戳、同一个重量写下的，据此一一对应，把 handle_record 上工人真正选的
-- 产品搬过来。
--
-- ⚠️ 写成 JOIN 派生表而不是「SET 标量子查询 + EXISTS」：那种写法两处条件容易不一致 ——
-- SET 侧带 HAVING 唯一性、EXISTS 侧不带，遇到「同作物同秒同重量对应多个产品」时
-- SET 子查询返 NULL 而 EXISTS 为真，会把原本有值的 product_id 静默刷成 NULL（且不可自愈，
-- 重跑仍是 NULL），与「匹配不唯一就别动」的本意正相反。JOIN 派生表天然只连唯一匹配的行。
--
-- 匹配不唯一 / 压根没有 handle_record（如采摘活动直送饲料那条链路）的行一律不动，保留旧值。
-- 只动 feed_type='veg_handle'；仓库领用来源（warehouse）的 product_id 本来就是领的原材料本身，不碰。
-- 幂等：命中行反复回填成同一个值。

UPDATE t_warehouse_feed_log fl
  JOIN (
        SELECT hr.tenant_id,
               hr.crop_id,
               hr.handle_time,
               hr.record_weight,
               MIN(hr.product_id) AS product_id
          FROM t_warehouse_handle_record hr
         WHERE hr.del_flag = '0'
           AND hr.record_type = 2
           AND hr.handle_target = 3
           AND hr.product_id IS NOT NULL
         GROUP BY hr.tenant_id, hr.crop_id, hr.handle_time, hr.record_weight
        HAVING COUNT(DISTINCT hr.product_id) = 1
       ) src
    ON src.tenant_id     = fl.tenant_id
   AND src.crop_id       = fl.crop_id
   AND src.handle_time   = fl.feed_date
   AND src.record_weight = fl.feed_weight
   SET fl.product_id = src.product_id
 WHERE fl.del_flag = '0'
   AND fl.feed_type = 'veg_handle';
