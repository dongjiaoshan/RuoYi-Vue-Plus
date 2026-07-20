-- ============================================================
-- FIX-PLT-HARVEST-ACTIVITY-001 采摘活动表 t_plant_plant_activity
--
-- 邓博权威 spec 新增表（原设计无此表）：采摘重量录入流水表。
-- 每次 mp 采摘重量录入一行；报表 SUM(daily_weight) GROUP BY crop_id, activity_date。
-- recordDailyWeight 按 UNIQUE 幂等：同 crop+date+班组已存在则累加 daily_weight，否则 insert。
--
-- 业务字段：activity_date / crop_id / daily_weight / activity_by
-- + 项目标准公共字段（替代邓博 create_id/is_del 等）。
-- ============================================================

SET NAMES utf8mb4;

DROP TABLE IF EXISTS t_plant_plant_activity;
CREATE TABLE t_plant_plant_activity (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（雪花）',
    tenant_id       VARCHAR(20)   NOT NULL DEFAULT '1001' COMMENT '租户/农场 ID',
    activity_date   DATE          NOT NULL                COMMENT '采摘日期',
    crop_id         BIGINT        NOT NULL                COMMENT 'FK → t_plant_crop_info.id',
    daily_weight    DECIMAL(12,3) NOT NULL DEFAULT 0.000  COMMENT '当日采摘重量（kg，同 crop+date+班组累加）',
    activity_by     BIGINT        NULL                    COMMENT '记录班组 FK → t_plant_work_team.id',
    create_dept     BIGINT        NULL                    COMMENT '创建部门',
    create_by       BIGINT        NULL                    COMMENT '创建人',
    create_time     DATETIME      NULL                    COMMENT '创建时间',
    update_by       BIGINT        NULL                    COMMENT '更新人',
    update_time     DATETIME      NULL                    COMMENT '更新时间',
    del_flag        CHAR(1)       NOT NULL DEFAULT '0'    COMMENT '删除标志',
    del_unique      BIGINT        NOT NULL DEFAULT 0      COMMENT '软删 token（软删时 SET del_unique=id 释放活槽）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crop_date_team (tenant_id, crop_id, activity_date, activity_by, del_unique),
    KEY idx_crop_date (tenant_id, crop_id, activity_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种植 - 采摘活动表（FIX-PLT-HARVEST-ACTIVITY-001 采摘重量流水）';
