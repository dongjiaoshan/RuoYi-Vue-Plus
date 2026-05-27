-- D08 HOTFIX：t_warehouse_demand_pig 补 create_dept 列。
--
-- 触发：Kevin 人力测试 §2.5 指定猪只时 INSERT 报 `Unknown column 'create_dept' in 'field list'`。
-- 根因：ruoyi BaseEntity 含 createDept 字段（用 MyBatis-Plus @TableField + InjectionMetaObjectHandler 自动 fill），
--      原始 V202606061000 建 t_warehouse_demand_pig 时漏建该列；其他 t_warehouse_* 表均含该列（DESC 查证）。
-- 修法：补一列 create_dept BIGINT NULL，与 ruoyi 自带表（sys_user 等）+ 其他 djs 业务表对齐。

ALTER TABLE `t_warehouse_demand_pig`
  ADD COLUMN `create_dept` BIGINT NULL COMMENT '创建部门' AFTER `assigned_by`;
