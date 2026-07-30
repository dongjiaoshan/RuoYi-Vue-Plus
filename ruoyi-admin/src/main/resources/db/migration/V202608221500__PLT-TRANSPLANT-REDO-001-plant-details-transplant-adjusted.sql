-- PLT-TRANSPLANT-REDO-001：育苗移栽重做——种植明细加「是否移栽调整」标记。
-- 移栽累计 100%（或手动结束移栽）自动落地时，为每个目标地块新增一条「满种」明细，置 transplant_adjusted=1；
-- 源育苗明细标采摘完成 + 源地块转采摘态(3) 走退茬。该标记 + t_plant_farm_records 移栽记录（源→目标+比例）可追溯历史。
ALTER TABLE t_plant_plant_details
    ADD COLUMN transplant_adjusted TINYINT NOT NULL DEFAULT 0
    COMMENT '是否移栽调整 0=否/1=是（该明细由育苗移栽完成自动落地生成，plot_id=目标地块）' AFTER pick_settle_round;
