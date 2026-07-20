-- ============================================================
-- DJS-FIX-WMS-PROD-MENU  生产管理菜单扁平化（去掉「打包录入」包裹层）
-- ============================================================
-- 背景：原型 IA「生产管理」直接挂 5 个录入叶子，无中间「打包录入」目录：
--     分割白条领用 / 白条分割管理 / 肉品打包管理 / 果蔬打包管理 / 其他产品打包管理
--   现状（A5 migration）把这 5 叶子塞进 M 目录「打包录入」(9232) 下，多一层包裹，
--   且 URL 出现重复段 wh-production/packEntry/packEntry/meat。Kevin 2026-06-12 拍板：
--   去掉「打包录入」包裹，5 叶子直挂生产管理 9300，命名逐字对齐原型。
-- 权威源：doc/origin/prototype/admin/_real_screenshots/origin_screenshots/仓库管理/生产管理
--   （screenshot 侧边栏 = 生产管理 5 直接子项；目录树 = 单层 leaf）。
-- 范围：只动 sys_menu（reparent / rename / order / 删空壳目录）+ sys_role_menu 清理。
--   不改 component / path / perms（component 仍 djs-warehouse/production/packEntry/<x>/index；
--   path 不带 packEntry 前缀重复——叶子原 path 已是 packEntry/<x>，reparent 到 9300 后
--   完整路由 = wh-production/packEntry/<x>，去掉了 9232 那一段重复）。
-- 表单 form factor 保持桌面表单（Kevin「功能对齐优先，保持桌面表单」拍板），本轮不动页面。
-- menu_id 分段：仓库 9000-9999 / 生产管理子段不变。
-- 幂等：reparent / rename / order 全 UPDATE；删壳目录 DELETE（重复跑等价）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。ruoyi visible：'0'=显示 / '1'=隐藏。
-- ★ 应用后需重新登录 / 前端刷新 getRouters 才能看到新菜单（菜单树非 redis dict 缓存）。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- A. reparent + reorder（5 录入叶子 9232 → 生产管理 9300，原型顺序）
-- ============================================================
UPDATE sys_menu SET parent_id = 9300, order_num = 1 WHERE menu_id = 9238;  -- 分割白条领用
UPDATE sys_menu SET parent_id = 9300, order_num = 2 WHERE menu_id = 9237;  -- 白条分割管理
UPDATE sys_menu SET parent_id = 9300, order_num = 3 WHERE menu_id = 9233;  -- 肉品打包管理
UPDATE sys_menu SET parent_id = 9300, order_num = 4 WHERE menu_id = 9236;  -- 果蔬打包管理
UPDATE sys_menu SET parent_id = 9300, order_num = 5 WHERE menu_id = 9234;  -- 其他产品打包管理

-- ============================================================
-- B. rename（命名逐字对齐原型；9237/9238 已对齐不动）
-- ============================================================
UPDATE sys_menu SET menu_name = '肉品打包管理'     WHERE menu_id = 9233;  -- 原「肉品打包」
UPDATE sys_menu SET menu_name = '果蔬打包管理'     WHERE menu_id = 9236;  -- 原「果蔬打包」
UPDATE sys_menu SET menu_name = '其他产品打包管理' WHERE menu_id = 9234;  -- 原「其他产品打包」

-- ============================================================
-- C. 删除空壳目录「打包录入」9232（原型无此层；5 子已 reparent 出去）
--    9239 礼盒打包权限 F 是 9234 的子，不受影响。
-- ============================================================
DELETE FROM sys_role_menu WHERE menu_id = 9232;
DELETE FROM sys_menu      WHERE menu_id = 9232;
