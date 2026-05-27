-- ============================================================================
-- PLT-MD-001 fix：djs_plot_type 字典重复
--
-- D1 SYS-INIT-002 已 seed `djs_plot_type` 3 行（大棚/露天/水田，dict_code 1002010-1002012）。
-- 本 ticket V202606050900 误把 INSERT IGNORE 写错（只防 dict_id PK 撞，未对照 dict_value
-- 是否已存在），导致 1030000-1030002 又灌了 open / shed / greenhouse 3 行，与 D1 数据有
-- "open"、"greenhouse" 两条 dict_value 重复，admin 字典管理页 / 表单下拉显示重复项。
--
-- 修复：删除本 ticket 新灌的 3 行；保留 D1 现状（大棚 / 露天 / 水田）。
-- 若客户 Q1 回复后需新增"温室"等，管理员可在 admin 字典管理页手动添加。
-- ============================================================================

DELETE FROM sys_dict_data WHERE dict_code IN (1030000, 1030001, 1030002);
DELETE FROM sys_dict_type WHERE dict_id = 103000;
