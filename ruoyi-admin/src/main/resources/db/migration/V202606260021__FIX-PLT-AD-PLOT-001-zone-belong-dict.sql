-- FIX-PLT-AD-PLOT-001 / K178：片区「所属大区」改字典下拉。
-- 决策 G5(b)：客户暂未给大区清单 → 先建空字典类型 + 前端控件就位（ZoneForm 改 el-select）；
-- 拿到清单后再补一条 sys_dict_data INSERT 灌项即可（dict_type='djs_zone_belong'）。
-- 命名遵循 ADR-0004 djs 字典 seed 规范；dict_id 落 plant 段（103003，紧随 103002 种植季节）。

INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, remark)
SELECT 103003, '1001', '所属大区', 'djs_zone_belong', 1, 'FIX-PLT-AD-PLOT-001 片区所属大区（项待客户提供清单后补 sys_dict_data）'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'djs_zone_belong');
