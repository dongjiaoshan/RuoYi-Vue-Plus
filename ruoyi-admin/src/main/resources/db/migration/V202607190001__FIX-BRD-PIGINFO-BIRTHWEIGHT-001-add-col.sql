-- 猪只主表新增出生重列：仔猪耳标登记时按头录入（t_farm_pig_pigletno.birth_weight）回写。
-- 详情接口 /djs/breed/pig/{id} 返回，前端 FatDetail 显示「出生重 N kg」。
ALTER TABLE t_farm_pig_info
    ADD COLUMN birth_weight DECIMAL(6,2) NULL COMMENT '出生重kg（仔猪耳标登记时按头录入回写）' AFTER birth_date;

-- 回灌存量：已登记仔猪的出生重在 t_farm_pig_pigletno.birth_weight，按 pig_id 关联回写主表，
-- 让老数据详情也能显示出生重。每头仔猪在 pigletno 表对应一行（取该行 birth_weight）。
UPDATE t_farm_pig_info i
    JOIN t_farm_pig_pigletno p ON p.pig_id = i.id AND p.del_flag = '0'
SET i.birth_weight = p.birth_weight
WHERE p.birth_weight IS NOT NULL
  AND i.birth_weight IS NULL;
