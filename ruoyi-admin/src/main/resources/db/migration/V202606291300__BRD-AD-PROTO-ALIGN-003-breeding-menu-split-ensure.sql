-- ============================================================
-- BRD-AD-PROTO-ALIGN-003  确保育种配置 7010 = M 目录 + 4 子菜单（修「育种配置管理」点进空白）
-- ============================================================
-- 症状：点「育种配置管理」进 /djs-breed/breeding-config 空白页。
-- 根因：D05-HOTFIX（把 7010 从 C 单页拆成 M 目录 + 7016-7019 四子菜单）版本号
--       V202605281100 正好是 Flyway baseline，baseline 及更早迁移不执行 ——
--       故本地 DB 里 7010 可能仍是旧 C 单页（component=djs-breed/breeding-config/index，
--       该 vue 已按原型对齐删除）→ 空白。
-- 本文件幂等地强制最终态：7010=M 目录；7016-7019 存在 / 可见 / 名称对齐原型 / 继承 7010 角色绑定。
-- 跑完 `bash script/sql/djs/_post-init.sh` flush 菜单缓存 + 重新登录刷新。
-- ============================================================

SET NAMES utf8mb4;

-- 1. 7010 确保为 M 目录（清空 component/perms，旧 C 单页的组件指向已删 index.vue）
UPDATE sys_menu
   SET menu_type = 'M', component = '', perms = '', icon = 'tree',
       path = 'breeding-config', menu_name = '育种配置管理'
 WHERE menu_id = 7010;

-- 2. 确保 4 个 C 子菜单存在（缺则建）
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7016, '品种信息管理',     7010, 1, 'breed-strain',      'djs-breed/breeding-config/strain',      '', 1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'PROTO-ALIGN-003 ensure'),
  (7017, '品系信息管理',     7010, 2, 'breed-line',        'djs-breed/breeding-config/line',        '', 1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'PROTO-ALIGN-003 ensure'),
  (7018, '品种配种信息管理', 7010, 3, 'breed-mate-strain', 'djs-breed/breeding-config/mate-strain', '', 1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'PROTO-ALIGN-003 ensure'),
  (7019, '品系配种信息管理', 7010, 4, 'breed-mate-line',   'djs-breed/breeding-config/mate-line',   '', 1, 0, 'C', '0', '0', 'djs:breed:breeding:list', 'list', 1, NOW(), 'PROTO-ALIGN-003 ensure');

-- 3. 若 4 子菜单已存在但属性是旧值：纠正 parent / 类型 / 可见 / 名称 / 路由 / 组件
UPDATE sys_menu
   SET parent_id = 7010, menu_type = 'C', visible = '0', status = '0', perms = 'djs:breed:breeding:list',
       menu_name = CASE menu_id
           WHEN 7016 THEN '品种信息管理' WHEN 7017 THEN '品系信息管理'
           WHEN 7018 THEN '品种配种信息管理' WHEN 7019 THEN '品系配种信息管理' END,
       path = CASE menu_id
           WHEN 7016 THEN 'breed-strain' WHEN 7017 THEN 'breed-line'
           WHEN 7018 THEN 'breed-mate-strain' WHEN 7019 THEN 'breed-mate-line' END,
       component = CASE menu_id
           WHEN 7016 THEN 'djs-breed/breeding-config/strain' WHEN 7017 THEN 'djs-breed/breeding-config/line'
           WHEN 7018 THEN 'djs-breed/breeding-config/mate-strain' WHEN 7019 THEN 'djs-breed/breeding-config/mate-line' END
 WHERE menu_id IN (7016, 7017, 7018, 7019);

-- 4. 4 子菜单继承父 7010 的所有角色绑定（凡能看 7010 的角色都能看 4 子菜单）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, c.menu_id
FROM sys_role_menu rm
JOIN (SELECT 7016 AS menu_id UNION ALL SELECT 7017 UNION ALL SELECT 7018 UNION ALL SELECT 7019) c
WHERE rm.menu_id = 7010;
