-- D12X 测试期发现：V202605221400 dev seed 建了 dev_breed_worker（养殖工人），
-- 漏建仓库工人 / 种植工人 → mp EmployeePicker(role='warehouse_worker' / 'plant_worker')
-- 在燎毛入库「入库人」/ 农事录入「录入人」等弹层恒空「暂无员工」。
-- 补齐两个 worker 级 dev 账号，与 dev_breed_worker 同口径（同密码 hash，可登录）。
-- 幂等：INSERT IGNORE（当前 dev 库已手工补过、重建库时才真插入，两种情况都安全）。

INSERT IGNORE INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark) VALUES
  (9108, '1001', 202, 'dev_warehouse_worker', '吴仓库（仓库工人）', 'sys_user', 'warehouse_worker@dongjiaoshan.dev', '13800009108', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D12X dev seed - 仓库工人'),
  (9109, '1001', 201, 'dev_plant_worker',     '周种植（种植工人）', 'sys_user', 'plant_worker@dongjiaoshan.dev',     '13800009109', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), 'D12X dev seed - 种植工人');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
  (9108, 110),  -- warehouse_worker
  (9109, 111);  -- plant_worker
