-- FIX-MED-MODEL-003 药品主数据 + 仓库药品库存 seed（ADR-0012 药品归仓库库位统一）
-- 养殖药品表此前为空（无 seed），导致 mp 药品领用页「暂无药品」。
-- 按 ADR-0012：药品主数据(名/码/规格/单位)留养殖 t_breed_medicine_info；库存真值落仓库 t_warehouse_location_stock(medicine 维) @ 药品库位 L0012。
-- 幂等：medicine_code 唯一判重（NOT EXISTS 守重跑）。

-- 1) 药品主数据（med_status=1 启用；current_stock 不再作真值，留 0）
INSERT INTO t_breed_medicine_info (tenant_id, medicine_code, medicine_name, medicine_type, unit, spec, manufacturer, withdraw_days, current_stock, med_status, del_flag, del_unique)
SELECT '1001','MED-AB-001','阿莫西林可溶性粉','antibiotic','袋','100g/袋','华畜生物',7,0,1,'0',0
WHERE NOT EXISTS (SELECT 1 FROM t_breed_medicine_info WHERE medicine_code='MED-AB-001' AND del_flag='0');
INSERT INTO t_breed_medicine_info (tenant_id, medicine_code, medicine_name, medicine_type, unit, spec, manufacturer, withdraw_days, current_stock, med_status, del_flag, del_unique)
SELECT '1001','MED-VC-001','猪瘟活疫苗','vaccine','瓶','20头份/瓶','金宇保灵',0,0,1,'0',0
WHERE NOT EXISTS (SELECT 1 FROM t_breed_medicine_info WHERE medicine_code='MED-VC-001' AND del_flag='0');
INSERT INTO t_breed_medicine_info (tenant_id, medicine_code, medicine_name, medicine_type, unit, spec, manufacturer, withdraw_days, current_stock, med_status, del_flag, del_unique)
SELECT '1001','MED-NU-001','复合多维','nutrition','袋','500g/袋','正大','0',0,1,'0',0
WHERE NOT EXISTS (SELECT 1 FROM t_breed_medicine_info WHERE medicine_code='MED-NU-001' AND del_flag='0');
INSERT INTO t_breed_medicine_info (tenant_id, medicine_code, medicine_name, medicine_type, unit, spec, manufacturer, withdraw_days, current_stock, med_status, del_flag, del_unique)
SELECT '1001','MED-DI-001','聚维酮碘溶液','disinfectant','桶','5L/桶','大华农',0,0,1,'0',0
WHERE NOT EXISTS (SELECT 1 FROM t_breed_medicine_info WHERE medicine_code='MED-DI-001' AND del_flag='0');

-- 2) 仓库药品库存（medicine 维 @ 药品库位 location_code='L0012'）：库存真值
INSERT INTO t_warehouse_location_stock (tenant_id, location_id, medicine_id, product_name, product_stock, product_unit, is_end, del_flag, del_unique)
SELECT '1001', loc.id, m.id, m.medicine_name, s.init_stock, m.unit, 0, '0', 0
FROM (
    SELECT 'MED-AB-001' code, 50.000 init_stock
    UNION ALL SELECT 'MED-VC-001', 200.000
    UNION ALL SELECT 'MED-NU-001', 30.000
    UNION ALL SELECT 'MED-DI-001', 20.000
) s
JOIN t_breed_medicine_info m ON m.medicine_code = s.code AND m.del_flag='0' AND m.tenant_id='1001'
JOIN t_warehouse_location_info loc ON loc.location_code='L0012' AND loc.location_type='medicine' AND loc.del_flag='0'
WHERE NOT EXISTS (
    SELECT 1 FROM t_warehouse_location_stock ls
    WHERE ls.medicine_id = m.id AND ls.location_id = loc.id AND ls.del_flag='0'
);
