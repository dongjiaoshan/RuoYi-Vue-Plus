-- GRAY-PREP-001 灰度前置：停用全部 dev 测试账号 + 清掉超管默认演示信息
--
-- 背景：staging 即灰度环境（按准生产对待）。14 个 dev_* 测试账号的 BCrypt 真实密码全是 'dev123'
-- ——关掉 DJS_APPLET_AUTH_MOCK 并不足以挡住它们：AppletAuthController.login 在 mock 白名单
-- 未命中时会 fallthrough 到真实 employeePasswordLogin(sys_user + BCrypt)，照样登得进。
-- 故必须在账号层关闭。
--
-- 用软删（del_flag='2'）而非物理删除：这些账号在 t_warehouse_stock_flow(28 行) /
-- t_warehouse_pig_burn_record(2 行) 等表的 create_by 里被引用，物理删会让历史记录失去操作人。

-- 1. 停用 + 软删 14 个 dev 测试账号（保留 user_id=1 超管）。
--    三重关闭：status=1 停用 / del_flag=2 软删 / password 置不可逆值。
--    password 置成非 BCrypt 格式字符串（BCrypt.checkpw 对它恒 false 且不抛异常），
--    这样即使有人误把 del_flag 改回 0，dev123 也登不进去。
UPDATE sys_user
   SET status     = '1',
       del_flag   = '2',
       password   = 'DISABLED-GRAY-PREP-001',
       wx_openid  = NULL,
       update_time = NOW()
 WHERE user_id IN (9001, 9100, 9101, 9102, 9103, 9104, 9105,
                   9106, 9107, 9108, 9109, 9110,
                   2067887406297165825, 2069412410780385281);

-- 2. 超管去掉 ruoyi 自带演示信息（甲方一登录就会看到「疯狂的狮子Li」）。
--    手机号必须清空：小程序端「微信登录 → 授权手机号 → 匹配 sys_user.phonenumber」
--    是拿手机号直接换 token 的，留着 15888888888 这种公开占位号 = 谁都能绑成超管。
--    超管只从 admin 后台账号密码登录，不需要手机号。
UPDATE sys_user
   SET nick_name   = '系统管理员',
       phonenumber = NULL,
       email       = NULL,
       remark      = NULL,
       update_time = NOW()
 WHERE user_id = 1;
