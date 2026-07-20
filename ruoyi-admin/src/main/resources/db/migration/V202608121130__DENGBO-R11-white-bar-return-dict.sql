-- DENGBO-R11 字典【白条产品退回】djs_white_bar_return_product（门店退回操作 · 白条产品来源）
-- 用途：门店退回操作「猪肉产品」tab 的白条产品退回候选取此字典配置的产品；单位显示对应产品原材料单位、按重量(kg)退货。
-- 约定（Kevin 2026-07-12 拍板）：新建【空】字典，客户在 admin「字典管理」自行增删配置；
--   label=产品名（admin 展示），value=产品业务编码 product_id（VARCHAR），后端按 value resolve 成雪花主键。
--   与【猪肉产品退回】djs_pork_return_product 分开：后者门店盘点/打包仍用，本字典专供门店退回白条产品。
-- 仅登记字典类型、不 seed 数据项（空字典待客户配置）。
INSERT IGNORE INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES (102608121, '1001', '白条产品退回', 'djs_white_bar_return_product', 1, NOW(),
        '门店退回操作白条产品取数：label=产品名 value=product_id 业务码；空字典客户在 admin 字典管理自配（DENGBO-R11）');
