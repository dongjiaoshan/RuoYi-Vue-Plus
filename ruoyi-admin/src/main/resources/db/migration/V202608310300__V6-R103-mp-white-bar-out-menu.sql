-- ============================================================
-- V6 row103  mp「分拣发货 → 白条出库」入口权限
-- ============================================================
-- 甲方 2026-08-17：分拣发货四宫格「生产领用」之后新增第 5 格「白条出库」，
-- 页面数据与三条出库去向（分割车间 / 门店发货 / 仓库出库）与后台「白条出库领用」完全一致。
--
-- 后端只加 applet 入口（AppletBarOutController，薄委托到 admin 白条领用页复用的同一批 ServiceImpl），
-- 故本迁移只建权限节点，不改任何业务表。
--
-- 为什么权限节点建在 11042「分拣发货」下、且另开 djs:applet:warehouse:barOut 命名空间：
--   · 该卡片属于「分拣发货」tab，授权树里必须落在 11042 下，勾谁显谁；
--   · 复用既有 djs:applet:warehouse:pack*（11070 打包·包材）或 pigCut:*（11069 白条分割领用）
--     会让这张卡被「燎毛间」(11068) 的勾选项间接开关、取消勾选压不住
--     （permMatch 双向通配互相命中，FIX-DJS-PERM-MENU-008 同款坑）。
--   · 读写分两串（:list / :submit）由本节点一个通配 :* 统一授出，与 11064-11067 同形态。
--
-- 授权范围：与 11064「物资领用」（= 四宫格里的「生产领用」入口）完全对齐 ——
--   甲方要的就是「生产领用下面新增一格」，能看见生产领用的角色就该能看见白条出库。
--   即 101 系统管理员 / 102 老板 / 103 管理人员 / 106 仓库管理员 / 110 仓库工人。
--
-- 生效：PermissionService 颁 token 实时读 DB，mp 重新登录即生效；无需 flush redis。
-- 幂等：sys_menu 走 ON DUPLICATE KEY，sys_role_menu 走 INSERT IGNORE。
-- ============================================================

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param,
                      is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) VALUES
  (11073, '白条出库', 11042, 5, '', '', '', 1, 0, 'F', '1', '0', 'djs:applet:warehouse:barOut:*', '#', 1, NOW(),
   'V6-R103 mp 分拣发货 白条出库（列表/门店/来源 :list + 三去向提交 :submit）')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), parent_id = VALUES(parent_id),
                        perms = VALUES(perms), menu_type = VALUES(menu_type);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (101, 11073), (102, 11073), (103, 11073), (106, 11073), (110, 11073);
