-- FIX-DJS-PERM-MENU-009 · row17：mp 养殖板块 tab 显隐 collapse（养殖管理 + 疫苗药品）
--
-- 陷阱：养殖管理 / 疫苗药品 两个 mp tab 的显隐权限挂在独立叶子导航节点
--   11028 养殖管理·导航  perms=djs:mptab:breed:dashboard （挂组节点 11025 养殖管理 下）
--   11027 疫苗药品·导航  perms=djs:mptab:breed:med       （挂组节点 11024 疫苗药品 下）
-- 角色 menu_check_strictly=1（父子不联动）。admin 在角色树取消勾组节点「养殖管理」/「疫苗药品」时，
-- 子叶仍勾着 → 保存走 checkedKeys ∪ halfCheckedKeys（plus-ui role/index.vue），半选父节点连子叶一起写回
-- → 取消无效，tab 照显（此即 row17 现象）。
--
-- 修法：tab 显隐权限上移到组节点本身、删冗余子叶，使组节点成为无子的干净 toggle
-- → admin 取消勾组节点直接移除 tab 权限、隐藏对应 tab。
-- 组节点旧携的功能通配冗余、剥离零 API 影响（tab 显隐与数据授权解耦）：
--   养殖管理 11025 djs:breed:dashboard:* —— admin 养殖看板 7400 同授全部相关角色（101/102/103/104/108/109）
--   疫苗药品 11024 djs:breed:med*        —— admin 药品 7060/7070/7076/7085 同授全部相关角色
-- 现有 6 角色均双持（组节点 + 对应 admin 数据节点），本迁移不改任何角色的有效权限，仅改结构。
-- 部署后 mp 用户需重新登录（权限在登录时快照进 Sa-Token 会话，改角色树后同理）。

-- 1) 组节点承接 tab 导航权限（取代原子叶）
UPDATE sys_menu SET perms = 'djs:mptab:breed:dashboard' WHERE menu_id = 11025;
UPDATE sys_menu SET perms = 'djs:mptab:breed:med'       WHERE menu_id = 11024;

-- 2) 删冗余导航子叶 + 其角色授权（tab 访问已由组节点保留）
DELETE FROM sys_role_menu WHERE menu_id IN (11027, 11028);
DELETE FROM sys_menu      WHERE menu_id IN (11027, 11028);
