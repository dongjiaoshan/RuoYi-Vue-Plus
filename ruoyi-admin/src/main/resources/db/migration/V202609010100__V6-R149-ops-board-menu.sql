-- V6-R149 运营管理板块骨架
--
-- 甲方 2026-08-31：
--   1) 增加【运营管理】版块（与养殖/种植/仓库/门店/系统并列的第 6 个板块）
--   2) 运营管理下新增【农场信息】菜单
--   3) 农场信息下新增【果蔬上市计划】【育肥猪信息】【作物需求】三个功能
--
-- 本迁移只建 1) 2) 两层骨架；3) 的三个页面菜单由各自迁移建。
-- menu_id 号段划分（12000-12999 整段在 sys_menu 与 migration 目录均零占用，不会继承任何历史授权）：
--   12000        运营管理      M  parent=0        本迁移
--   12010        农场信息      M  parent=12000    本迁移
--   12020-12029  果蔬上市计划  C+F parent=12010   留给 row150
--   12030-12039  育肥猪信息    C+F parent=12010   留给 row151
--   12040-12049  作物需求      C+F parent=12010   留给 row152
--   12050-12999  运营域后续预留
--   注意：11000-11099 已被小程序权限树占用（11000-11073），不得复用。
--
-- 前端联动：plus-ui/src/views/index.vue 的 BOARD_CARDS 追加 menuPath='djs-ops' 的第 6 张卡；
--   卡片可见性由「用户是否拥有 djs-ops 顶级菜单」决定（ADR-0020，不看 role_key）。
--   row150/151/152 的页面 component 路径统一挂 'djs-ops/xxx/index'。
--
-- order_num=110：顶级目录现有 1(系统管理) / 50(通用主数据,隐藏) / 70(养殖) / 80(种植)
--   / 90(仓库) / 100(门店管理) / 900(小程序权限,隐藏)，110 排在门店管理之后、隐藏项之前。
--
-- 授权面：1 超级管理员 + 101 系统管理员 + 102 老板 + 103 管理人员。
--   102/103 对现有 5 个业务顶级目录全部有授权，运营管理是老板/管理层面板，同口径给。
--   ADR-0020：授权必须覆盖整条子树含父目录，否则子菜单成孤儿不显示。
--   row150/151/152 建页面菜单时各自补自己那几行的 sys_role_menu。
--
-- 生效：/getRouters 实时读库，重新登录即生效；sys_menu 不进 redis 字典缓存，无需 flush。
-- 幂等：sys_menu 走 ON DUPLICATE KEY UPDATE，sys_role_menu 走 INSERT IGNORE，可重复执行。

SET NAMES utf8mb4;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (12000, '运营管理', 0, 110, 'djs-ops', '', '',
     1, 0, 'M', '0', '0',
     '', 'chart', 1, NOW(), 'V6-R149 运营管理一级板块目录'),
    (12010, '农场信息', 12000, 1, 'farm-info', '', '',
     1, 0, 'M', '0', '0',
     '', 'documentation', 1, NOW(), 'V6-R149 农场信息二级目录（子页面 12020/12030/12040 见 row150/151/152）')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path      = VALUES(path),
    component = VALUES(component),
    menu_type = VALUES(menu_type),
    visible   = VALUES(visible),
    status    = VALUES(status),
    icon      = VALUES(icon);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 12000), (1, 12010),
    (101, 12000), (101, 12010),
    (102, 12000), (102, 12010),
    (103, 12000), (103, 12010);

-- 验收 query
--   SELECT menu_id, menu_name, parent_id, order_num, path, menu_type, visible
--     FROM sys_menu WHERE menu_id BETWEEN 12000 AND 12999 ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id IN (12000, 12010) GROUP BY menu_id;
