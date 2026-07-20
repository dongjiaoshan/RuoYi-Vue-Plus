-- D06 HOTFIX: 合并 7 个事件独立菜单为 1 个「事件台账」菜单
--
-- 根因：D6 BRD-EVENT-002 / 004 各 subagent 写 menu seed 但未写 admin vue 页面，
--      点击菜单后路由进入空白页（component 路径指向不存在的文件）。
--      产品决策：admin 端不需要为每个事件做独立列表页，统一用 t_farm_status_record
--      流水台账查询（按 event_type 筛选切换），减菜单数量 + 减开发量。
--
-- 删除范围：menu_id 7120-7139（7 个 C 菜单 + 13 个 F 按钮 = 20 行）
--          7100 引种 / 7110 仔猪耳标 / 7140 生长记录保留（admin 已有 vue 页）
--
-- 新增：menu_id 7145「事件台账」C 菜单 + 7146 查询按钮

-- ------------------------------------------------------------
-- 1. 删菜单 + 按钮（7120-7139 段）
-- ------------------------------------------------------------
DELETE FROM sys_menu WHERE menu_id BETWEEN 7120 AND 7139;

-- ------------------------------------------------------------
-- 2. 加「事件台账」菜单
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7145, '事件台账', 7000, 4, 'event-status-record', 'djs-breed/event/status-record/index', '',
   1, 0, 'C', '0', '0',
   'djs:breed:pig:query', 'log', 1, NOW(), 'D06 HOTFIX 合并事件菜单'),

  (7146, '台账查询', 7145, 1, '', '', '',
   1, 0, 'F', '0', '0',
   'djs:breed:pig:query', '#', 1, NOW(), 'D06 HOTFIX');
