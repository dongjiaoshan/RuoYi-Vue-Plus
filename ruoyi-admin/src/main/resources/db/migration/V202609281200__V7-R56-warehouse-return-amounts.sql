-- V7 第56/58行：新增独立仓库处理字段，保留原退回数量及历史处置字段。
SET @djs_return_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_store_return' AND COLUMN_NAME='warehouse_discard_qty')=0, 'ALTER TABLE t_store_return ADD COLUMN warehouse_discard_qty DECIMAL(18,3) NULL COMMENT ''仓库丢弃记录量(原材料单位)，历史为NULL''', 'SELECT 1');
PREPARE djs_return_stmt FROM @djs_return_ddl;
EXECUTE djs_return_stmt;
DEALLOCATE PREPARE djs_return_stmt;

SET @djs_return_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_store_return' AND COLUMN_NAME='warehouse_remark')=0, 'ALTER TABLE t_store_return ADD COLUMN warehouse_remark VARCHAR(500) NULL COMMENT ''仓库处理备注''', 'SELECT 1');
PREPARE djs_return_stmt FROM @djs_return_ddl;
EXECUTE djs_return_stmt;
DEALLOCATE PREPARE djs_return_stmt;

SET @djs_return_ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='t_store_return' AND COLUMN_NAME='warehouse_added')=0, 'ALTER TABLE t_store_return ADD COLUMN warehouse_added TINYINT NOT NULL DEFAULT 0 COMMENT ''仓库补录标记，1=无门店报退量''', 'SELECT 1');
PREPARE djs_return_stmt FROM @djs_return_ddl;
EXECUTE djs_return_stmt;
DEALLOCATE PREPARE djs_return_stmt;
