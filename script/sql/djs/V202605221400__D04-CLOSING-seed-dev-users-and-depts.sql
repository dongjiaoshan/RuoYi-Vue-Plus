-- D04 closing — dev / 联调用户 + 部门 seed
-- 触发：D04 MIN-INFRA-003 raise（通讯录端点 SELECT sys_user 仅 admin 1 行，dev 联调全空，UI 验证无数据）
-- 决策：Kevin closing 拍板 — D04 当晚加 SQL seed（5-8 用户覆盖 4 板块角色 + 2-3 部门）
--
-- 覆盖：
--   - 4 部门：200 养殖部 / 201 种植部 / 202 仓库部 / 203 门店部（挂 XXX科技 100 下）+ 204 综合管理部
--   - 8 用户：每板块至少 1 个管理员 + 工人，含 boss / vet 角色
--   - 全部 tenant_id=1001 + farm_id=1001 + current_farm_id=1001（V1 单农场）
--   - 密码统一 admin123（BCrypt 同 admin user）
--
-- 注意：user_id 9100-9107 业务角色用户；mock LoginUser 9001 (user_name=dev) 由后续 patch
-- V202605222100__D04-TH06-seed-mock-dev-user.sql 单独 seed，对齐 AppletAuthController#mockUserId

-- ----------------------------------------------------------------------
-- 1) 部门 seed
-- ----------------------------------------------------------------------

INSERT INTO sys_dept (dept_id, parent_id, ancestors, dept_name, order_num, leader, phone, email, status, del_flag, create_by, create_time, tenant_id)
VALUES
  (200, 100, '0,100',        '东角山-养殖部',     1, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (201, 100, '0,100',        '东角山-种植部',     2, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (202, 100, '0,100',        '东角山-仓库部',     3, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (203, 100, '0,100',        '东角山-门店部',     4, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001'),
  (204, 100, '0,100',        '东角山-综合管理部', 5, NULL, NULL, NULL, '0', '0', 1, NOW(), '1001')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

-- ----------------------------------------------------------------------
-- 2) 用户 seed（密码统一 admin123，BCrypt hash 同 admin user）
-- ----------------------------------------------------------------------

INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark)
VALUES
  (9100, '1001', 200, 'dev_breed_mgr',     '李养殖（养殖管理员）', 'sys_user', 'breed_mgr@dongjiaoshan.dev',    '13800009100', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 养殖管理员'),
  (9101, '1001', 200, 'dev_breed_worker',  '张三（养殖工人）',     'sys_user', 'breed_worker@dongjiaoshan.dev', '13800009101', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 养殖工人'),
  (9102, '1001', 200, 'dev_vet',           '王兽医（兽医）',       'sys_user', 'vet@dongjiaoshan.dev',          '13800009102', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 兽医'),
  (9103, '1001', 201, 'dev_plant_mgr',     '陈种植（种植管理员）', 'sys_user', 'plant_mgr@dongjiaoshan.dev',    '13800009103', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 种植管理员'),
  (9104, '1001', 202, 'dev_warehouse_mgr', '赵仓库（仓库管理员）', 'sys_user', 'warehouse_mgr@dongjiaoshan.dev','13800009104', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 仓库管理员'),
  (9105, '1001', 203, 'dev_store_mgr',     '钱门店（门店管理员）', 'sys_user', 'store_mgr@dongjiaoshan.dev',    '13800009105', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 门店管理员'),
  (9106, '1001', 203, 'dev_store_clerk',   '孙小妹（门店店员）',   'sys_user', 'store_clerk@dongjiaoshan.dev',  '13800009106', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 门店店员'),
  (9107, '1001', 204, 'dev_boss',          '老板（dev 测试）',     'sys_user', 'boss@dongjiaoshan.dev',         '13800009107', '0', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D04 dev seed - 老板')
ON DUPLICATE KEY UPDATE nick_name = VALUES(nick_name), dept_id = VALUES(dept_id);

-- ----------------------------------------------------------------------
-- 3) 用户 → 角色绑定
-- ----------------------------------------------------------------------

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (9100, 104),  -- breed_admin
  (9101, 108),  -- breed_worker
  (9102, 109),  -- vet
  (9103, 105),  -- plant_admin
  (9104, 106),  -- warehouse_admin
  (9105, 107),  -- store_admin
  (9106, 112),  -- store_clerk
  (9107, 102)   -- boss
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ----------------------------------------------------------------------
-- 验证
-- ----------------------------------------------------------------------
-- SELECT COUNT(*) FROM sys_user WHERE user_id BETWEEN 9100 AND 9107 AND del_flag='0';
-- 期望：8
-- SELECT COUNT(*) FROM sys_dept WHERE dept_id BETWEEN 200 AND 204 AND del_flag='0';
-- 期望：5
-- SELECT COUNT(*) FROM sys_user_role WHERE user_id BETWEEN 9100 AND 9107;
-- 期望：8
