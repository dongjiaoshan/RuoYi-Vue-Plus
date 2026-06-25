-- ============================================================
-- DJS-FIX-WMS-GIFT-PACK-001  礼盒打包独立 C 页面菜单（需求 B）
-- ============================================================
-- 背景：礼盒打包原仅以按钮权限存在（9239 F：perms 'djs:warehouse:packEntry:gift'，
--   挂于「其他产品打包管理」9234 下），无独立页面入口。需求 B 要求礼盒打包独立成
--   一个 C 页面菜单，与肉品 9233 / 果蔬 9236 / 其他 9234 同级（生产管理 9300 直挂叶子）。
-- 处理：新增 C 菜单「礼盒打包管理」9249（9230-9248 段已占满，9249 为下一空闲），
--   parent 9300（生产管理；V202606291200 已把 5 录入叶子 reparent 到 9300，删空壳 9232），
--   perms 复用 9239 同串 'djs:warehouse:packEntry:gift'，order_num 排在其他产品打包之后。
--   9239 F 按钮权限保留不动（仍挂 9234；perm 与本 C 菜单同串，权限语义一致）。
-- 权威源：doc/origin/prototype/admin/_real_screenshots/origin_screenshots/仓库管理/生产管理
-- 范围：只动 sys_menu（新增 1 C 菜单）+ sys_role_menu（授权）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。ruoyi visible：'0'=显示 / '1'=隐藏。
-- ★ 应用后用户需重新登录 / 前端刷新 getRouters 才能看到新菜单（菜单树非 redis dict 缓存，无需 flush）。
-- 幂等：INSERT IGNORE + role_menu INSERT IGNORE，重复跑等价。
-- ============================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    -- 礼盒打包管理（独立 C 页面，挂生产管理 9300，order 排在 其他产品打包管理 9234 之后）
    (9249, '礼盒打包管理', 9300, 6, 'packEntry/gift', 'djs-warehouse/production/packEntry/gift/index', '',
     1, 0, 'C', '0', '0', 'djs:warehouse:packEntry:gift', 'box', 1, NOW(), 'DJS-FIX-WMS-GIFT-PACK-001 礼盒打包独立页面');

-- ------------------------------------------------------------
-- role_menu 授权（同 9234 其他产品打包管理范式：保留 superadmin/system_admin 通配，
-- 其余未删角色 boss/manager/warehouse_admin 等全量授）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0'
  AND m.menu_id = 9249;
