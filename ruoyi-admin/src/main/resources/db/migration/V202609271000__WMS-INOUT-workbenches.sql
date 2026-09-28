-- V6 rows 262/264/265：出入库工作台持久幂等及业务字典。
-- 部署成功最大版本 202609261800；按 ADR-0008 仓库模块 10:00 段取号。
-- request_key 仅本次操作的主 IN 流水赋值；历史/旧入口为 NULL，MySQL 唯一键允许多 NULL。
-- 单事务先锁整猪、复用入库/领用/出库，再保存回执；唯一键冲突使整次操作回滚。
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN request_key VARCHAR(36) NULL COMMENT '工作台单次操作UUID，主入库流水唯一回执',
    ADD COLUMN request_hash CHAR(64) NULL COMMENT '规范化操作载荷SHA256',
    ADD UNIQUE KEY uk_stock_flow_request (tenant_id, request_key, del_unique);

INSERT INTO sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_by, create_time, remark)
VALUES
    (1026092801, '1001', '猪只白条出品率判定', 'djs_burn_yield_threshold', 1, NOW(), '工作台/小程序燎毛处理完成：累计接收重占出栏重的百分比阈值'),
    (1026092802, '1001', '白条半扇分割判定', 'djs_cut_yield_threshold', 1, NOW(), '白条分割完成判定的百分比阈值')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name), remark=VALUES(remark);

INSERT INTO sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_by, create_time, remark)
VALUES
    (1026092811, '1001', 1, '50%', '50', 'djs_burn_yield_threshold', '', 'primary', 'Y', 1, NOW(), '默认百分比，可在字典管理修改'),
    (1026092812, '1001', 1, '70%', '70', 'djs_cut_yield_threshold', '', 'primary', 'Y', 1, NOW(), '默认百分比，可在字典管理修改'),
    (1026092813, '1001', 40, '分割间出库', 'cut_room_out', 'djs_flow_type', '', 'warning', 'N', 1, NOW(), '猪只入库/白条分割工作台产品直接出库；保留原cut_out白条出库口径')
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), remark=VALUES(remark);
