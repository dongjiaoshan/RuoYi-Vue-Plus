-- ============================================================================
-- V6 需求和问题（测试环境）row152 + row153：作物需求（运营端提）/ 需求反馈（种植端回）
--
-- 甲方口径（一张表两个入口，共用 VO / 共用字典 / 共用端点）：
--   row152 运营管理 → 农场信息 → 作物需求
--     · 【新增需求】弹框：需求分类（字典下拉）/ 需求内容（多行）/ 图片（多张）
--     · 新建默认「待回复」；需求日期由服务端取当天（弹框里没有日期项）
--     · 操作列：查看详情 + 删除；删除只有创建人能删自己录入的需求（服务端校验 create_by）
--   row153 种植 → 种植管理 → 需求反馈
--     · 搜索条件 / 列表列与 row152 完全一致
--     · 操作列只有「回复」，无论什么状态都是回复；已回复的支持后续修改回复内容
--
-- 搜索条件（两端一致）：需求内容（模糊）/ 需求分类（字典下拉）/ 状态（全部|待回复|已回复）/ 需求日期范围
--
-- 状态存 key 不存中文：跟随全仓范式（ADR-0004 §2.2），VARCHAR 存字典 code，
--   走字典 djs_plant_demand_status（pending / replied）。
--
-- ⚠️ parent_id=12010「农场信息」由 V202609010100（row149）建，本文件不重复建。
--
-- ⚠️ 跑完刷 Redis 字典缓存（新增字典不刷则 admin 下拉 / dict-tag 空白）：
--      本地    bash script/sql/djs/_post-init.sh
--      staging bash ops/redis-flush-dict.sh staging --yes
-- ============================================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 需求表 t_plant_crop_demand
--
--    无业务唯一键 → 不建 UNIQUE。del_unique 仍必须存在：
--    DjsBaseServiceImpl.softDelete 恒执行 .set("del_unique", id)，列缺失直接 SQL 报错。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_plant_crop_demand (
    id              BIGINT        NOT NULL                   COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001'    COMMENT '租户/农场 ID',
    demand_date     DATE          NOT NULL                   COMMENT '需求日期（新增时服务端取当天，不由用户填）',
    demand_category VARCHAR(32)   NOT NULL                   COMMENT '需求分类（字典 djs_plant_demand_category：plant=种植需求 / new_crop=新作物需求）',
    demand_content  VARCHAR(1000) NOT NULL                   COMMENT '需求内容（多行文本）',
    demand_status   VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT '需求状态（字典 djs_plant_demand_status：pending=待回复 / replied=已回复）',
    image_oss_ids   VARCHAR(2048) NULL                       COMMENT '需求图片 OSS ossId 逗号分隔（多张，bizType=plant_demand）',
    reply_content   VARCHAR(1000) NULL                       COMMENT '回复内容（种植端填，已回复后可再改）',
    reply_time      DATETIME      NULL                       COMMENT '最后一次回复时间',
    reply_by        BIGINT        NULL                       COMMENT '回复人 sys_user.user_id',
    create_by       BIGINT        NULL                       COMMENT '创建人（= 提需求的人，删除权限判定依据）',
    create_time     DATETIME      NULL                       COMMENT '创建时间',
    update_by       BIGINT        NULL                       COMMENT '更新人',
    update_time     DATETIME      NULL                       COMMENT '更新时间',
    create_dept     BIGINT        NULL                       COMMENT '创建部门',
    del_flag        CHAR(1)       NOT NULL DEFAULT '0'       COMMENT '软删标记 0=正常 / 1=已删',
    del_unique      BIGINT        NOT NULL DEFAULT 0         COMMENT '软删唯一辅助列（删除时写 id）',
    PRIMARY KEY (id),
    KEY idx_demand_status  (tenant_id, demand_status, del_flag),
    KEY idx_demand_date    (tenant_id, demand_date),
    KEY idx_demand_creator (tenant_id, create_by, del_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '作物需求（运营端提 / 种植端回，row152 + row153 共用一表）';

-- ------------------------------------------------------------
-- 2. 字典 djs_plant_demand_category（种植需求分类）
--    甲方原话「新增字典表：种植需求分类，目前两项：种植需求、新作物需求」，后续可在字典管理增删。
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
SELECT 103600, '1001', '种植需求分类', 'djs_plant_demand_category', 1, NOW(),
       '运营端提作物需求时选的分类，可在字典管理里增删'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_type
                   WHERE dict_type = 'djs_plant_demand_category' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1036000, '1001', 0, '种植需求', 'plant', 'djs_plant_demand_category', '', 'primary', 'Y', 1, NOW(), 'V6-R152'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_demand_category' AND tenant_id = '1001'
                     AND dict_value = 'plant' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1036001, '1001', 1, '新作物需求', 'new_crop', 'djs_plant_demand_category', '', 'success', 'N', 1, NOW(), 'V6-R152'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_demand_category' AND tenant_id = '1001'
                     AND dict_value = 'new_crop' LIMIT 1) x);

