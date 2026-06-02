-- STR-MEMBER-001 会员档案 + 手录消费 菜单 seed + MEMBER_NO 编码 pattern 调整
-- menu_id 段 10320-10327（门店收尾 10300-10399 band，与 STR-SPLIT 10300-10303 / STR-RETURN 分段不重叠）
-- 父菜单 10000 门店销售 已由 SYS-AUTH-001 seed；ADR-0006 菜单分治
SET NAMES utf8mb4;

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (10320, '会员档案', 10000, 4, 'member', 'djs-store/member/index', '',
   1, 0, 'C', '0', '0', 'djs:store:member:list', 'peoples', 1, NOW(), 'STR-MEMBER-001'),
  (10321, '会员查询',   10320, 1, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:query',         '#', 1, NOW(), 'STR-MEMBER-001'),
  (10322, '会员新增',   10320, 2, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:add',           '#', 1, NOW(), 'STR-MEMBER-001'),
  (10323, '会员编辑',   10320, 3, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:edit',          '#', 1, NOW(), 'STR-MEMBER-001'),
  (10324, '会员删除',   10320, 4, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:remove',        '#', 1, NOW(), 'STR-MEMBER-001'),
  (10325, '会员导出',   10320, 5, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:export',        '#', 1, NOW(), 'STR-MEMBER-001'),
  (10326, '消费记录查询', 10320, 6, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:consume:list', '#', 1, NOW(), 'STR-MEMBER-001'),
  (10327, '消费记录录入', 10320, 7, '', '', '', 1, 0, 'F', '0', '0', 'djs:store:member:consume:add',  '#', 1, NOW(), 'STR-MEMBER-001');

-- role_menu 白名单（boss 102 / manager 103 / store_admin 107 / store_clerk 112 命中；NOT IN(1,101) 覆盖全部业务角色）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE r.role_id NOT IN (1, 101) AND r.del_flag = '0' AND m.menu_id BETWEEN 10320 AND 10349;
-- 超级管理员 role_id=1 全绑
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id FROM sys_menu m WHERE m.menu_id BETWEEN 10320 AND 10349;

-- MEMBER_NO 编码 pattern：M{seq4}（生成 M0001，人员编号语义残留）→ 1{seq4}（生成 10001 起，与 doc/10+11 + 实际 DDL 注释「会员编号 10001 起」对齐）
-- openQuestion #1 默认方案 a：pattern 前缀凑 10001 段（生成器无 seq_start，冷启动恒从 1 起算），10001-19999 段够 V1 会员用
-- 可逆：Kevin 若选 b（保留 M0001）closing 删本 UPDATE + 回修 doc
UPDATE t_md_biz_code_rule SET pattern = '1{seq4}', prefix = '1', seq_length = 4 WHERE code_type = 'MEMBER_NO';
