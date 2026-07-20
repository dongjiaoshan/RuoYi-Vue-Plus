-- 种植部(dept_id=201)演示用：一个未分配任何班组的种植工人。
-- 班组「成员管理」候选列表 = 种植部内未入组的人员；此用户不写入 t_plant_work_people，
-- 故作为候选员工出现（用于演示候选列表非空）。
INSERT IGNORE INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark) VALUES
  (9110, '1001', 201, 'dev_plant_worker2', '李种植（种植工人）', 'sys_user', 'plant_worker2@dongjiaoshan.dev', '13800009110', '1', '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm', '0', '0', '1001', '1001', 1, NOW(), '种植工人（未分配班组）');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES
  (9110, 111);  -- plant_worker