-- ------------------------------------------------------------
-- 3. 字典 djs_plant_demand_status（作物需求状态）
--    两态：待回复 / 已回复。列表 dict-tag 与搜索下拉共用。
-- ------------------------------------------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
SELECT 103601, '1001', '作物需求状态', 'djs_plant_demand_status', 1, NOW(),
       '待回复/已回复两态，列表 dict-tag 与状态下拉共用'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_type
                   WHERE dict_type = 'djs_plant_demand_status' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1036010, '1001', 0, '待回复', 'pending', 'djs_plant_demand_status', '', 'warning', 'Y', 1, NOW(), 'V6-R152'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_demand_status' AND tenant_id = '1001'
                     AND dict_value = 'pending' LIMIT 1) x);

INSERT INTO sys_dict_data
  (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
SELECT 1036011, '1001', 1, '已回复', 'replied', 'djs_plant_demand_status', '', 'success', 'N', 1, NOW(), 'V6-R152'
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT 1 FROM sys_dict_data
                   WHERE dict_type = 'djs_plant_demand_status' AND tenant_id = '1001'
                     AND dict_value = 'replied' LIMIT 1) x);

-- ------------------------------------------------------------
-- 4. 菜单
--    12040-12043  运营管理 → 农场信息 → 作物需求（parent 12010 由 row149 建）
--    8300-8302    种植 → 种植管理 → 需求反馈（parent 8006；现有子项 order 0/1/2/3 → 取 4）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (12040, '作物需求', 12010, 3, 'cropDemand', 'djs-ops/cropDemand/index', '',
     1, 0, 'C', '0', '0', 'djs:ops:cropDemand:list', 'documentation', 1, NOW(), 'V6-R152'),
    (12041, '作物需求查询', 12040, 1, '', '', '',
     1, 0, 'F', '0', '0', 'djs:ops:cropDemand:list', '#', 1, NOW(), 'V6-R152'),
    (12042, '作物需求新增', 12040, 2, '', '', '',
     1, 0, 'F', '0', '0', 'djs:ops:cropDemand:add', '#', 1, NOW(), 'V6-R152'),
    (12043, '作物需求删除', 12040, 3, '', '', '',
     1, 0, 'F', '0', '0', 'djs:ops:cropDemand:remove', '#', 1, NOW(), 'V6-R152'),
    (8300, '需求反馈', 8006, 4, 'demandFeedback', 'djs-plant/demandFeedback/index', '',
     1, 0, 'C', '0', '0', 'djs:plant:demandFeedback:list', 'message', 1, NOW(), 'V6-R153'),
    (8301, '需求反馈查询', 8300, 1, '', '', '',
     1, 0, 'F', '0', '0', 'djs:plant:demandFeedback:list', '#', 1, NOW(), 'V6-R153'),
    (8302, '需求回复', 8300, 2, '', '', '',
     1, 0, 'F', '0', '0', 'djs:plant:demandFeedback:reply', '#', 1, NOW(), 'V6-R153');

-- ------------------------------------------------------------
-- 5. sys_role_menu 授权（ADR-0020：授权覆盖整条子树含父目录）
-- ------------------------------------------------------------
-- (a) 作物需求：从父菜单 12010「农场信息」已有角色派生
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
  FROM sys_role_menu rm
 CROSS JOIN (SELECT 12040 AS menu_id
             UNION ALL SELECT 12041
             UNION ALL SELECT 12042
             UNION ALL SELECT 12043) m
 WHERE rm.menu_id = 12010;

-- 显式兜底：超管 1 / 系统管理员 101 / 老板 102 / 管理人员 103（与 row149 给 12000/12010 的授权面一致）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 12040), (1, 12041), (1, 12042), (1, 12043),
    (101, 12040), (101, 12041), (101, 12042), (101, 12043),
    (102, 12040), (102, 12041), (102, 12042), (102, 12043),
    (103, 12040), (103, 12041), (103, 12042), (103, 12043);

-- (b) 需求反馈：从同层兄弟「种植计划」8070 已有角色派生（1/102/103/105/111）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT rm.role_id, m.menu_id
  FROM sys_role_menu rm
 CROSS JOIN (SELECT 8300 AS menu_id
             UNION ALL SELECT 8301
             UNION ALL SELECT 8302) m
 WHERE rm.menu_id = 8070;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 8300), (1, 8301), (1, 8302),
    (101, 8300), (101, 8301), (101, 8302);

-- 验收 query
--   SHOW COLUMNS FROM t_plant_crop_demand;
--   SELECT dict_type, dict_label, dict_value FROM sys_dict_data
--     WHERE dict_type IN ('djs_plant_demand_category', 'djs_plant_demand_status') ORDER BY dict_type, dict_sort;
--   SELECT menu_id, menu_name, parent_id, order_num, path, component, perms
--     FROM sys_menu WHERE menu_id IN (12040,12041,12042,12043,8300,8301,8302) ORDER BY menu_id;
--   SELECT menu_id, GROUP_CONCAT(role_id ORDER BY role_id) FROM sys_role_menu
--     WHERE menu_id IN (12040,8300) GROUP BY menu_id;
