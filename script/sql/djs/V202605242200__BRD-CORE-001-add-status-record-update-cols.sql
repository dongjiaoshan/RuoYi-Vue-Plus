-- ============================================================
-- BRD-CORE-001 t_farm_status_record 补 update_by / update_time 列
--
-- 触发：testing-human B.1 mp 引种提交时 MP InjectionMetaObjectHandler
-- 全局 insertFill 注入 updateBy/updateTime，但本表 DDL 缺这两列 →
-- SQLSyntaxErrorException: Unknown column 'update_by' in 'field list'
--
-- 其他 12 张业务表都已有这两列；status_record 当初按"事件流不可编辑"
-- 语义省略，但 MP 框架行为是全局 insertFill 不分表，省不掉。
--
-- SYS-INIT-001 V202605200901 源文件同步更新，新环境从零跑也正确。
-- ============================================================
ALTER TABLE t_farm_status_record
  ADD COLUMN update_by   BIGINT   NULL COMMENT '更新人（MP insertFill 占位，状态记录实际不 update）' AFTER create_time,
  ADD COLUMN update_time DATETIME NULL COMMENT '更新时间（MP insertFill 占位）' AFTER update_by;
