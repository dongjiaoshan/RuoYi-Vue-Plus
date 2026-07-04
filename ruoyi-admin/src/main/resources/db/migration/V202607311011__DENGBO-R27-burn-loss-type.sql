-- DENGBO row27：损耗总览新增「燎毛损耗」类型。
-- 燎毛间「处理完成」时，剩余未入库重量计入燎毛损耗（= 燎毛间接收重量[头皮肉重量] − 燎毛间入库产品重量之和），
-- 由 PigBurnRecordServiceImpl.finishBurn 写统一损耗流水（loss_type='burn_loss'），进「损耗总览」明细。
-- 损耗总览 compute-on-read over t_warehouse_loss_flow、按 loss_type 分组，前端 loss_type 走 djs_loss_type 字典渲染，
-- 故仅补字典值即可，无需改后端聚合/VO/前端。
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1007037, '1001', 8, '燎毛损耗', 'burn_loss', 'djs_loss_type', '', 'warning', 'N', 1, NOW(), '白条：燎毛间接收重量[头皮肉重量]−燎毛入库产品重量之和');
