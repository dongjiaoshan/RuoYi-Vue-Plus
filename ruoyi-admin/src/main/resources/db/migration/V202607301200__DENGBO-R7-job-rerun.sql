-- ============================================================
-- DENGBO row7  定时任务失败重跑 + 日期范围重跑（轻量方案）
-- ============================================================
-- 给所有 djs @Scheduled job（养殖聚合 / 仓库统计 / 有机证书预警）统一加：
--   1) 执行审计日志表 t_djs_job_log（schedule 自动落 + manual 手动重跑落）
--   2) admin「定时任务重跑」页：列表查日志 + 选 job + 日期范围逐日重跑
-- 经单一入口 DjsJobRunner 落日志，job 主体逻辑不改（UPSERT 幂等，重跑安全）。
--
-- menu_id 段：通用主数据 5000 下子段 5600 起（空闲）。
-- sys_menu / sys_role_menu 为 ruoyi 框架表，无 tenant_id。
-- ============================================================

-- ------------------------------------------------------------
-- 1. 执行审计日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_djs_job_log` (
    `id`           BIGINT       NOT NULL COMMENT '主键（雪花）',
    `tenant_id`    VARCHAR(20)  NOT NULL DEFAULT '1001' COMMENT '租户编号',
    `job_name`     VARCHAR(64)  NOT NULL COMMENT 'job 名（breed-aggregate / warehouse-stat / organic-warning）',
    `target_date`  DATE         NULL     COMMENT '重算目标日（手动重跑落具体日，schedule / 无日期 job 为 null）',
    `status`       VARCHAR(16)  NOT NULL COMMENT '执行状态（running / success / fail）',
    `error_msg`    VARCHAR(1000) NULL    COMMENT '失败信息（status=fail 时落，截断至 1000）',
    `cost_ms`      BIGINT       NULL     COMMENT '耗时毫秒（done / fail 时回填）',
    `run_time`     DATETIME     NOT NULL COMMENT '触发时间',
    `trigger_type` VARCHAR(16)  NOT NULL COMMENT '触发方式（schedule 定时 / manual 手动重跑）',
    `create_dept`  BIGINT       NULL     COMMENT '创建部门',
    `create_by`    BIGINT       NULL     COMMENT '创建者',
    `create_time`  DATETIME     NULL     COMMENT '创建时间',
    `update_by`    BIGINT       NULL     COMMENT '更新者',
    `update_time`  DATETIME     NULL     COMMENT '更新时间',
    `del_flag`     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标记（0 未删 / 1 已删）',
    PRIMARY KEY (`id`),
    KEY `idx_job_log_tenant_name_time` (`tenant_id`, `job_name`, `run_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'djs 定时任务执行日志';

-- ------------------------------------------------------------
-- 2. sys_menu seed：定时任务重跑（挂通用主数据 5000 下）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu (
    menu_id, menu_name, parent_id, order_num,
    path, component, query_param,
    is_frame, is_cache, menu_type, visible, status,
    perms, icon, create_by, create_time, remark)
VALUES
    (5600, '定时任务重跑', 5000, 90, 'job-rerun', 'djs-common/jobRerun/index', '',
     1, 0, 'C', '0', '0',
     'djs:job:rerun:list', 'time-range', 1, NOW(), 'DENGBO-R7'),

    (5601, '日志查询', 5600, 1, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:job:rerun:list', '#', 1, NOW(), 'DENGBO-R7'),

    (5602, '手动重跑', 5600, 2, '', '', '',
     1, 0, 'F', '0', '0',
     'djs:job:rerun:exec', '#', 1, NOW(), 'DENGBO-R7');

-- ------------------------------------------------------------
-- 3. role_menu 绑定（超级管理员角色 1 全绑）
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.menu_id
FROM sys_menu m
WHERE m.menu_id BETWEEN 5600 AND 5602;
