-- D04 testing-human #6 — 补 user_id=9001 dev 用户（mock LoginUser 对齐）
-- 触发：mock dev/dev123 login 颁发 mock-token-9001（mockUserId=9001L，[AppletAuthController.java:112]），
--       但 D04 closing seed 当时为"避开 mock LoginUser 9001"用了 9100-9107 段，DB 无 user_id=9001 行。
--       小程序"我的"页 GET /applet/user/me 走 SELECT sys_user WHERE user_id=9001 → 空 → R.fail(404)
--       → 前端"加载失败"。
-- 决策：补一行 user_id=9001 user_name=dev，与 mock LoginUser 对齐；保留 9100-9107 业务角色 seed 不动。

INSERT INTO sys_user (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, password, status, del_flag, farm_id, current_farm_id, create_by, create_time, remark)
VALUES
  (9001, '1001', 204, 'dev', 'dev 员工', 'sys_user', 'dev@dongjiaoshan.dev', '13800009001', '0',
   '$2a$10$7JB720yubVSZvuENVucfeurUyOJyKdyXBdC0HyrCl1tT5ZUmgo7Wm',
   '0', '0', '1001', '1001', 1, NOW(), 'D04 TH#6 - mock LoginUser 对齐（dev/dev123）')
ON DUPLICATE KEY UPDATE nick_name = VALUES(nick_name), dept_id = VALUES(dept_id), status = '0', del_flag = '0';

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (9001, 101)  -- admin 角色（mock LoginUser.rolePermission 默认 *:*:*，DB 给个 admin 角色对齐）
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 验证：
-- SELECT user_id, user_name, nick_name, dept_id FROM sys_user WHERE user_id = 9001;
-- 期望：1 行 dev / dev 员工 / 204
