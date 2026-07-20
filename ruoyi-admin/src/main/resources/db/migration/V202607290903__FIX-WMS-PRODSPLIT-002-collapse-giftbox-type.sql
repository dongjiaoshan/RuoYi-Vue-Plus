-- ============================================================
-- FIX-WMS-PRODSPLIT-002  废弃 product_type=3（礼盒），礼盒归入 belong_type=gift_box
-- ============================================================
-- 客户（kevin-miniapp问题汇总 行5）：产品配置表单「产品类型」字段多余——自产/礼盒不是一个维度，
-- 「礼盒」已是产品类别（belong_type=gift_box）一项。Kevin 拍板：彻底废弃 product_type=3，
-- productType 仅保留 1=自产 / 2=外购；礼盒 = product_type=1 + belong_type=gift_box。
--
-- 配套代码改动（同批）：
--   后端 ProductProductionServiceImpl 礼盒打包校验由 productType==3 改 belong_type==gift_box、产出记录 set type=1；
--   后端 ProductInfoServiceImpl 去掉 productType=3 分支（礼盒按 type=1+belong=gift_box 走）；
--   plus-ui 产品配置表单删「产品类型」下拉、入口固定 type（产品配置=1/商品配置=2）、礼盒打包按 belong_type=gift_box；
--   miniapp 礼盒选择/打包按 belong_type=gift_box 过滤；3 个单测类同步。
--
-- 仅迁移 djs_product_type 语义的两表（master + 产出），不动 demand 的 djs_demand_product_type(VARCHAR)。
-- 业务表无显式 tenant_id 操作（这里只改既有行的 product_type/belong_type）。
-- 生效：迁移 + 重启 admin；dict 改动跑 _post-init.sh flush redis 字典缓存。
-- ============================================================

-- 1. 产品主数据：礼盒(type=3) → 自产(type=1)，并确保 belong_type=gift_box（理论上已是，兜底补全）
UPDATE t_warehouse_product_info
   SET belong_type = 'gift_box'
 WHERE product_type = 3 AND (belong_type IS NULL OR belong_type <> 'gift_box');
UPDATE t_warehouse_product_info
   SET product_type = 1
 WHERE product_type = 3;

-- 2. 产出记录（礼盒打包产出 stamp 了 type=3）→ 同步降为 type=1
UPDATE t_warehouse_product_production
   SET product_type = 1
 WHERE product_type = 3;

-- 3. 删除 djs_product_type 字典「礼盒=3」项（productType 仅余 1=自产 / 2=外购）
DELETE FROM sys_dict_data WHERE dict_type = 'djs_product_type' AND dict_value = '3';

-- 4. 移除已隐藏的旧「礼盒配置」菜单 9038（FIX-WMS-PRODSPLIT-001 建，query_param productType=3）+ 授权行
DELETE FROM sys_role_menu WHERE menu_id = 9038;
DELETE FROM sys_menu      WHERE menu_id = 9038;
