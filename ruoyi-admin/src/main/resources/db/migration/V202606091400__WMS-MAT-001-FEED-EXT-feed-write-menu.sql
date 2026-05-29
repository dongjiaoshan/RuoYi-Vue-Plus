-- WMS-MAT-001-FEED-EXT 饲料原料 mp 真录入闭环（落实 D9X MP-002 占位）
--
-- 背景：D9X MP-002 把 mp 端"采食原料领用"卡片切到饲料专属页
-- pages/breed/feed/index.vue（2 tab：饲料原料库 / 采食领用记录），但顶部"领用"
-- 按钮仍是占位 toast"敬请期待"。本 ticket 接通真录入闭环：
--   - mp 端新建 pages/breed/feed/issue/index.vue（复用 MatReturnLossForm matType='feed'）
--   - 后端复用现有 AppletMatFlowController.pick/return/loss（matType='feed' 已支持）
--   - 本 SQL 给饲料领用 / 退回 / 损耗 3 个写操作 seed 权限菜单
--
-- 菜单段：7362-7364（CLAUDE.md §6 #6 养殖域 7000-7999；7361 已被 D9X MP-002 占
-- 用作饲料列表 list 权限；本 ticket 续用 7362-7364 给 pick/return/loss 3 写）。
--
-- 注：AppletMatFlowController.pick/return/loss 当前用 @SaCheckLogin（D9 WMS-MAT-001
-- 范式），未接 @SaCheckPermission；本 SQL 灌入的 3 个权限 perm code 仅作为权限管理
-- 面板的展示性占位 + 后续 V1.5 切换 @SaCheckPermission 时白名单已就绪。当前接口
-- 鉴权语义：登录即可调用，不依赖这 3 个 perm。
--
-- 角色绑定范式：同 D9X MP-002 / D9-hotfix-purchase-in-menu 白名单（role_id NOT IN
-- (1=超管自动全权 / 101=system_admin 仅系统管理) AND del_flag=0），覆盖
-- breed_worker / vet / boss / manager / 其他 mp 业务角色。

-- ============================================================
-- 1. 3 个 mp 饲料写操作权限菜单（F 按钮型，visible='1' 隐藏；parent_id=0 独立挂根）
-- ============================================================
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
  (7362, 'mp 饲料领用', 0, 22, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:feed:pick', '#', 1, NOW(),
   'WMS-MAT-001-FEED-EXT AppletMatFlowController POST /applet/warehouse/mat/pick (matType=feed)'),
  (7363, 'mp 饲料退回', 0, 23, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:feed:return', '#', 1, NOW(),
   'WMS-MAT-001-FEED-EXT AppletMatFlowController POST /applet/warehouse/mat/return (matType=feed)'),
  (7364, 'mp 饲料损耗', 0, 24, '', '', '',
   1, 0, 'F', '1', '0',
   'djs:applet:breed:feed:loss', '#', 1, NOW(),
   'WMS-MAT-001-FEED-EXT AppletMatFlowController POST /applet/warehouse/mat/loss (matType=feed)');

-- ============================================================
-- 2. 角色白名单（mp 真实使用角色全勾选；admin role_id=1 自带全权不需显式插）
-- ============================================================
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
CROSS JOIN (
    SELECT 7362 AS menu_id
    UNION ALL SELECT 7363
    UNION ALL SELECT 7364
) m
WHERE r.role_id NOT IN (1, 101)
  AND r.del_flag = '0';
