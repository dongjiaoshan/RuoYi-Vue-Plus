-- ============================================================
-- 肉品打包管理「小屏测试」菜单（临时）
-- ------------------------------------------------------------
-- 背景：TSX-615 一体秤屏幕高度小，需做小屏适配（右台上移 / 卡片缩小一排多个 /
--   去 numpad / 秤重量做成可编辑输入框自动填入 / 去填入·归零·去皮）。
--   先建独立测试页迭代，验好再回灌到正式肉品打包页及其他页。
-- 菜单：与「肉品打包管理」9233 同级，挂 生产管理 9300；复用 packEntry:* 权限。
--   menu_id 9299（9230-9269 段已占满，取 9276-9299 未分配段的 9299）；is_cache=1 不缓存（测试页每次进重挂）。
-- 页面组件：djs-warehouse/production/packEntry/meatMini/index（SkuPackForm mini 模式）。
-- ============================================================

INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (9299, '肉品打包管理小屏测试', 9300, 99,
     'packEntry/meat-mini', 'djs-warehouse/production/packEntry/meatMini/index', '',
     1, 1, 'C', '0', '0',
     'djs:warehouse:packEntry:*', 'tool', 1, NOW(), '小屏适配测试页（临时，验好回灌正式页后可删）');

-- role_menu 授权：给除 superadmin(1)/租户管理员(101) 外所有角色（与 9233 同范式，谁看得到肉品打包谁看得到测试页）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 9299
FROM sys_role r
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
