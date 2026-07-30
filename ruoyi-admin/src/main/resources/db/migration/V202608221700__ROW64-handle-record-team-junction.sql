-- ============================================================
-- ROW64：毛菜处理·采摘重量录入「采摘班组」升级为全量多选
-- ============================================================
-- 决策（全量多选，沿用 G1-TEAMS-MULTISELECT/row36-40 范式）：
--   mp 采摘重量录入(record_type=1)的采摘班组由单值 FK 升级为多班组。
--   旧单列 t_warehouse_handle_record.team_id 保留一版做过渡读兼容——继续写多选第一个，
--   下游 row39 班组绩效按 team_id GROUP BY 的聚合口径不变、无需改。
--   多选全集写入本中间表 t_warehouse_handle_record_team。
--
-- 幂等 guard（IF NOT EXISTS 建表 + NOT EXISTS 回填）：可先手动 apply staging 做 live 验证，
--   Flyway 重跑安全。version 202608221700 > 当前 flyway max V202608221600。无字典变更，无需 flush redis。
-- ============================================================
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 采摘班组多选中间表 t_warehouse_handle_record_team（record_id → team_id）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_warehouse_handle_record_team (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   VARCHAR(20)  NOT NULL DEFAULT '1001'  COMMENT '租户编号',
    record_id   BIGINT       NOT NULL                 COMMENT 'FK → t_warehouse_handle_record.id',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采摘重量录入·采摘班组多选中间表';

-- ------------------------------------------------------------
-- 2) 回填：现存单列 team_id → 中间表各 1 行（record_type=1 采收行且 team_id 非空）
--    id 用 NULL 走 AUTO_INCREMENT；tenant_id / create_* 从源行带过来对齐历史；NOT EXISTS 守卫幂等重跑。
-- ------------------------------------------------------------
INSERT INTO t_warehouse_handle_record_team (tenant_id, record_id, team_id, create_dept, create_by, create_time, del_flag)
SELECT r.tenant_id, r.id, r.team_id, r.create_dept, r.create_by, r.create_time, '0'
FROM t_warehouse_handle_record r
WHERE r.del_flag = '0' AND r.record_type = 1 AND r.team_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_warehouse_handle_record_team t
    WHERE t.record_id = r.id AND t.team_id = r.team_id AND t.del_flag = '0'
  );
