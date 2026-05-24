-- ============================================================
-- BRD-CORE-001 修 t_farm_status_record.event_type 列注释词汇漂移（无破坏性）
--   SYS-INIT-001 V202605200901 建表时注释写的是 MATING/CONFIRM_PREG/.../DEAD/CULL/MARKET (10 个旧词汇)
--   SYS-INIT-002 V202605201000 灌字典 djs_pig_status_event 用的是 INTRO/BREED/FARROW/.../SLAUGHTER (11 个)
--   应用层（PigStatusEvent enum / PigStateMachine）以字典 11 个 value 为准
--   本 SQL 只改 COLUMN COMMENT 元数据，不动数据 / 不动类型 / 不动索引
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE t_farm_status_record MODIFY COLUMN event_type VARCHAR(16) NOT NULL
  COMMENT '触发事件（11 枚举，字典 djs_pig_status_event）：INTRO/BREED/FARROW/WEAN/OESTRUS/NULL_RETURN/DIE/ELIMINATE/CASTRATE/TRANSFER/SLAUGHTER';

-- 顺带修 t_farm_pig_info.current_status 注释（原写"9 枚举"，应为 10：含 BOAR_ACTIVE 公猪在产）
ALTER TABLE t_farm_pig_info MODIFY COLUMN current_status VARCHAR(16) NOT NULL DEFAULT 'HB'
  COMMENT '当前状态（10 枚举，字典 djs_pig_lifecycle）：HB/PZ/PH/FM/DN/LC/KH/FQ/END/BOAR_ACTIVE；BRD-CORE-001 状态机维护';
