-- DJS-FIX-WMS-RALN-A：库位配置管理对齐原型
-- 原型「库位配置管理」列表含「库位排序」「库位说明」两列；新增/编辑表单含「库位排序」录入。
-- t_warehouse_location_info（V202605290910 建）缺这两列，纯 ADD COLUMN 补齐，向后兼容（旧记录新列默认值）。
-- 公共字段 + tenant_id 不变。

SET NAMES utf8mb4;

ALTER TABLE t_warehouse_location_info
  ADD COLUMN location_sort INT          NULL DEFAULT 0  COMMENT '库位排序（升序，越小越靠前）' AFTER location_status,
  ADD COLUMN location_desc VARCHAR(500) NULL            COMMENT '库位说明' AFTER location_sort;
