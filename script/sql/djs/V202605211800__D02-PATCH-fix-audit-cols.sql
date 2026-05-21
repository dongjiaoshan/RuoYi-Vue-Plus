-- ============================================================
-- D02 hot-patch — 把 D01/D02 创建的 3 张主数据/编码表的审计字段对齐 ruoyi BaseEntity 约定
--   ruoyi BaseEntity: createDept Long / createBy Long / updateBy Long （不是 varchar）
--   现状：t_md_biz_code_rule / t_md_biz_code_sequence / t_md_person 都用 varchar(64) + 缺 create_dept
--   后果：MyBatis-Plus auto-SELECT 含 create_dept → SQLSyntaxErrorException
--         seed 写入 'system' → 后续 ResultSet.getLong(create_by) NumberFormatException
--   修复步骤：先 UPDATE 把字符串值改成 NULL / 1，再 MODIFY 类型为 BIGINT，最后 ADD create_dept
-- ============================================================

-- ------------------------------------------------------------
-- t_md_biz_code_rule（9 行 seed，create_by='system' / update_by=''）
-- ------------------------------------------------------------
UPDATE t_md_biz_code_rule SET create_by = '1' WHERE create_by = 'system';
UPDATE t_md_biz_code_rule SET update_by = NULL WHERE update_by = '' OR update_by IS NULL OR update_by = '0';
ALTER TABLE t_md_biz_code_rule
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER status;

-- ------------------------------------------------------------
-- t_md_biz_code_sequence（0 行，简单 ALTER）
-- ------------------------------------------------------------
UPDATE t_md_biz_code_sequence SET create_by = NULL WHERE create_by = '' OR create_by IS NULL;
UPDATE t_md_biz_code_sequence SET update_by = NULL WHERE update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_biz_code_sequence
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER current_seq;

-- ------------------------------------------------------------
-- t_md_person（0 行）
-- ------------------------------------------------------------
UPDATE t_md_person SET create_by = NULL WHERE create_by = '' OR create_by IS NULL;
UPDATE t_md_person SET update_by = NULL WHERE update_by = '' OR update_by IS NULL;
ALTER TABLE t_md_person
  MODIFY COLUMN create_by BIGINT NULL COMMENT '创建人',
  MODIFY COLUMN update_by BIGINT NULL COMMENT '更新人',
  ADD COLUMN create_dept BIGINT NULL COMMENT '创建部门' AFTER avatar_url;

-- ------------------------------------------------------------
-- sys_menu 5001 占位的 create_by 也是 'system'，改成 1
--   （ruoyi 自带 sys_menu.create_by 已经是 bigint，存 'system' 等于隐式转 0；这里改成显式 1=admin）
-- ------------------------------------------------------------
UPDATE sys_menu SET create_by = 1 WHERE menu_id = 5001 AND (create_by = 0 OR create_by IS NULL);
