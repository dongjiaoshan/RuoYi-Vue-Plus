-- FIX-BRD-PIGTYPE-001 存量数据回填：把旧逻辑遗留的 pig_type 不一致行补成符合「类型进展」规则的值。
-- 配合两处代码修复：内部留种（育肥猪→种猪）+ 断奶翻育肥（仔猪→育肥猪）。
-- 两条均为幂等条件 UPDATE（重复跑命中 0 行无害）；只回填 pig_type 一致性，不动 status 可空语义。
-- 单租户 V1（全 '1001'），Flyway 裸 SQL 无租户拦截，按 del_flag='0' 全量更新。

-- 回填 A：已「内部留种」但 pig_type 仍 fattening 的猪（旧 internalIntroToReserve 只推状态没改类型）
--   → 按性别重定：母→种母猪(sow)，公→种公猪(boar)；公猪误设的后备 HB 一并修正为公猪在产 BOAR_ACTIVE
--     （与外部引种公猪 / 修复后的留种逻辑一致，否则公种猪进不了配种选猪 status-filter=BOAR_ACTIVE）。
UPDATE t_farm_pig_info p
JOIN (
    SELECT DISTINCT pig_id
    FROM t_farm_pig_introduce
    WHERE introduce_type = 'internal' AND pig_id IS NOT NULL AND del_flag = '0'
) i ON i.pig_id = p.id
SET p.pig_type = IF(p.pig_sex = 'F', 'sow', 'boar'),
    p.current_status = IF(p.pig_sex = 'M' AND p.current_status = 'HB', 'BOAR_ACTIVE', p.current_status)
WHERE p.pig_type = 'fattening'
  AND p.del_flag = '0'
  AND p.current_status <> 'END';

-- 回填 B：已断奶但 pig_type 仍 piglet 的仔猪（旧逻辑只在转栏翻、不在断奶翻）→ 育肥猪。
--   判定「已断奶」= 该仔猪所属分娩（t_farm_pig_pigletno.farrow_id）存在断奶记录（t_farm_pig_weaning）。
UPDATE t_farm_pig_info p
JOIN t_farm_pig_pigletno pl ON pl.pig_id = p.id
JOIN t_farm_pig_weaning w ON w.farrow_id = pl.farrow_id AND w.del_flag = '0'
SET p.pig_type = 'fattening'
WHERE p.pig_type = 'piglet'
  AND p.del_flag = '0'
  AND p.current_status <> 'END';
