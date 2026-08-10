-- ============================================================================
-- SYS-AUTH-001 登录态时长
--
-- admin(plus-ui) 登录走 AuthController → PasswordAuthStrategy，token 两个时限直接取本表：
--   active_timeout = 「多久不发请求就掉线」。sa-token autoRenew 默认 true，
--                    每个带 token 的请求都会把它续满 → 滑动窗口，一直在用就一直不掉。
--   timeout        = 从登录那一刻起算的绝对上限，不续期，到点强制重登一次。
--
-- 取值：7 天滑动 + 30 天硬顶。连续 7 天没打开后台才需要重登；正常每天用的人 30 天重登一次。
--
-- pc  = admin 后台，本行是唯一对 admin 生效的配置。
-- mp  = 小程序。小程序的时限由 WechatLoginServiceImpl 的常量决定（30 天 / 7 天活跃），
--       不读本表；这里保持同值，只为「系统管理 → 客户端管理」页显示的数字不误导人。
-- app = android，本项目未使用，不动。
--
-- ⚠️ 跑完必须清 Redis 缓存 `global:sys_client`（SysClientServiceImpl @Cacheable TTL 30 天），
--    否则本次 UPDATE 最长 30 天不生效：bash ops/redis-flush-dict.sh {staging|prod}
-- ============================================================================
UPDATE sys_client
SET active_timeout = 604800,
    timeout        = 2592000
WHERE client_id IN ('e5cd7e4891bf95d1d19206ce24a7b32e', 'mp-applet-dongjiaoshan');
