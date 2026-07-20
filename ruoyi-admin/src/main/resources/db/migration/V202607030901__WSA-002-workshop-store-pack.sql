-- WSA-002 生产车间字典补「门店打包间」+ 白条产品 workshop 回填（仓库×门店全流程对齐 阶段0 F0-2）
-- 1. djs_product_workshop 增 5=门店打包间（门店猪肉打包筛 product_workshop=5 用；现有 1-4）。
INSERT IGNORE INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time)
VALUES
    (1026070310, '1001', 4, '门店打包间', '5', 'djs_product_workshop', '', 'success', 'N', 1, NOW());

-- 2. 白条标准产品 product_workshop 回填为 1=燎毛间（燎毛产出，P3 燎毛产品按 workshop 筛依赖此值；现全 NULL）。
--    仅回填 NULL 行，幂等；不动测试/演示数据。
UPDATE t_warehouse_product_info
   SET product_workshop = 1, update_time = NOW()
 WHERE tenant_id = '1001'
   AND del_flag = '0'
   AND product_id LIKE 'PROD-WHITE-BAR-%'
   AND product_workshop IS NULL;
