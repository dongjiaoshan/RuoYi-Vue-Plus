-- DICT-PIG-TYPE-001 统一 djs_pig_type 的 sow / boar 显示名为「种母猪 / 种公猪」
--
-- 背景：2026-06-01 15:48 有人用 admin 账号在「字典管理」界面把 staging 的这两条 label
-- 从「母猪 / 公猪」手工改成了「种母猪 / 种公猪」，但没写迁移 —— 于是：
--   · 生产库拿不到这个改动，至今仍是 Flyway seed 的原值（V202605201000 / V202605280800）；
--   · 初始数据重灌也冲不掉它（reseed cleanup 只清 t_% 业务表，明确不碰 sys_*，
--     设计上是为了保住客户在 admin 里的自定义字典，代价就是这类手改会变成环境间的隐形漂移）。
--
-- 取「种母猪 / 种公猪」为准：V202608141001__DICT-REFACTOR-002 写进 sys_dict_type.remark 的
-- 术语就是「猪的类型（种母猪/种公猪/仔猪/育肥猪）」，那次只改了 remark、漏改 dict_label，
-- 这里补上。同步改 plus-ui i18n 的 tab 文案，消除「Tab 写母猪、同行 tag 写种母猪」的自相矛盾。
--
-- 幂等：按 dict_type + dict_value 定位并直接赋目标值，与原值无关（staging 已是目标值 → 0 行受影响；
-- 生产是旧值 → 2 行受影响）。不按 dict_code 定位，避免客户重建字典行后 code 漂移导致改不到。

UPDATE sys_dict_data
   SET dict_label = '种母猪', update_time = NOW()
 WHERE dict_type = 'djs_pig_type'
   AND dict_value = 'sow'
   AND dict_label <> '种母猪';

UPDATE sys_dict_data
   SET dict_label = '种公猪', update_time = NOW()
 WHERE dict_type = 'djs_pig_type'
   AND dict_value = 'boar'
   AND dict_label <> '种公猪';
