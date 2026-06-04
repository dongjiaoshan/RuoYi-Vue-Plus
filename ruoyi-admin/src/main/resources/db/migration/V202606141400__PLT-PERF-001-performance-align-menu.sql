-- ============================================================
-- PLT-PERF-001 班组绩效月度结算（D12）
--
-- 1. 对齐绩效表 t_plant_work_performance 到 doc/11 §1.13
--    baseline 占位（V202605200902）的 pick_weight 精度 12,2 → 12,3
--    （斤保留 3 位，与聚合源 t_plant_plant_details.actual_yield DECIMAL(12,3) 对齐）
--    不 DROP+CREATE（该表 V1 无业务数据，ALTER 安全）；baseline 多余 people_id 列保留不删，
--    entity/VO 均不映射（doc/11 §1.13 无该字段）。
--    不加 UNIQUE 约束：generate 走"软删旧行 del_flag '0'→'2' + INSERT 新行"幂等保证不重复，
--    加 UNIQUE(tenant_id,stat_month,team_id,crop_id) 会与软删行冲突（本表无 del_unique 辅助列）。
--
-- 2. menu seed（8200-8203，种植域 8200-8299 新二级分段；ADR-0006）
--    8200 C 班组绩效   path=performance   component=djs-plant/performance/index
--    8201 F 绩效详情   perms=djs:plantPerformance:query
--    8202 F 生成结算   perms=djs:plantPerformance:generate
--    8203 F 绩效导出   perms=djs:plantPerformance:export
--    父菜单 8000 由 SYS-AUTH-001 seed。
--    V1 不新建业务角色，仅绑 super-admin(role_id=1)，与既有 plant menu seed 一致。
-- ============================================================

-- 1. 绩效表精度对齐
ALTER TABLE t_plant_work_performance
    MODIFY COLUMN pick_weight DECIMAL(12,3) NULL DEFAULT 0 COMMENT '采摘总量（斤）';

-- 2. menu seed
INSERT INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (8200, '班组绩效', 8000, 60, 'performance', 'djs-plant/performance/index', NULL, 1, 0, 'C', '0', '0', 'djs:plantPerformance:list',     'money', 1, NOW(), 'PLT-PERF-001'),
  (8201, '绩效详情', 8200, 1,  '',           NULL,                          NULL, 1, 0, 'F', '0', '0', 'djs:plantPerformance:query',    NULL,    1, NOW(), 'PLT-PERF-001'),
  (8202, '生成结算', 8200, 2,  '',           NULL,                          NULL, 1, 0, 'F', '0', '0', 'djs:plantPerformance:generate', NULL,    1, NOW(), 'PLT-PERF-001'),
  (8203, '绩效导出', 8200, 3,  '',           NULL,                          NULL, 1, 0, 'F', '0', '0', 'djs:plantPerformance:export',   NULL,    1, NOW(), 'PLT-PERF-001');

-- 3. 显式分配给 super-admin (role_id=1)
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 8200), (1, 8201), (1, 8202), (1, 8203);
