-- FIX-BRD-PENCOUNT-001：按在场猪只实况重算栏位当前头数 t_farm_barn_pen.current_count
--
-- 该列此前只在外部引种落栏时 +N，转群 / 死亡 / 淘汰 / 出栏 / 断奶转栏都不减，仔猪打耳标落母猪栏也不加；
-- 初始数据的猪只是直接 INSERT 进 t_farm_pig_info 的，一次都没 bump 过。结果这一列与实际在场头数
-- 双向偏离（住满的栏显示为空、腾空的栏显示占用）。代码侧已把全部增减收口到 PenCountUpdater，
-- 本次一次性把存量对齐到实况。
--
-- 口径 = 该栏未删除且非终止态的猪只行数（含仔猪），与 FarmStatMapper 栏位存栏 headCount 同口径；
-- 容量校验走 PenCapacityChecker 实时 COUNT（另一套口径：排除仔猪），不读本列。
-- pen_id 是全局唯一主键，无需按 tenant_id 再限定。
-- 幂等：整表按子查询无条件覆盖，重复执行结果一致。
UPDATE t_farm_barn_pen p
   SET p.current_count = (
       SELECT COUNT(*)
         FROM t_farm_pig_info i
        WHERE i.pen_id = p.id
          AND i.del_flag = '0'
          AND (i.current_status IS NULL OR i.current_status <> 'END')
   );
