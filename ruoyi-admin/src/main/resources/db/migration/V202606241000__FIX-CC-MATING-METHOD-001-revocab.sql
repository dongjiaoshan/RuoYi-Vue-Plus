-- ============================================================================
-- FIX-CC-MATING-METHOD-001 配种方式词表去非权威值（仅留 本场公猪 / 精液产品）
--
-- 权威源 = 东角山字典项整理.xlsx Sheet3：djs_mating_method 仅 本场公猪 / 精液产品 两项
-- （备注「精液需要先领用」）。人工授精(AI)/冷冻精液(LQ)/鲜精(RJ) 系 D5/D6 seed 漂移、
-- 不在权威源，清除。后端按「是否本场公猪（value=1）」分支：=1 选公猪耳号，其余走 djs_semen。
-- ============================================================================

-- 1) 删非权威 stray 值
DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_mating_method' AND tenant_id = '1001' AND dict_value IN ('AI', 'LQ', 'RJ');

-- 2) 存量迁移：误用 'AI'（人工授精，已删）的配种记录归并到 '2'（精液产品）
UPDATE t_farm_pig_breeding
   SET breeding_type = '2'
 WHERE tenant_id = '1001' AND breeding_type = 'AI';
