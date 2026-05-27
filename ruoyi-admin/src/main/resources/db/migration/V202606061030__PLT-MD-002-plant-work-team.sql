-- ============================================================
-- PLT-MD-002 种植班组主数据 (D8)
--
-- 域：种植 (PLT)
-- 表：t_plant_work_team / t_plant_work_people
-- 字典：djs_common_status (inline seed - 与 PLT-MD-001 字典族解耦)
--
-- 字段权威：doc/11-字段权威表.md §1.11 + §1.12
-- 业务约束：一人只能属于一个班组（service 层 INSERT 前校验 + UNIQUE 兜底）
--
-- 注：t_plant_work_team / t_plant_work_people 在 D2 baseline V202605200902
--    建过占位表（字段命名为草稿：team_code / work_id / people_id），与 doc/11 权威字段不一致。
--    本 ticket DROP+CREATE 重建对齐 doc/11（PLT-MD-001 实施时未触碰这两张表，0 行业务数据）。
-- ============================================================

-- ------------------------------------------------------------
-- 1. t_plant_work_team 班组表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_work_team;
CREATE TABLE t_plant_work_team (
  id              BIGINT       NOT NULL COMMENT '主键 (snowflake)',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  team_name       VARCHAR(64)  NOT NULL COMMENT '班组名称',
  leader_id       BIGINT       NULL     COMMENT '班组负责人 sys_user.user_id (ADR-0007)',
  team_status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 djs_common_status: 1=启用 2=停用',
  create_dept     BIGINT       NULL     COMMENT '创建部门',
  create_by       BIGINT       NULL     COMMENT '创建者',
  create_time     DATETIME     NULL     COMMENT '创建时间',
  update_by       BIGINT       NULL     COMMENT '更新者',
  update_time     DATETIME     NULL     COMMENT '更新时间',
  remark          VARCHAR(500) NULL     COMMENT '备注',
  del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志 0=正常 1=已删',
  del_unique      BIGINT       NOT NULL DEFAULT 0   COMMENT '软删唯一标识 (active=0 / 软删=id)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_name (tenant_id, team_name, del_unique),
  KEY idx_leader (leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植班组 (PLT-MD-002)';

-- ------------------------------------------------------------
-- 2. t_plant_work_people 班组人员表（M:N → sys_user）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_plant_work_people;
CREATE TABLE t_plant_work_people (
  id              BIGINT       NOT NULL COMMENT '主键 (snowflake)',
  tenant_id       VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
  team_id         BIGINT       NOT NULL COMMENT 'FK t_plant_work_team.id',
  user_id         BIGINT       NOT NULL COMMENT 'FK sys_user.user_id (ADR-0007: 员工统一 sys_user)',
  is_leader       TINYINT      NOT NULL DEFAULT 2 COMMENT '是否负责人 1=是 2=否',
  join_date       DATE         NULL     COMMENT '加入日期',
  create_dept     BIGINT       NULL     COMMENT '创建部门',
  create_by       BIGINT       NULL     COMMENT '创建者',
  create_time     DATETIME     NULL     COMMENT '创建时间',
  update_by       BIGINT       NULL     COMMENT '更新者',
  update_time     DATETIME     NULL     COMMENT '更新时间',
  remark          VARCHAR(500) NULL     COMMENT '备注',
  del_flag        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志 0=正常 1=已删',
  del_unique      BIGINT       NOT NULL DEFAULT 0   COMMENT '软删唯一标识',
  PRIMARY KEY (id),
  -- 一人只能属于一个班组（业务约束 + DB 兜底；软删行 del_unique=id 不冲突活动行 del_unique=0）
  UNIQUE KEY uk_user_active (tenant_id, user_id, del_unique),
  KEY idx_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植班组成员 (PLT-MD-002)';

-- ------------------------------------------------------------
-- 3. djs_common_status 字典 inline seed (ADR-0004 通用启停字典族)
--    本 ticket 占用 dict_id 段：103100（type）/ 1031000-1031001（data）
--    与 PLT-MD-001 使用的 103000-103002 / 1030000-1030022 段不冲突。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (103100, '1001', '通用启停状态', 'djs_common_status', 1, NOW(), 'ADR-0004 通用启停字典；班组等业务复用');

INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1031000, '1001', 0, '启用', '1', 'djs_common_status', '', 'success', 'Y', 1, NOW()),
    (1031001, '1001', 1, '停用', '2', 'djs_common_status', '', 'info',    'N', 1, NOW());
