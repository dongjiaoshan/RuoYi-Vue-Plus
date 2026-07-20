-- 门店新增「生产标识码」字段（测试问题 row83）。
-- 用途：门店打包生产编码前缀，规则 <生产标识码>YYMMDD####（row84）。
-- 门店级唯一：同租户下不同门店不可重复；UNIQUE 含 tenant_id + del_unique（软删后重启用同码不冲突）。
-- NULL 默认：MySQL UNIQUE 允许多行 NULL，故存量门店未设置标识码不冲突。
ALTER TABLE t_md_store
    ADD COLUMN production_mark_code VARCHAR(32) NULL COMMENT '生产标识码（门店级唯一，门店打包生产编码前缀）' AFTER pos_system_id;

ALTER TABLE t_md_store
    ADD UNIQUE KEY uk_store_prod_mark (tenant_id, production_mark_code, del_unique);
