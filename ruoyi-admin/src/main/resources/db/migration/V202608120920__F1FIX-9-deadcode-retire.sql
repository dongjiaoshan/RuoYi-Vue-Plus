-- F1FIX-9 死代码一次性收口（审计 2026-07-05 D8：DEAD-3 / DEAD-4 / DEAD-6；controller 留 V2 删，本迁移只动 DB）
-- 权限变更在用户下次登录生效（Sa-Token 登录时取 perm 集），无需 flush redis 字典缓存。

-- ------------------------------------------------------------
-- ① DEAD-3：收窄 admin「退货记录」页权限，去电死写链
--    menu 9210 的通配 djs:warehouse:return:* 是无前端调用的 ReturnProductController
--    写链（POST / PUT / DELETE / {id}/confirm，仍写已停用的 t_warehouse_return_product，
--    confirm 会真回库存）的唯一供电点。活页面（plus-ui djs-warehouse/return/index.vue）
--    只读，只需 :list（列表两端点 OR 命中）+ :export（store-daily/export OR 命中），
--    故 9210 收窄为 :list，export 以独立 F 行 9216 精确授权（镜像 9210 现有持有角色）。
--    mp 侧 11067 通配（djs:applet:warehouse:return:*）保留不收：在役门店退货链
--    AppletStoreReturnController（/applet/store/return/*）4 端点按设计复用
--    djs:applet:warehouse:return:add（其 Javadoc 明示），且 mp tabbar 以该通配 gate 入口，
--    收回即打断在役退货流；mp 死端点（AppletReturnController）与活端点权限串相同，
--    SQL 层不可分，只能 V2 删 Java 收口。
--    9206 与 9211-9215 颗粒度行已由 V202607261300 / V202607261400 删除，DB 中无残留，不再处理。
UPDATE sys_menu
   SET perms = 'djs:warehouse:return:list'
 WHERE menu_id = 9210
   AND perms = 'djs:warehouse:return:*';

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
    (9216, '退货导出', 9210, 1, '', '', '', 1, 0, 'F', '1', '0', 'djs:warehouse:return:export', '#', 1, NOW(), 'F1FIX-9 admin 退货记录页只读（list + export）');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT role_id, 9216
  FROM sys_role_menu
 WHERE menu_id = 9210;

-- ------------------------------------------------------------
-- ② DEAD-6：清 STORE_CHECK_NO 编码规则 seed 残件
--    表 t_store_check_record 已 DROP（V202607221030）、BizCodeType.STORE_CHECK_NO 枚举已删，
--    规则行为孤儿（无任何代码路径生成 SC 码）。
DELETE FROM t_md_biz_code_rule WHERE code_type = 'STORE_CHECK_NO';

-- ------------------------------------------------------------
-- ③ DEAD-4：DROP 8 张孤儿表（主代码 / mapper XML / 三前端零读写，无 FK 引用，无 seed 数据；
--    t_farm_annual_indicator 已改名 t_farm_year_production 活用，不在本清单）
DROP TABLE IF EXISTS pig_batch;                   -- 审计 DEAD-4 证实建库后零读写
DROP TABLE IF EXISTS t_breed_production_config;   -- 审计 DEAD-4 证实建库后零读写
DROP TABLE IF EXISTS t_farm_grow_record;          -- 审计 DEAD-4 证实建库后零读写
DROP TABLE IF EXISTS t_warehouse_gift_box;        -- 审计 DEAD-4 证实建库后零读写
DROP TABLE IF EXISTS t_warehouse_product_produce; -- 审计 DEAD-4 证实建库后零读写
DROP TABLE IF EXISTS t_breed_medicine_use;        -- 审计 DEAD-4 证实建库后零读写（同名功能实际落 t_breed_medicine_usage）
DROP TABLE IF EXISTS t_farm_medicine_record;      -- 审计 DEAD-4 证实建库后零读写（同名功能实际落 t_breed_medicine_record）
DROP TABLE IF EXISTS t_warehouse_supplier_record; -- 审计 DEAD-4 证实建库后零读写（供应商往来实际走采购流水）
