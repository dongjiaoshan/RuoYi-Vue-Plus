-- 毛菜间出库：销售价格 / 出库单价快照 / 7 位出库单号
-- admin 行191：产品属性=原材料时可填「销售价格」（= 该原材料对外出库时的出库价格）。
-- admin 行192-196：列表加出库金额与出库单号；新增页录销售单价、算销售总价；打印三联出库单。

-- 1) 产品主数据加销售价格（全表原本一个价格字段都没有）
ALTER TABLE t_warehouse_product_info
    ADD COLUMN sale_price DECIMAL(10, 2) NULL
    COMMENT '销售价格（原材料对外出库单价，产品属性=原材料时可填）' AFTER material_num;

-- 2) 出库流水加单价快照
--    ⚠️ 必须落在流水行上、不能只读产品主数据：甲方要求新增出库时销售单价默认带出产品价格但**可以改**，
--    且改产品价格后历史出库单的金额不能跟着漂。
ALTER TABLE t_warehouse_stock_flow
    ADD COLUMN out_unit_price DECIMAL(10, 2) NULL
    COMMENT '出库销售单价快照（毛菜间出库录入，默认取产品 sale_price，可改）' AFTER change_quantity;

-- 3) 出库单号规则：7 位纯数字、终生递增（不按日重置）
--    原实现是 VO+时间戳+4位随机（随机是为了防并发撞号，因为 batch_no 无唯一约束）；
--    换成编码生成器后并发安全由 Redisson 锁 + 序号表唯一键保证，不再需要随机后缀。
INSERT IGNORE INTO t_md_biz_code_rule
    (code_type,     pattern,   daily_reset, prefix, seq_length, status, create_by, create_time)
VALUES
    ('VEG_OUT_NO',  '{seq7}',  0,           '',     7,          '0',    1,         NOW());

-- 4) 历史 batch_no 回填成新号（按出库时间、id 顺序重编）
--    现存单极少（staging 实测 4 单），直接重编比新旧两种格式混排干净；batch_no 只作聚合键，无外键引用。
--    ⚠️ 用 ROW_NUMBER() 而不是 `@seq := @seq + 1` 配 UPDATE JOIN：后者依赖 UPDATE 的行处理顺序，
--    MySQL 不保证它与派生表的 ORDER BY 一致，号会串。MySQL 8.0 起支持窗口函数，结果确定。
--    ⚠️ 必须限定 flow_type='backstage_out'：batch_no 是 stock_flow 的通用列，今天恰好只有毛菜间出库在用
--    （staging 实测 4 批 5 行、无其他 flow_type），但不加限定就是「谁将来用了 batch_no 谁被重编号」，
--    一张只跑一次的迁移不该埋这种雷。
CREATE TEMPORARY TABLE tmp_veg_out_no AS
SELECT batch_no,
       LPAD(ROW_NUMBER() OVER (ORDER BY MIN(flow_date), MIN(id)), 7, '0') AS new_no
FROM t_warehouse_stock_flow
WHERE batch_no IS NOT NULL AND del_flag = '0' AND flow_type = 'backstage_out'
GROUP BY batch_no;

UPDATE t_warehouse_stock_flow f
JOIN tmp_veg_out_no t ON t.batch_no = f.batch_no
SET f.batch_no = t.new_no
WHERE f.flow_type = 'backstage_out';

-- 让生成器的序号从已用最大号之后继续，避免新单撞上回填号。
-- 规则 daily_reset=0（终生递增）→ seq_date 存空串（与 BizCodeGeneratorImpl.resolveSeqDate 一致）。
INSERT INTO t_md_biz_code_sequence
    (id, tenant_id, code_type, seq_date, current_seq, create_by, create_time, update_by, update_time, del_flag, del_unique)
SELECT UNIX_TIMESTAMP() * 1000000 + 1, '1001', 'VEG_OUT_NO', '', COUNT(*), 1, NOW(), 1, NOW(), '0', 0
FROM tmp_veg_out_no;

DROP TEMPORARY TABLE tmp_veg_out_no;
