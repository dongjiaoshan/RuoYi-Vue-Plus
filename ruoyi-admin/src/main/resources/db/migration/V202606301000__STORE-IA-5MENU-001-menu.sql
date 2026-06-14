-- ============================================================
-- STORE-IA-5MENU-001  门店管理 admin 菜单恢复为「5 板块二级分组」最终态
-- ============================================================
-- 客户 0613 书面最新口径（覆盖前一版 V202606281200 的「4 扁平最终态」）：
--   门店管理(父 10000) 下 5 板块：
--     1. 门店总览              C   10400  djs-store/dashboard/index
--     2. 需求下单              C   10002  djs-store/demand/index
--     3. 门店管理（分组 M）    10600
--        (1) 产品管理          C   10210  djs-store/product/index
--        (2) 门店盘点          C   10200  djs-store/check/index
--     4. 追溯码管理（分组 M）  10500
--        (1) 果蔬追溯码管理    C   10501  djs-store/trace/index?tab=veg
--        (2) 猪肉追溯码管理    C   10520  djs-store/trace/index?tab=pork
--     5. 退回管理（分组 M）    10601
--        (1) 退回操作          C   10360  djs-store/return/index?tab=operation
--        (2) 退回记录          C   10350  djs-store/return/index?tab=record
--
-- 锚点：全部以 menu_id 为准 UPDATE，复用现有空壳目录(10600/10500/10601)和已隐藏菜单
--   (10400/10210/10520/10360)，不 INSERT 新 menu_id。trace/return 均为单页 tab 容器
--   (djs-store/trace/index、djs-store/return/index)，两个二级菜单各指同页 + query_param 预选 tab。
-- 仅 UPDATE sys_menu + INSERT IGNORE sys_role_menu 白名单；不删表/不删行。
-- 跑完必须 flush redis 字典/菜单缓存（bash script/sql/djs/_post-init.sh 或重启 admin）。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 父菜单：门店管理（10000）显示
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '门店管理', visible = '0' WHERE menu_id = 10000;

-- ------------------------------------------------------------
-- 1. 板块①：门店总览（reuse 10400）直挂 10000，撤隐藏 + 改名
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '门店总览', parent_id = 10000, order_num = 1,
    component = 'djs-store/dashboard/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10400;

-- ------------------------------------------------------------
-- 2. 板块②：需求下单（reuse 10002）直挂 10000
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '需求下单', parent_id = 10000, order_num = 2,
    component = 'djs-store/demand/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10002;

-- ------------------------------------------------------------
-- 3. 板块③：门店管理（分组 M，reuse 空壳目录 10600）
--    其下：产品管理(10210) + 门店盘点(10200)
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '门店管理', parent_id = 10000, order_num = 3,
    menu_type = 'M', visible = '0'
WHERE menu_id = 10600;

UPDATE sys_menu
SET menu_name = '产品管理', parent_id = 10600, order_num = 1,
    component = 'djs-store/product/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10210;

UPDATE sys_menu
SET menu_name = '门店盘点', parent_id = 10600, order_num = 2,
    component = 'djs-store/check/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10200;

-- ------------------------------------------------------------
-- 4. 板块④:追溯码管理（分组 M，reuse 空壳目录 10500）
--    其下：果蔬追溯码管理(10501) + 猪肉追溯码管理(10520)，均指单页 tab 容器
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '追溯码管理', parent_id = 10000, order_num = 4,
    menu_type = 'M', visible = '0'
WHERE menu_id = 10500;

UPDATE sys_menu
SET menu_name = '果蔬追溯码管理', parent_id = 10500, order_num = 1,
    path = 'trace-veg', component = 'djs-store/trace/index', query_param = '{"tab":"veg"}',
    menu_type = 'C', visible = '0'
WHERE menu_id = 10501;

UPDATE sys_menu
SET menu_name = '猪肉追溯码管理', parent_id = 10500, order_num = 2,
    path = 'trace-pork', component = 'djs-store/trace/index', query_param = '{"tab":"pork"}',
    menu_type = 'C', visible = '0'
WHERE menu_id = 10520;

-- ------------------------------------------------------------
-- 5. 板块⑤:退回管理（分组 M，reuse 空壳目录 10601）
--    其下：退回操作(10360) + 退回记录(10350)，均指单页 tab 容器
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '退回管理', parent_id = 10000, order_num = 5,
    menu_type = 'M', visible = '0'
WHERE menu_id = 10601;

UPDATE sys_menu
SET menu_name = '退回操作', parent_id = 10601, order_num = 1,
    path = 'return-op', component = 'djs-store/return/index', query_param = '{"tab":"operation"}',
    menu_type = 'C', visible = '0'
WHERE menu_id = 10360;

UPDATE sys_menu
SET menu_name = '退回记录', parent_id = 10601, order_num = 2,
    path = 'return-record', component = 'djs-store/return/index', query_param = '{"tab":"record"}',
    menu_type = 'C', visible = '0'
WHERE menu_id = 10350;

-- ------------------------------------------------------------
-- 6. role_menu 白名单（业务角色 + 超管），保险幂等补绑本轮恢复显示的菜单
--    范式抄 STORE-LEDGER-001-menu.sql：role_id NOT IN(1,101) AND del_flag='0' + 超管 role_id=1
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id IN (10400, 10210, 10600, 10500, 10501, 10520, 10601, 10360, 10350);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m
WHERE m.menu_id IN (10400, 10210, 10600, 10500, 10501, 10520, 10601, 10360, 10350);
