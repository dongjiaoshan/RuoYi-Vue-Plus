-- ============================================================
-- STORE-IA-4MENU-001  门店管理 admin 菜单收敛为「4 小菜单」最终态
-- ============================================================
-- 权威 IA（已与客户确认，最终态）：门店管理(父 10000) 下只保留 4 个直挂 C 小菜单：
--   1. 需求下单    order_num=1  component djs-store/demand/index   (reuse 10002)
--   2. 门店盘点    order_num=2  component djs-store/check/index    (reuse 10200)
--   3. 退回管理    order_num=3  component djs-store/return/index   (reuse 10350，页内 tab 退回操作|退回记录)
--   4. 追溯码管理  order_num=4  component djs-store/trace/index    (reuse 10501，页内 tab 果蔬|猪肉)
--
-- 4 个 C 全部直接挂 parent_id=10000（不再经分组目录 10001/10600/10601/10500）。
-- 被合并的子页降级为 F 权限并 re-parent 到对应 4 小菜单（10010→10002 / 10206→10200 /
--   10360→10350 / 10521→10501），保留 perms 行 + sys_role_menu 绑定，权限串不丢。
--
-- 隐藏清单（visible='1'，只隐藏不删表/不删行/不动 sys_role_menu）：
--   10001 需求管理目录 · 10100 门店经营(M)+10110/10120 · 10210 产品管理+10211/10212 ·
--   10300 门店分割+10301/10302/10303 · 10320 会员档案+10321~10327 · 10400 门店总览/dashboard ·
--   10500 追溯顶级目录 · 10520 猪肉独立菜单 · 10600 门店管理目录 · 10601 退回管理目录 ·
--   10360 退回操作独立菜单（降 F 见上）。
--
-- 锚点：以 menu_id 为准 UPDATE（不假设当前 name/parent/visible；前序迁移 V202606270100 等已做部分改名/re-parent）。
-- 仅 UPDATE sys_menu，不 INSERT 新 menu_id，不动 sys_role_menu。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 父菜单：门店管理（10000）显示并改名
-- ------------------------------------------------------------
UPDATE sys_menu SET menu_name = '门店管理', visible = '0' WHERE menu_id = 10000;

-- ------------------------------------------------------------
-- 2. 小菜单①：需求下单（reuse 10002）直挂 10000
--    把 10010（新增需求页，原 C 隐藏，列表内路由跳入）re-parent 到 10002，降 F 留权限串
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '需求下单', parent_id = 10000, order_num = 1,
    component = 'djs-store/demand/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10002;

UPDATE sys_menu
SET parent_id = 10002, menu_type = 'F', visible = '1', component = ''
WHERE menu_id = 10010;

-- ------------------------------------------------------------
-- 3. 小菜单②：门店盘点（reuse 10200）直挂 10000
--    把 10206（当日盘点录入页，原 C）re-parent 到 10200，降 F 留权限串
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '门店盘点', parent_id = 10000, order_num = 2,
    component = 'djs-store/check/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10200;

UPDATE sys_menu
SET parent_id = 10200, menu_type = 'F', visible = '1', component = ''
WHERE menu_id = 10206;

-- ------------------------------------------------------------
-- 4. 小菜单③：退回管理（reuse 10350）直挂 10000（页内 tab：退回操作 | 退回记录）
--    把 10360（退回操作，原独立 C 菜单）降 F + re-parent 到 10350，并入本页 tab，权限串保留
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '退回管理', parent_id = 10000, order_num = 3,
    component = 'djs-store/return/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10350;

UPDATE sys_menu
SET parent_id = 10350, menu_type = 'F', visible = '1', component = ''
WHERE menu_id = 10360;

-- ------------------------------------------------------------
-- 5. 小菜单④：追溯码管理（reuse 10501）直挂 10000（页内 tab：果蔬追溯 | 猪肉追溯）
--    component 收敛为 djs-store/trace/index（单页 tab 容器）；
--    把 10521（追溯码打印 F，原挂猪肉独立菜单 10520）re-parent 到 10501，权限串保留
-- ------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '追溯码管理', parent_id = 10000, order_num = 4,
    component = 'djs-store/trace/index', menu_type = 'C', visible = '0'
WHERE menu_id = 10501;

UPDATE sys_menu
SET parent_id = 10501, menu_type = 'F', visible = '1', component = ''
WHERE menu_id = 10521;

-- ------------------------------------------------------------
-- 6. 隐藏原型外 / 被合并的目录与菜单（visible='1'；保留表/行/绑定，可逆）
-- ------------------------------------------------------------
-- 需求管理目录（10002 已直挂 10000，目录空壳隐藏）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10001;

-- 门店经营 10100 + 产品关联 10110 + 经营明细 10120
UPDATE sys_menu SET visible = '1' WHERE menu_id IN (10100, 10110, 10120);

-- 产品管理 10210 + 产品新增 10211 + 产品修改 10212
UPDATE sys_menu SET visible = '1' WHERE menu_id IN (10210, 10211, 10212);

-- 门店分割 10300 + 分割查询/录入/导出 10301/10302/10303
UPDATE sys_menu SET visible = '1' WHERE menu_id IN (10300, 10301, 10302, 10303);

-- 会员档案 10320 及其全部子（10321~10327）
UPDATE sys_menu SET visible = '1' WHERE menu_id BETWEEN 10320 AND 10327;

-- 门店总览 / dashboard 10400（V202606270100 曾撤隐藏，本轮重新隐藏）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10400;

-- 追溯顶级目录 10500（10501 已直挂 10000，目录空壳隐藏）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10500;

-- 猪肉独立菜单 10520（猪肉追溯并入 10501 页内 tab，10521 已 re-parent 到 10501）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10520;

-- 门店管理目录 10600（4 个 C 已直挂 10000，分组目录隐藏）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10600;

-- 退回管理目录 10601（10350 已直挂 10000，分组目录隐藏）
UPDATE sys_menu SET visible = '1' WHERE menu_id = 10601;
