-- F0FIX-F0-2（STOCK-D2-07）：stock_flow product_id=0 孤儿流水清洗
-- 根因：VegetableHandleServiceImpl 曾在作物未关联果蔬成品时把 stock_flow.product_id 兜底写 0
-- （历史背景见 V202606071520 注释，D9 _open-issues #18）；product_id=0 不是 NULL，
-- 库存总览过滤（product_id IS NOT NULL）拦不住，产生「产品名空、期末为负」无名幽灵行。
-- 写侧兜底已废除（作物未关联产品 → 显式抛错），读侧聚合已加 product_id <> 0 过滤（双保险）。
--
-- 清洗方案：软删（del_flag='1'，对齐 MyBatis-Plus logicDeleteValue）而非物理 DELETE —— 影响最小：
-- 0 值行无法归属任何产品，留着只产生幽灵行；软删后所有读取端（WHERE del_flag='0'）自然排除，
-- 数据仍在表里可追查（操作人/时间/重量），无不可逆风险。
-- 幂等：只命中 del_flag='0' 的 0 值行，软删过的不再命中。
UPDATE t_warehouse_stock_flow
   SET del_flag = '1'
 WHERE product_id = 0
   AND del_flag = '0';
