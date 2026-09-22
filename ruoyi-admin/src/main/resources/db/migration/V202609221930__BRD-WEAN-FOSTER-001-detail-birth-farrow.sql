-- 断奶明细记录「这一头仔猪出生在哪一窝」（D-0113 寄养）。
--
-- 为什么要加这一列：开了寄养之后，一条断奶记录的明细里可以混着别窝的仔猪。
-- 「本窝自己消耗了几头」原先是从汇总列事后反推的（Σweaned_count 减去「看起来像寄养的」明细行），
-- 而「看起来像寄养的」怎么判都有漏：按「耳号非空」判，编几个不存在的耳号就能把本窝上限翻倍；
-- 按「回查 pigletno 且不是本窝」判，那头寄养仔猪日后死亡（pigletno 被软删）就会漏算，
-- 反过来凭空吃掉本窝一个名额，本窝最后一头再也断不掉。归属是提交那一刻就确定的事实，记下来即可，不该反推。
ALTER TABLE t_farm_pig_weaning_detail
    ADD COLUMN birth_farrow_id BIGINT NULL
        COMMENT '这头仔猪出生在哪一窝 t_farm_pig_farrow.id；寄养时与断奶记录的 farrow_id 不同；无耳号的匿名行记为断奶记录本窝'
        AFTER ear_no;

-- 历史行回填：按耳号回查仔猪打标行（连软删一起查 —— 耳号是事实，删不掉），
-- 查不到的（匿名行 / admin 汇总录入 / 耳号已不存在）按断奶记录自己那一窝算。
-- 上线前不存在寄养，所以历史行的归属本来就全是本窝，这里只是把它显式写下来。
UPDATE t_farm_pig_weaning_detail d
    JOIN t_farm_pig_weaning w ON w.id = d.weaning_id
    LEFT JOIN t_farm_pig_pigletno pn
           ON pn.piglet_ear_no = d.ear_no AND pn.tenant_id = d.tenant_id
   SET d.birth_farrow_id = COALESCE(pn.farrow_id, w.farrow_id);

CREATE INDEX idx_wean_detail_birth_farrow ON t_farm_pig_weaning_detail (tenant_id, birth_farrow_id);
