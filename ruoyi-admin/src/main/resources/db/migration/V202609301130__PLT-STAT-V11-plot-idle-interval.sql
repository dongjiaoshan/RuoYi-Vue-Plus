CREATE TABLE IF NOT EXISTS t_plant_plot_idle_interval (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id VARCHAR(20) NOT NULL DEFAULT '1001' COMMENT '租户编号',
    plot_id BIGINT NOT NULL COMMENT '地块 ID',
    idle_started_on DATE NOT NULL COMMENT '空闲首日（含）',
    idle_ended_on DATE NULL COMMENT '恢复日（不含），NULL 表示仍空闲',
    start_source VARCHAR(32) NOT NULL COMMENT '开段来源',
    end_source VARCHAR(32) NULL COMMENT '关段来源',
    create_dept BIGINT NULL COMMENT '创建部门',
    create_by BIGINT NULL COMMENT '创建者',
    create_time DATETIME NULL COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新者',
    update_time DATETIME NULL COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    del_unique BIGINT NOT NULL DEFAULT 0 COMMENT '软删唯一标识',
    open_plot_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN idle_ended_on IS NULL AND del_flag = '0' THEN plot_id END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plot_idle_open (tenant_id, open_plot_id, del_unique),
    KEY idx_plot_idle_period (tenant_id, del_flag, idle_started_on, idle_ended_on),
    KEY idx_plot_idle_history (tenant_id, plot_id, del_flag, idle_started_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='地块空闲区间';

ALTER TABLE t_plant_production_indicator_annual
    ADD COLUMN idle_days_sum BIGINT NOT NULL DEFAULT 0 COMMENT '年内各段空闲天数合计',
    ADD COLUMN idle_interval_count INT NOT NULL DEFAULT 0 COMMENT '年内有空闲自然日的区间数';

ALTER TABLE t_plant_plot_info
    ADD COLUMN idle_open_interval_id BIGINT NULL COMMENT '当前开放空闲区间ID（空闲统计用）',
    ADD COLUMN idle_last_ended_on DATE NULL COMMENT '最近一次空闲恢复日（空闲统计用）';

-- 以最近有效退茬业务日推断当前空闲段，缺失或过早时使用 2026-08-01。
-- 历史行固定用地块 ID 作为主键。重跑时保留已初始化的区间和服务已写入的开放区间。
INSERT INTO t_plant_plot_idle_interval (id, tenant_id, plot_id, idle_started_on, start_source, create_time, update_time)
SELECT p.id, p.tenant_id, p.id,
       GREATEST(COALESCE(r.last_rotation, DATE '2026-08-01'), DATE '2026-08-01'),
       'history_init', NOW(), NOW()
  FROM t_plant_plot_info p
  LEFT JOIN (
      SELECT tenant_id, plot_id, MAX(farm_date) AS last_rotation
        FROM t_plant_farm_records
       WHERE farm_type = 'rotation' AND del_flag = '0'
         AND farm_date <= DATE(UTC_TIMESTAMP() + INTERVAL 8 HOUR)
       GROUP BY tenant_id, plot_id
  ) r ON r.tenant_id = p.tenant_id AND r.plot_id = p.id
 WHERE p.plot_status = 1 AND p.del_flag = '0'
ON DUPLICATE KEY UPDATE id = t_plant_plot_idle_interval.id;

UPDATE t_plant_plot_info p
  JOIN t_plant_plot_idle_interval i ON i.open_plot_id = p.id AND i.del_unique = 0
   AND i.tenant_id COLLATE utf8mb4_bin = p.tenant_id COLLATE utf8mb4_bin
   SET p.idle_open_interval_id = i.id
 WHERE p.plot_status = 1 AND p.del_flag = '0';
