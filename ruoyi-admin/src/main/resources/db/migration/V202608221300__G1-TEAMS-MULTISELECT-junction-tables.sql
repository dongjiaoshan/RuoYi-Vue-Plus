-- ============================================================
-- G1-TEAMS-MULTISELECT（row36 / row37 / row40）：班组全量多选数据模型改造
-- ============================================================
-- 决策（全量多选）：种植计划 plant_by/harvest_by、农事 farm_by、采摘活动 activity_by
--   由单值 FK 升级为多班组。旧单列（t_plant_plant_details.plant_by/harvest_by、
--   t_plant_farm_records.farm_by、t_plant_plant_activity.activity_by）保留一版做过渡读兼容 +
--   下游单值聚合（班组绩效 / dashboard / mapper JOIN）继续读旧列 = 多选第一个，口径不变。
-- 多选全集存入 3 张中间表；VO 展示侧从中间表 GROUP_CONCAT / enrich 出逗号 / 多 tag。
--
-- 幂等 guard（INFORMATION_SCHEMA 守卫）：可先手动 apply staging 做 live 验证，Flyway 重跑安全。
-- version 202608221300 > 当前 flyway max V202608221200。无字典变更，无需 flush redis。
--
-- 注：t_plant_plant_activity 原 uk_crop_date_team 已在 V202607301300（DENGBO-R4）DROP，
--   现表已是 per-event 无唯一键，本迁移不再处理其唯一键，仅补建 activity 中间表 + 回填。
-- ============================================================
SET NAMES utf8mb4;
SET @db := DATABASE();

-- ------------------------------------------------------------
-- 1) 种植计划明细班组中间表 t_plant_details_team（role: plant | harvest）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_plant_details_team (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    detail_id   BIGINT       NOT NULL                 COMMENT 'FK → t_plant_plant_details.id',
    team_id     BIGINT       NOT NULL                 COMMENT 'FK → t_plant_work_team.id',
    role        VARCHAR(16)  NOT NULL                 COMMENT '班组角色：plant=种植班组 / harvest=采摘班组',
    create_dept BIGINT       NULL                     COMMENT '创建部门',
    create_by   BIGINT       NULL                     COMMENT '创建者',
    create_time DATETIME     NULL                     COMMENT '创建时间',
    update_by   BIGINT       NULL                     COMMENT '更新者',
    update_time DATETIME     NULL                     COMMENT '更新时间',
    del_flag    CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_detail_role_team (tenant_id, detail_id, role, team_id, del_flag),
    KEY idx_detail_role (tenant_id, detail_id, role),
    KEY idx_team (tenant_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植计划明细·班组多选中间表（plant/harvest）';

-- ------------------------------------------------------------
-- 2) 农事记录班组中间表 t_farm_records_team
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_farm_records_team (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    record_id   BIGINT       NOT NULL                 COMMENT 'FK → t_plant_farm_records.id',
    team_id     BIGINT       NOT NULL                 COMMENT 'FK → t_plant_work_team.id',
    create_dept BIGINT       NULL                     COMMENT '创建部门',
    create_by   BIGINT       NULL                     COMMENT '创建者',
    create_time DATETIME     NULL                     COMMENT '创建时间',
    update_by   BIGINT       NULL                     COMMENT '更新者',
    update_time DATETIME     NULL                     COMMENT '更新时间',
    del_flag    CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_team (tenant_id, record_id, team_id, del_flag),
    KEY idx_record (tenant_id, record_id),
    KEY idx_team (tenant_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='农事记录·班组多选中间表';

-- ------------------------------------------------------------
-- 3) 采摘活动班组中间表 t_plant_activity_team
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_plant_activity_team (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 snowflake',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    activity_id BIGINT       NOT NULL                 COMMENT 'FK → t_plant_plant_activity.id',
    team_id     BIGINT       NOT NULL                 COMMENT 'FK → t_plant_work_team.id',
    create_dept BIGINT       NULL                     COMMENT '创建部门',
    create_by   BIGINT       NULL                     COMMENT '创建者',
    create_time DATETIME     NULL                     COMMENT '创建时间',
    update_by   BIGINT       NULL                     COMMENT '更新者',
    update_time DATETIME     NULL                     COMMENT '更新时间',
    del_flag    CHAR(1)      DEFAULT '0'              COMMENT '软删 0=正常 1=已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_team (tenant_id, activity_id, team_id, del_flag),
    KEY idx_activity (tenant_id, activity_id),
    KEY idx_team (tenant_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采摘活动·班组多选中间表';

-- ------------------------------------------------------------
-- 4) 回填：现存单列 → 中间表各 1 行（WHERE 非空；INSERT ... SELECT 幂等，UNIQUE 兜底重跑）
--    id 用 NULL 走 AUTO_INCREMENT；tenant_id / create_time 从源表带过来对齐历史。
-- ------------------------------------------------------------
-- 4.1 plant_by → role=plant
INSERT INTO t_plant_details_team (tenant_id, detail_id, team_id, role, create_dept, create_by, create_time, del_flag)
SELECT d.tenant_id, d.id, d.plant_by, 'plant', d.create_dept, d.create_by, d.create_time, '0'
FROM t_plant_plant_details d
WHERE d.del_flag = '0' AND d.plant_by IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_plant_details_team t
    WHERE t.detail_id = d.id AND t.role = 'plant' AND t.team_id = d.plant_by AND t.del_flag = '0'
  );

-- 4.2 harvest_by → role=harvest
INSERT INTO t_plant_details_team (tenant_id, detail_id, team_id, role, create_dept, create_by, create_time, del_flag)
SELECT d.tenant_id, d.id, d.harvest_by, 'harvest', d.create_dept, d.create_by, d.create_time, '0'
FROM t_plant_plant_details d
WHERE d.del_flag = '0' AND d.harvest_by IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_plant_details_team t
    WHERE t.detail_id = d.id AND t.role = 'harvest' AND t.team_id = d.harvest_by AND t.del_flag = '0'
  );

-- 4.3 farm_by → 农事中间表
INSERT INTO t_farm_records_team (tenant_id, record_id, team_id, create_dept, create_by, create_time, del_flag)
SELECT r.tenant_id, r.id, r.farm_by, r.create_dept, r.create_by, r.create_time, '0'
FROM t_plant_farm_records r
WHERE r.del_flag = '0' AND r.farm_by IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_farm_records_team t
    WHERE t.record_id = r.id AND t.team_id = r.farm_by AND t.del_flag = '0'
  );

-- 4.4 activity_by → 采摘活动中间表
INSERT INTO t_plant_activity_team (tenant_id, activity_id, team_id, create_dept, create_by, create_time, del_flag)
SELECT a.tenant_id, a.id, a.activity_by, a.create_dept, a.create_by, a.create_time, '0'
FROM t_plant_plant_activity a
WHERE a.del_flag = '0' AND a.activity_by IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_plant_activity_team t
    WHERE t.activity_id = a.id AND t.team_id = a.activity_by AND t.del_flag = '0'
  );
