-- V202608161000__STORE-MEMBER-REMOVE-001-drop-menu.sql
-- 门店无会员系统（Kevin 2026 拍板）：删会员相关逻辑后，删会员菜单 + 权限。
-- 后端 store/member 模块、看板会员 KPI、前端会员页/mp 会员页均已删除。
-- t_store_member / t_store_member_consumption 表保留（空表，不 DROP）；StoreReturn.member_id 列保留（无害）。

DELETE FROM sys_role_menu WHERE menu_id IN (SELECT menu_id FROM sys_menu WHERE menu_id=10320 OR parent_id=10320 OR perms LIKE 'djs:store:member%');
DELETE FROM sys_menu WHERE menu_id=10320 OR parent_id=10320 OR perms LIKE 'djs:store:member%';
