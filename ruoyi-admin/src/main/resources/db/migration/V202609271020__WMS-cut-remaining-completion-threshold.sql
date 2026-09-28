-- D0130：用户确认“剩余重量不超过原入库重量的50%才能完成”。
-- 271000 已应用，追加本迁移，不修改旧文件及校验和。
-- type 保持兼容；明确配置为“剩余占原入库上限”，不是产出率下限。
UPDATE sys_dict_type
   SET dict_name='白条半扇分割判定（剩余占入库上限）',
       remark='分割完成条件：剩余重量 <= 原入库重量 × 默认百分比 / 100；默认50%。确认交互不改变此计算口径。',
       update_by=1, update_time=NOW()
 WHERE tenant_id='1001' AND dict_type='djs_cut_yield_threshold';

UPDATE sys_dict_data
   SET dict_label='剩余不超过原入库的50%', dict_value='50', is_default='Y',
       remark='剩余重量占原入库重量的上限百分比；50代表50%，可在字典管理调整。',
       update_by=1, update_time=NOW()
 WHERE tenant_id='1001' AND dict_type='djs_cut_yield_threshold' AND dict_code=1026092812;
