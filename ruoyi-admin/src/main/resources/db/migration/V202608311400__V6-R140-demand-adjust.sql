-- ============================================================
-- V6-R140 需求调整管理：调整留痕表 + 系统管理→数据管理下的新菜单
-- ============================================================
-- 甲方原话 6 条：数据管理下新增菜单【需求调整管理】、页面内容与仓库管理→需求管理一致、
-- 查看需求详情列表的操作列新增「调整」、点开弹框改门店需求量 + 填备注、
-- 发货之后不再显示调整、数据库新增记录表留痕（需求日期 / 需求门店 / 需求产品编码 /
-- 原始需求量 / 调整后需求量 / 备注 / 调整人 / 调整时间）。
--
-- 号段：系统底座 5000-5999，数据管理目录 5700 已建（V202608300600），本次取 5703 / 5704
--       （5703-5760 当前无任何 sys_menu 行占用，不会继承历史授权）。
-- 版本号：目标库 flyway max = 202608311200，本文件 202608311400 已留足 1 小时 buffer。
-- 无字典变更，无需 flush redis。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 调整留痕表
--    甲方点名的 8 项全部落列；门店名 / 产品名 / 产品编码 取调整当时的**快照**而不是留 FK 现查 ——
--    留痕表的用途就是「当时是什么样」，门店改名或产品下架之后回看不能跟着变。
--    demand_id / store_id / product_id 仍留着，供下钻回原单。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_warehouse_demand_adjust_record (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id     VARCHAR(20)   NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    demand_id     BIGINT        NOT NULL                 COMMENT 'FK → t_warehouse_demand_manage.id',
    demand_no     VARCHAR(32)   NULL                     COMMENT '需求单号（快照）',
    demand_date   DATE          NOT NULL                 COMMENT '需求日期（快照）',
    store_id      BIGINT        NULL                     COMMENT '需求门店 FK → t_md_store.id',
    store_name    VARCHAR(100)  NULL                     COMMENT '需求门店名称（快照）',
    product_id    BIGINT        NOT NULL                 COMMENT '需求产品 FK → t_warehouse_product_info.id',
    product_code  VARCHAR(64)   NULL                     COMMENT '需求产品编码（快照，= product_info.product_id 业务码）',
    product_name  VARCHAR(128)  NULL                     COMMENT '需求产品名称（快照）',
    old_quantity  DECIMAL(12,3) NOT NULL                 COMMENT '原始需求量',
    new_quantity  DECIMAL(12,3) NOT NULL                 COMMENT '调整后需求量',
    adjust_remark VARCHAR(500)  NULL                     COMMENT '调整备注',
    adjuster_id   BIGINT        NULL                     COMMENT '调整人 FK → sys_user.user_id',
    adjust_time   DATETIME      NOT NULL                 COMMENT '调整时间',
    create_dept   BIGINT        NULL                     COMMENT '创建部门',
    create_by     BIGINT        NULL                     COMMENT '创建者',
    create_time   DATETIME      NULL                     COMMENT '创建时间',
    update_by     BIGINT        NULL                     COMMENT '更新者',
    update_time   DATETIME      NULL                     COMMENT '更新时间',
    del_flag      CHAR(1)       DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    KEY idx_demand (tenant_id, demand_id),
    KEY idx_demand_date (tenant_id, demand_date),
    KEY idx_store (tenant_id, store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求量调整留痕表（V6-R140）';

-- ------------------------------------------------------------
-- 2) 菜单：系统管理 → 数据管理 → 需求调整管理（+「调整」按钮权限）
--    页面复用需求管理本体（djs-warehouse/demandAdjust/index 内部套 demand/index.vue 的 adjust 模式），
--    因此列表数据仍走 djs:warehouse:demand:list —— 授本菜单的角色必须同时有需求管理的读权限。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5703, '需求调整管理', 5700, 2, 'demand-adjust', 'djs-warehouse/demandAdjust/index', '',
     1, 0, 'C', '0', '0',
     'djs:warehouse:demandAdjust:list', 'form', 1, NOW(), 'V6-R140'),
    (5704, '调整需求量', 5703, 1, '', '', '', 1, 0, 'F', '0', '0',
     'djs:warehouse:demandAdjust:adjust', '#', 1, NOW(), 'V6-R140');

-- ------------------------------------------------------------
-- 3) 角色授权：派生自**父目录「数据管理」(5700) 本身**的角色集合（不写死 role_id），再显式补超级管理员。
--    ADR-0020：授权要覆盖整条子树；父目录 5700 早已授权，此处只补两个新节点。
--
--    为什么派生源是 5700 而不是同目录的兄弟菜单 5701（燎毛间产品重量调整）：
--    「能看见数据管理这个目录的人，就该看见它下面的功能」是稳定的语义；
--    挂到兄弟菜单上则是偶然耦合 —— 哪天 5701 被停用或改授权，这个菜单会跟着一起没，
--    而两者业务上毫无关系。当前两库的实际角色集相同（超管 + 系统管理员），行为不变。
--
--    ⚠️ 本页的列表数据仍走 djs:warehouse:demand:list（页面复用需求管理本体），
--    所以被授权的角色**必须同时持有「需求管理」(menu 9040)**，否则菜单点得开、列表是空的。
--    上线后自查：
--      SELECT r.role_id, r.role_name, MAX(rm.menu_id=5703) 有调整页, MAX(rm.menu_id=9040) 有需求管理
--      FROM sys_role r JOIN sys_role_menu rm ON rm.role_id=r.role_id
--      WHERE r.del_flag='0' GROUP BY r.role_id, r.role_name HAVING 有调整页=1;
--    两列必须都是 1。
--
--    🔴 待甲方/Kevin 定：老板 / 管理人员 / 仓库管理员这三个真正在管需求的角色，目前看不到本菜单
--    —— 因为它按甲方要求挂在「系统管理」下，而他们没有系统管理目录的权限。要开给他们，
--    就得连「系统管理」顶层目录一起授权，那是权限结构层面的变更，不在本条范围内自行决定。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
FROM sys_role_menu rm
CROSS JOIN (SELECT 5703 AS menu_id UNION ALL SELECT 5704) m
WHERE rm.menu_id = 5700;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
VALUES (1, 5700), (1, 5703), (1, 5704);
