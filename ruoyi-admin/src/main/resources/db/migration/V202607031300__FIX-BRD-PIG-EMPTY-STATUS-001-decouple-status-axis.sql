-- FIX-BRD-PIG-EMPTY-STATUS-001 — 状态轴彻底解耦（ADR-0016）：非种母猪 current_status 置空 + 删 BOAR_ACTIVE
--
-- 模型：current_status 是「种母猪(sow)繁殖状态机」专属轴（HB/PZ/FM/DN/LC/KH/FQ/END）。
-- 非种母猪类型（boar/piglet/fattening）平时无繁殖态 → current_status=''（空串，非 NULL，保 NOT NULL 约束，
-- picker isNotNull / <> 'END' 对空串自动通过）；仅死亡/淘汰/出栏进 END。公猪不再有「在产」态
-- （BOAR_ACTIVE 删），靠 pig_type='boar' 标识。
--
-- 本迁移反向收敛两个旧模型 backfill 写脏的存量：V202607012000 / V202607031102 曾把空状态非种母猪
-- 回填成 boar→BOAR_ACTIVE / else→HB（旧语义，全类型套繁殖态）。createPig / batchEarTag /
-- internalIntroToReserve 已改为非种母猪写 ''，新数据不再产生 HB/BOAR_ACTIVE。
-- 幂等：UPDATE 命中范围稳定、DELETE 按 dict_value，重跑安全。纯数据/字典/注释 SQL，Flyway 自动应用。
-- ⚠️ T3 部署后跑 script/sql/djs/_post-init.sh flush redis 字典缓存（否则下拉仍显示「公猪在产」直到 TTL）。
-- V202607031300 > 当前 max V202607031200。

-- 1) 存量：非种母猪（boar/piglet/fattening）非终止 → 空状态 ''
UPDATE t_farm_pig_info
   SET current_status = ''
 WHERE pig_type IN ('boar', 'piglet', 'fattening')
   AND current_status <> 'END'
   AND del_flag = '0';

-- 2) 删字典『公猪在产』BOAR_ACTIVE 行（djs_pig_lifecycle 收敛为 8 枚举）
DELETE FROM sys_dict_data
 WHERE dict_type = 'djs_pig_lifecycle' AND dict_value = 'BOAR_ACTIVE';

-- 3) 字典类型 remark 订正（旧写 10 状态 → 8 状态）
UPDATE sys_dict_type
   SET remark = '养殖：种母猪繁殖状态机 8 状态（HB/PZ/FM/DN/LC/KH/FQ/END），BRD-CORE-001/ADR-0016；非种母猪空状态'
 WHERE dict_type = 'djs_pig_lifecycle';

-- 4) 订正 t_farm_pig_info.current_status 列注释（旧迁移写 10 枚举含 PH/BOAR_ACTIVE，已不符）
ALTER TABLE t_farm_pig_info MODIFY COLUMN current_status VARCHAR(16) NOT NULL DEFAULT 'HB'
  COMMENT '当前状态（8 枚举，字典 djs_pig_lifecycle）：HB/PZ/FM/DN/LC/KH/FQ/END；仅种母猪走繁殖态，非种母猪平时空(空串)、终止 END；BRD-CORE-001 状态机';
