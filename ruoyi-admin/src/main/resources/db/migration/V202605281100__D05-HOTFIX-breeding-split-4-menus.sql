-- ============================================================
-- D05-HOTFIX BRD-MD-001 育种配置 — 顶层菜单按 4 个独立子菜单拆开
--
-- 拆分前：7010 是 C 菜单（单页 4 tab：品种 / 品种配种 / 品系 / 品系配种）
-- 拆分后：7010 改 M 目录，下挂 4 个 C 菜单：
--   7016 品种管理      breed-strain      djs-breed/breeding-config/strain
--   7017 品系管理      breed-line        djs-breed/breeding-config/line
--   7018 品种配种      breed-mate-strain djs-breed/breeding-config/mate-strain
--   7019 品系配种      breed-mate-line   djs-breed/breeding-config/mate-line
--
-- 7011-7015 按钮权限（list/add/edit/remove/export）保留 parent_id=7010 不动 —
-- v-hasPermi 看 perms 串 djs:breed:breeding:{action}，不依赖按钮归属哪个 C 菜单。
-- ============================================================

SET NAMES utf8mb4;

-- 1) 7010 由 C 菜单升级为 M 目录
UPDATE sys_menu
   SET menu_type = 'M',
       component = '',
       perms     = '',
       icon      = 'tree'
 WHERE menu_id = 7010;

-- 2) 新增 4 个 C 菜单（独立路由 + 独立组件，共用一组 perms）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7016, '品种管理', 7010, 1, 'breed-strain',      'djs-breed/breeding-config/strain',      '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7017, '品系管理', 7010, 2, 'breed-line',        'djs-breed/breeding-config/line',        '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7018, '品种配种', 7010, 3, 'breed-mate-strain', 'djs-breed/breeding-config/mate-strain', '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus'),

  (7019, '品系配种', 7010, 4, 'breed-mate-line',   'djs-breed/breeding-config/mate-line',   '',
   1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'D05-HOTFIX BRD-MD-001 split-4-menus');
