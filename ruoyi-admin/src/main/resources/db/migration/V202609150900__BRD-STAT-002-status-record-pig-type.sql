-- BRD-STAT-002：状态记录补「猪类型变更史」两列，供期末存栏按业务时间重放。
--
-- 期末存栏要按业务日重建（BRD-STAT-003），就必须能回答「某业务日这头猪是什么类型」。
-- 而 pig_type 的变更此前只落在 t_farm_pig_info.pig_type 这一个当前值上，没有历史。
-- 本迁移把变更前后类型落进 t_farm_status_record，并回填历史上三条会改类型的路径：
--   ① TO_FATTEN            后备母猪转育肥   sow      → fattening
--   ② 内部引种 INTRO        场内肥猪留种     fattening → sow/boar
--   ③ TRANSFER 转入育肥舍   仔猪转育肥       piglet   → fattening
-- 第四条路径「断奶即翻育肥」(WeaningServiceImpl.flipWeanedPigletsToFattening) 是批量 wrapper
-- update、不经状态机，历史上没有对应事件行；重放侧按「该窝断奶业务日之前一律算仔猪」判定
-- （AggregateQueryMapper.rebuildPigSnapshotForDate 的 pigletno → weaning 分支），不在此回填。

ALTER TABLE t_farm_status_record
  ADD COLUMN old_pig_type VARCHAR(16) NULL COMMENT '变更前猪只类型（仅本次事件改了 pig_type 时有值）' AFTER duration_days,
  ADD COLUMN new_pig_type VARCHAR(16) NULL COMMENT '变更后猪只类型（与 old_pig_type 成对）' AFTER old_pig_type;

-- ① TO_FATTEN 恒为 sow → fattening（TransferServiceImpl 固定 payload.newPigType='fattening'）
UPDATE t_farm_status_record
   SET old_pig_type = 'sow', new_pig_type = 'fattening'
 WHERE event_type = 'TO_FATTEN';

-- ② 内部引种：同一头猪的第 2 条及以后的 INTRO = internalIntroToReserve（第 1 条是 createPig 建档）
UPDATE t_farm_status_record r
  JOIN (
        SELECT id, pig_id,
               ROW_NUMBER() OVER (PARTITION BY pig_id ORDER BY change_time, id) AS rn
          FROM t_farm_status_record
         WHERE event_type = 'INTRO'
       ) x ON x.id = r.id AND x.rn > 1
  JOIN t_farm_pig_info p ON p.id = r.pig_id
   SET r.old_pig_type = 'fattening', r.new_pig_type = p.pig_type
 WHERE p.pig_type IN ('sow', 'boar');

-- ③ 仔猪转育肥舍：取该猪「最早一次转入 barn_type='fattening'」那条 TRANSFER。
--    mother_ear 非空 = 场内出生（曾经是仔猪）；外部引进的肥猪没有母猪耳号，转舍不改类型。
UPDATE t_farm_status_record r
  JOIN (
        SELECT MIN(r2.id) AS id
          FROM t_farm_status_record r2
          JOIN t_farm_pig_transfer t2 ON t2.id = r2.related_event_id AND t2.del_flag = '0'
          JOIN t_farm_barn_info b2 ON b2.id = t2.new_barn_id
          JOIN t_farm_pig_info p2 ON p2.id = r2.pig_id
          JOIN (
                SELECT r3.pig_id, MIN(r3.change_time) AS first_ct
                  FROM t_farm_status_record r3
                  JOIN t_farm_pig_transfer t3 ON t3.id = r3.related_event_id AND t3.del_flag = '0'
                  JOIN t_farm_barn_info b3 ON b3.id = t3.new_barn_id
                  JOIN t_farm_pig_info p3 ON p3.id = r3.pig_id
                 WHERE r3.event_type = 'TRANSFER' AND b3.barn_type = 'fattening'
                   AND p3.mother_ear IS NOT NULL
                 GROUP BY r3.pig_id
               ) fst ON fst.pig_id = r2.pig_id AND fst.first_ct = r2.change_time
         WHERE r2.event_type = 'TRANSFER' AND b2.barn_type = 'fattening'
           AND p2.mother_ear IS NOT NULL
         GROUP BY r2.pig_id
       ) y ON y.id = r.id
   SET r.old_pig_type = 'piglet', r.new_pig_type = 'fattening';
