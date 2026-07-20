-- FIX-DROP-BAR-SINGED-DICT-001：删除白条状态不可达死值 singed（审计 SM-1）
--
-- djs_bar_status 的 'singed'（待入库）为不可达状态：燎毛流程 singing → in_stock 直达，
-- 不会经过 singed，删除该字典值避免下拉/状态展示出现死状态。

DELETE FROM sys_dict_data WHERE dict_type = 'djs_bar_status' AND dict_value = 'singed';

-- 应用后需跑 bash script/sql/djs/_post-init.sh flush redis 字典缓存
