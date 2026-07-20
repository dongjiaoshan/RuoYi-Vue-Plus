-- DENGBO-ROW84：门店猪肉现场打包「生产编码」持久化。
-- 生产编码 = <门店生产标识码>YYMMDD####（门店级每日流水），标签「生产编号」展示 + 补打列表可见。
-- 门店现场生码（TraceServiceImpl.genPorkOnsiteCode）纯生码不写 product_production，
-- 故补打列表无法从产出记录反查生产编号；改为生码时把已生成的生产编码落此列，补打列表直接读。
-- 存量行（无生产编码的旧门店码 / 仓库码）该列 NULL，展示层兜底不报错；不加 UNIQUE（生产编码非追溯主键）。
ALTER TABLE t_warehouse_trace_code
    ADD COLUMN production_code VARCHAR(64) NULL COMMENT '生产编码（门店现场打包 <生产标识码>YYMMDD####；仓库码 NULL）' AFTER produce_code;
